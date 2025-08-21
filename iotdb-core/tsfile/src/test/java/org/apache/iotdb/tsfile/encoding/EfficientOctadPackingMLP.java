package org.apache.iotdb.tsfile.encoding;
// EfficientOctadPackingMLP.java
// Java 17+
// Usage: java EfficientOctadPackingMLP [trainCsv] [dataDirectory] [outputDirectory]

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.*;

public class EfficientOctadPackingMLP {

    static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data", "test.csv");
    static final int CHUNK_SIZE = 1000;
    static final int INPUT_DIM = 5;
    static final int HIDDEN_DIM = 48;

    // ========== Pack / Result / DecisionPoint ==========
    static class Pack {
        int size = 0;
        int maxBitWidth = 0;
        int startIndex = 0;

        void addOctad(int index, int bitWidth) {
            if (size == 0) {
                startIndex = index;
                maxBitWidth = bitWidth;
            } else {
                if (bitWidth > maxBitWidth) maxBitWidth = bitWidth;
            }
            size++;
        }

        long dataCost() {
            return 8L * size * (long) maxBitWidth;
        }

        int logSize() {
            if (size <= 0) return 0;
            return 32 - Integer.numberOfLeadingZeros(size);
        }
    }

    static class PackingResult {
        int packCount = 0;
        long dataCostA = 0;
        int bitWidthCostB = 0;
        int packSizeCostC = 0;
        long totalCost = 0;

        void calculateCost(int maxLog) {
            bitWidthCostB = 5 * packCount;
            packSizeCostC = packCount * maxLog;
            totalCost = dataCostA + bitWidthCostB + packSizeCostC;
        }

        @Override
        public String toString() {
            return String.format("Packs: %d, Cost: %d (A=%d, B=%d, C=%d)", packCount, totalCost, dataCostA, bitWidthCostB, packSizeCostC);
        }
    }
    private static double safeLog(double p) {
        return Math.log(Math.max(p, 1e-8));
    }
    static class DecisionPoint {
        int currentPackSize;
        int currentPackMaxB;
        int newOctadB;
        int packCount;
        int currentMaxLog;
        boolean action;
        float probability;

        DecisionPoint(int cps, int cpm, int nob, int pc, int cml, boolean a, float p) {
            currentPackSize = cps;
            currentPackMaxB = cpm;
            newOctadB = nob;
            packCount = pc;
            currentMaxLog = cml;
            action = a;
            probability = p;
        }
    }

    // ========== 2-layer MLP policy with REINFORCE ==========
    static class RLDecisionModel {
        // W1: H x I, b1: H
        // W2: H (row), b2: scalar
        float[] W1; // size HIDDEN_DIM * INPUT_DIM
        float[] b1; // size HIDDEN_DIM
        float[] W2; // size HIDDEN_DIM
        float b2;

        float explorationRate = 0.3f;
        float learningRate = 0.01f;

        Random rng;

        RLDecisionModel() {
            rng = new Random();
            W1 = new float[HIDDEN_DIM * INPUT_DIM];
            b1 = new float[HIDDEN_DIM];
            W2 = new float[HIDDEN_DIM];
            for (int i = 0; i < W1.length; ++i) W1[i] = randUniform(-0.08f, 0.08f);
            for (int i = 0; i < b1.length; ++i) b1[i] = randUniform(-0.08f, 0.08f);
            for (int i = 0; i < W2.length; ++i) W2[i] = randUniform(-0.08f, 0.08f);
            b2 = randUniform(-0.08f, 0.08f);
        }

        private float randUniform(float a, float b) {
            return a + rng.nextFloat() * (b - a);
        }

        static float relu(float x) { return x > 0.0f ? x : 0.0f; }
        static float reluDeriv(float x) { return x > 0.0f ? 1.0f : 0.0f; }
        static float sigmoid(float x) {
            if (x >= 0) {
                double z = Math.exp(-x);
                return (float)(1.0 / (1.0 + z));
            } else {
                double z = Math.exp(x);
                return (float)(z / (1.0 + z));
            }
        }

        // forward pass: compute probability and optionally return hidden activations/z1
        float forwardProb(float[] feat, float[] outHidden, float[] outZ1) {
            if (outHidden != null) Arrays.fill(outHidden, 0.0f);
            if (outZ1 != null) Arrays.fill(outZ1, 0.0f);

            // z1 = W1 * x + b1
            for (int h = 0; h < HIDDEN_DIM; ++h) {
                float z = b1[h];
                int base = h * INPUT_DIM;
                for (int j = 0; j < INPUT_DIM; ++j) {
                    z += W1[base + j] * feat[j];
                }
                if (outZ1 != null) outZ1[h] = z;
                float hval = relu(z);
                if (outHidden != null) outHidden[h] = hval;
            }

            // output z2 = W2 · h + b2
            float z2 = b2;
            if (outHidden != null) {
                for (int h = 0; h < HIDDEN_DIM; ++h) z2 += W2[h] * outHidden[h];
            } else {
                // compute hidden on the fly
                for (int h = 0; h < HIDDEN_DIM; ++h) {
                    float z = b1[h];
                    int base = h * INPUT_DIM;
                    for (int j = 0; j < INPUT_DIM; ++j) z += W1[base + j] * feat[j];
                    float hval = relu(z);
                    z2 += W2[h] * hval;
                }
            }
            return sigmoid(z2);
        }

        float forwardProb(float[] feat) {
            return forwardProb(feat, null, null);
        }

        // REINFORCE training on collected decisions; returns total loss (sum -reward*log pi)
        float train(List<DecisionPoint> decisions, float reward) {
            if (decisions == null || decisions.isEmpty()) return 0.0f;

            explorationRate *= 0.99f;
            if (explorationRate < 0.05f) explorationRate = 0.05f;

            float[] dW1 = new float[W1.length];
            float[] db1 = new float[b1.length];
            float[] dW2 = new float[W2.length];
            float db2 = 0.0f;

            float totalLoss = 0.0f;

            float[] feat = new float[INPUT_DIM];
            float[] hidden = new float[HIDDEN_DIM];
            float[] z1 = new float[HIDDEN_DIM];

            for (DecisionPoint dp : decisions) {
                feat[0] = dp.currentPackSize / 100.0f;
                feat[1] = dp.currentPackMaxB / 64.0f;
                feat[2] = dp.newOctadB / 64.0f;
                feat[3] = dp.packCount / 100.0f;
                feat[4] = dp.currentMaxLog / 10.0f;

                float p = forwardProb(feat, hidden, z1);

                // clip p to [1e-6, 1 - 1e-6] to prevent log(0)
                float pClipped = Math.min(Math.max(p, 1e-6f), 1.0f - 1e-6f);

                float piA = dp.action ? pClipped : (1.0f - pClipped);
                if (piA <= 0.0f) {
                    // 防止log(0)或负数
                    piA = 1e-6f;
                }
                float lossI = -reward * (float) Math.log(piA);

                if (Float.isNaN(lossI) || Float.isInfinite(lossI)) {
                    // 打印调试信息
                    System.err.printf("Warning: loss is NaN or Infinite. p=%.8f, piA=%.8f, reward=%.8f\n", p, piA, reward);
                    lossI = 0.0f; // 避免传播NaN
                }

                totalLoss += lossI;

                // 梯度计算
                float dL_dz2 = reward * (p - (dp.action ? 1.0f : 0.0f));

                for (int h = 0; h < HIDDEN_DIM; ++h) {
                    dW2[h] += dL_dz2 * hidden[h];
                }
                db2 += dL_dz2;

                for (int h = 0; h < HIDDEN_DIM; ++h) {
                    float w2h = W2[h];
                    float dh = dL_dz2 * w2h;
                    float dReLU = reluDeriv(z1[h]);
                    float dZ1 = dh * dReLU;
                    int base = h * INPUT_DIM;
                    for (int j = 0; j < INPUT_DIM; ++j) {
                        dW1[base + j] += dZ1 * feat[j];
                    }
                    db1[h] += dZ1;
                }
            }

            float lr = learningRate;
            for (int i = 0; i < W1.length; ++i) {
                W1[i] -= lr * dW1[i];
                W1[i] = clip(W1[i], -10f, 10f);
            }
            for (int i = 0; i < b1.length; ++i) {
                b1[i] -= lr * db1[i];
                b1[i] = clip(b1[i], -10f, 10f);
            }
            for (int i = 0; i < W2.length; ++i) {
                W2[i] -= lr * dW2[i];
                W2[i] = clip(W2[i], -10f, 10f);
            }
            b2 -= lr * db2;
            b2 = clip(b2, -10f, 10f);

            return totalLoss;
        }

        static float clip(float v, float low, float high) {
            return Math.min(Math.max(v, low), high);
        }

    }

    // ========== CSV loader & scaling helpers ==========
    static List<List<Integer>> loadDataFromCSV(String filename) {
        List<List<Integer>> sequences = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"?\\[([0-9,\\s]+)\\]\"?");
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    String data = m.group(1);
                    List<Integer> arr = new ArrayList<>();
                    String[] tokens = data.split(",");
                    for (String t : tokens) {
                        String s = t.trim();
                        if (s.isEmpty()) continue;
                        try {
                            arr.add(Integer.parseInt(s));
                        } catch (Exception ex) { /* ignore */ }
                    }
                    if (!arr.isEmpty()) sequences.add(arr);
                }
            }
        } catch (IOException e) {
            System.err.println("Error opening CSV: " + filename);
        }
        return sequences;
    }

    static String trimStr(String s) {
        if (s == null) return "";
        int a = 0;
        while (a < s.length() && Character.isWhitespace(s.charAt(a))) a++;
        if (a == s.length()) return "";
        int b = s.length() - 1;
        while (b >= 0 && Character.isWhitespace(s.charAt(b))) b--;
        return s.substring(a, b + 1);
    }

    static String stripEnclosingQuotes(String s) {
        if (s == null) return "";
        if (s.length() >= 2) {
            char f = s.charAt(0);
            char l = s.charAt(s.length() - 1);
            if ((f == '"' && l == '"') || (f == '\'' && l == '\'')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    // scaleNumbers: use BigDecimal to parse, scale by 10^decimalMax, shift so min becomes 0, return long[] with clipping
    static long[] scaleNumbers(List<String> numbers, int decimalMax) {
        int n = numbers.size();
        long[] result = new long[n];
        if (n == 0) return result;

        BigDecimal scale = BigDecimal.ONE;
        for (int i = 0; i < decimalMax; ++i) scale = scale.multiply(BigDecimal.TEN);

        BigDecimal[] vals = new BigDecimal[n];
        for (int i = 0; i < n; ++i) {
            String s = trimStr(numbers.get(i));
            s = stripEnclosingQuotes(s);
            if (s.isEmpty()) { vals[i] = BigDecimal.ZERO; continue; }
            s = s.replace(",", ""); // remove thousands sep

            // If scientific notation present, BigDecimal can parse it
            try {
                BigDecimal bd = new BigDecimal(s);
                BigDecimal scaled = bd.multiply(scale);
                // rounding to nearest whole
                BigDecimal rounded = scaled.setScale(0, RoundingMode.HALF_UP);
                vals[i] = rounded;
            } catch (Exception ex) {
                // fallback: parse double
                try {
                    double dv = Double.parseDouble(s);
                    BigDecimal bd = BigDecimal.valueOf(dv).multiply(scale);
                    vals[i] = bd.setScale(0, RoundingMode.HALF_UP);
                } catch (Exception ex2) {
                    System.err.println("Warning: cannot parse token '" + numbers.get(i) + "', set to 0");
                    vals[i] = BigDecimal.ZERO;
                }
            }
        }

        // find min
        BigDecimal minv = vals[0];
        for (int i = 1; i < n; ++i) if (vals[i].compareTo(minv) < 0) minv = vals[i];

        for (int i = 0; i < n; ++i) {
            BigDecimal shifted = vals[i].subtract(minv);
            // clamp to long range
            try {
                BigInteger bi = shifted.toBigIntegerExact();
                if (bi.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) result[i] = Long.MAX_VALUE;
                else if (bi.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) result[i] = Long.MIN_VALUE;
                else result[i] = bi.longValue();
            } catch (ArithmeticException ae) {
                // not an integer exactly: fallback by converting to long with rounding
                BigDecimal rounded = shifted.setScale(0, RoundingMode.HALF_UP);
                try {
                    BigInteger bi = rounded.toBigIntegerExact();
                    if (bi.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) result[i] = Long.MAX_VALUE;
                    else if (bi.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) result[i] = Long.MIN_VALUE;
                    else result[i] = bi.longValue();
                } catch (Exception ex) {
                    result[i] = 0;
                }
            }
        }
        return result;
    }

    // ========== packOctads (will call model.forwardProb for probability) ==========
    static PackingResult packOctads(List<Integer> bitWidths, RLDecisionModel model, List<DecisionPoint> decisionTrace) {
        PackingResult result = new PackingResult();
        Pack currentPack = new Pack();
        int globalMaxLog = 0;
        int packCount = 0;

        Random localRng = ThreadLocalRandom.current();

        for (int i = 0; i < bitWidths.size(); ++i) {
            int b = bitWidths.get(i);

            if (currentPack.size == 0) {
                currentPack.addOctad(i, b);
            } else if (b == currentPack.maxBitWidth) {
                currentPack.addOctad(i, b);
            } else {
                float[] feat = new float[INPUT_DIM];
                feat[0] = currentPack.size / 100.0f;
                feat[1] = currentPack.maxBitWidth / 32.0f;
                feat[2] = b / 32.0f;
                feat[3] = packCount / 100.0f;
                feat[4] = globalMaxLog / 10.0f;

                float probability = model.forwardProb(feat);

                boolean shouldMerge;
                if (localRng.nextFloat() < model.explorationRate) {
                    shouldMerge = (localRng.nextFloat() > 0.5f);
                } else {
                    shouldMerge = probability > 0.5f;
                }

                if (decisionTrace != null) {
                    decisionTrace.add(new DecisionPoint(currentPack.size, currentPack.maxBitWidth, b, packCount, globalMaxLog, shouldMerge, probability));
                }

                if (shouldMerge) {
                    currentPack.addOctad(i, b);
                } else {
                    result.dataCostA += currentPack.dataCost();
                    int logSize = currentPack.logSize();
                    if (logSize > globalMaxLog) globalMaxLog = logSize;
                    packCount++;

                    currentPack = new Pack();
                    currentPack.addOctad(i, b);
                }
            }
        }

        if (currentPack.size > 0) {
            result.dataCostA += currentPack.dataCost();
            int logSize = currentPack.logSize();
            if (logSize > globalMaxLog) globalMaxLog = logSize;
            packCount++;
        }

        result.packCount = packCount;
        result.calculateCost(globalMaxLog);
        return result;
    }

    // ========== Training loop (trainModel) ==========
    static RLDecisionModel trainModel(int epochs, String csvFilePath) {
        System.err.println("Training RL model from CSV data...");
        RLDecisionModel model = new RLDecisionModel();
        List<List<Integer>> sequences = loadDataFromCSV(csvFilePath);
        if (sequences.isEmpty()) {
            System.err.println("No data loaded from CSV. Returning initial model.");
            return model;
        }
        System.err.println("Loaded " + sequences.size() + " sequences from CSV");

        List<DecisionPoint> decisionTrace = new ArrayList<>();
        for (int epoch = 1; epoch <= epochs; ++epoch) {
            long startTime = System.nanoTime();
            float totalReward = 0.0f;
            float totalLoss = 0.0f;
            int processedSequences = 0;

            for (List<Integer> bitWidths : sequences) {
                decisionTrace.clear();
                PackingResult result = packOctads(bitWidths, model, decisionTrace);

                float reward = - (float) result.totalCost / 10000.0f;
                totalReward += reward;

                float loss = model.train(decisionTrace, reward);
                totalLoss += loss;
                processedSequences++;
            }

            long durationMs = (System.nanoTime() - startTime) / 1_000_000L;

            if (epoch % 10 == 0 || epoch == 1 || epoch == epochs) {
                System.out.printf("Epoch %d: Avg Reward = %.6f, Avg Loss = %.6f, Time = %d ms%n",
                        epoch,
                        totalReward / processedSequences,
                        totalLoss / processedSequences,
                        durationMs);
            } else {
                System.out.printf("Epoch %d done. Time = %d ms%n", epoch, durationMs);
            }
        }

        return model;
    }

    // ========== performanceTest (same logic as before) ==========
    static void performanceTest(RLDecisionModel model, String directory, String outputDirStr) {
        System.out.println("\nPerformance Testing...");
        Path outdir = Paths.get(outputDirStr);
        try {
            if (!Files.exists(outdir)) Files.createDirectories(outdir);
        } catch (IOException e) {
            System.err.println("Cannot create output dir: " + outputDirStr);
            return;
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Paths.get(directory))) {
            for (Path entry : ds) {
                if (!Files.isRegularFile(entry)) continue;
                String fname = entry.getFileName().toString();
                if (IGNORE_FILES.contains(fname)) continue;

                System.out.println("Processing " + fname + "...");
                List<String> numbers = new ArrayList<>();
                List<Integer> decimalPlaces = new ArrayList<>();

                try (BufferedReader br = Files.newBufferedReader(entry)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] tokens = line.split(",");
                        for (String token : tokens) {
                            String t = trimStr(token);
                            if (!t.isEmpty()) {
                                numbers.add(t);
                                int dec = 0;
                                int pos = t.indexOf('.');
                                if (pos != -1) dec = t.length() - pos - 1;
                                decimalPlaces.add(dec);
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Cannot open " + entry.toString());
                    continue;
                }

                if (numbers.isEmpty()) continue;

                Path outPath = outdir.resolve(fname);
                try (BufferedWriter writer = Files.newBufferedWriter(outPath)) {
                    writer.write("Input Direction,Encoding Algorithm,Encoding Time,Points,Compressed Size,Compression Ratio\n");

                    int time_of_repeat = 50;
                    long modelCost = 0;
                    long modelTime = 0;

                    for (int rep = 0; rep < time_of_repeat; ++rep) {
                        for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                            int end = Math.min(numbers.size(), i + CHUNK_SIZE);
                            if (end - i <= 2) continue;
                            List<String> chunkNumbers = numbers.subList(i, end);
                            int decimalMax = 0;
                            for (int k = i; k < end; ++k) {
                                if (decimalPlaces.get(k) > decimalMax) decimalMax = decimalPlaces.get(k);
                            }

                            long[] scaledInts = scaleNumbers(chunkNumbers, decimalMax);
                            long startTime = System.nanoTime();

                            int remainder = scaledInts.length % 8;
                            int padding = (remainder == 0) ? 0 : 8 - remainder;
                            long[] padded = new long[scaledInts.length + padding];
                            System.arraycopy(scaledInts, 0, padded, 0, scaledInts.length);
                            for (int p = scaledInts.length; p < padded.length; ++p) padded[p] = 0L;

                            int groups = padded.length / 8;
                            List<Integer> bitWidths = new ArrayList<>(groups);
                            for (int si = 0; si < padded.length; si += 8) {
                                long maxInGroup = 0;
                                for (int sj = si; sj < si + 8; ++sj) {
                                    if (padded[sj] > maxInGroup) maxInGroup = padded[sj];
                                }
                                int bitWidth = 0;
                                int ui = (maxInGroup > 0) ? (int) (maxInGroup & 0xffffffffL) : 0;
                                if (ui != 0) bitWidth = 32 - Integer.numberOfLeadingZeros(ui);
                                bitWidths.add(bitWidth);
                            }

                            PackingResult res = packOctads(bitWidths, model, null);
                            long duration = System.nanoTime() - startTime;
                            modelTime += duration;
                            modelCost += res.totalCost;
                        }
                    }

                    modelCost /= time_of_repeat;
                    modelTime /= time_of_repeat;
                    double model_ratio = (double) modelCost / (double) (numbers.size() * 64);
                    double modelTime_throughput = (double) (numbers.size() * 8000) / (double) modelTime;

                    writer.write(entry.toString() + ",");
                    writer.write("BP-Reinforce-MLP,");
                    writer.write(String.valueOf(modelTime_throughput) + ",");
                    writer.write(String.valueOf(numbers.size()) + ",");
                    writer.write(String.valueOf(modelCost) + ",");
                    writer.write(String.valueOf(model_ratio) + "\n");
                } catch (IOException e) {
                    System.err.println("Error writing output file for " + fname);
                }
            }
        } catch (IOException e) {
            System.err.println("Error iterating directory: " + directory);
        }
    }

    public static void main(String[] args) {
        String trainCsv = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/processed_data.csv";// args.length > 0 ? args[0] : "";
        String dataDir = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";//args.length > 1 ? args[1] : "";
        String outDir = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_BPRL";// args.length > 2 ? args[2] : "./output_BPRL";

        int epochs = 40;

        if (args.length >= 1) trainCsv = args[0];
        if (args.length >= 2) dataDir = args[1];
        if (args.length >= 3) outDir = args[2];

        RLDecisionModel model = new RLDecisionModel();
        if (!trainCsv.isEmpty()) {
            model = trainModel(epochs, trainCsv);
        } else {
            System.err.println("No training CSV given. Using randomly initialized RL model.");
        }

        if (!dataDir.isEmpty()) {
            performanceTest(model, dataDir, outDir);
        } else {
            System.err.println("No data directory provided for performanceTest. Exiting.");
        }
    }
}
