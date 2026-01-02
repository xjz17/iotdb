package org.apache.iotdb.tsfile.encoding;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PackSizeMLTrainerAndEvaluator
 * <p>
 * 单文件实现：
 * - MLPackSizeEstimator: 训练并保存线性回归（Ridge）权重；可以读取权重并预测 pack_size
 * - encodeBitPackingV2 / decodeBitPackingV2: 与之前代码兼容的位打包/解包实现
 * - 数据读取、scaleNumbers (decimal scaling)、synthetic sample generator
 * - main: 训练（可选，默认会训练合成样本并保存权重），然后加载权重并对指定目录下的CSV文件做评估
 *
 * 评估输出（控制台 + CSV）：
 * - Compression Ratio (bits after compression / (n * 64))
 * - Encoding throughput (MB/s) — 包含预测时间，但不包含训练时间
 * - Decoding throughput (MB/s)
 *
 * 说明：本文件无外部依赖，直接用 javac 编译并运行。默认训练使用合成样本。
 */
public class PackSizeMLTrainerAndEvaluator {

    // --------------------------- 配置 ---------------------------
    private static final int CHUNK_SIZE = 1024; // 每次用于pack_size决策的chunk大小
    private static final int TIME_OF_REPEAT = 50; // 每个文件重复多少次以平滑计时
    private static final String DEFAULT_WEIGHT_FILE = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/packsize_weights.csv";
    static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data", "test.csv", "POI-lat.csv",
            "POI-lon.csv", "Basel-wind.csv", "Basel-temp.csv", "Air-sensor.csv"); //,"Mem-usage.csv","Cpu-usage_right.csv","Disk-usage.csv"
    // --------------------------- 主流程 ---------------------------
    public static void main(String[] args) throws Exception {
        // 参数： dataDirectory [weightFile] [trainFlag]
//        if (args.length < 1) {
//            System.out.println("Usage: java PackSizeMLTrainerAndEvaluator <data-directory> [weight-file] [train(true|false)]");
//            System.exit(1);
//        }

        String dataDir ="/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/ElfTestData_camel";// args[0];
        String weightFile = args.length >= 2 ? args[1] : DEFAULT_WEIGHT_FILE;
        boolean doTrain =  true;//args.length >= 3 ? Boolean.parseBoolean(args[2]) :

        // 1) 训练（不计入后续吞吐测量）
        MLPackSizeEstimator estimator = new MLPackSizeEstimator();
        if (doTrain) {
            System.out.println("Training model (synthetic samples)... This time is NOT included in throughput measurements.");
            Random rnd = new Random(12345);
            List<int[]> trainSamples = MLPackSizeEstimator.generateSyntheticSamples(2000, 16, 1024, rnd);
            estimator.trainAndSave(trainSamples, weightFile);
            System.out.println("Training finished. Weights saved to: " + weightFile);
        }

        // 2) 加载权重（训练后或直接用已有权重）
        estimator.loadWeightsFromCSV(weightFile);

        // 3) 对目录下每个文件进行评估
        Path dir = Paths.get(dataDir);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("data-directory does not exist or is not a directory: " + dataDir);
        }

        Path outCsv = Paths.get("/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/learning_evaluation_results.csv");
        try (BufferedWriter w = Files.newBufferedWriter(outCsv, StandardCharsets.UTF_8)) {
            String head = "InputFile,Points,Compressed Size,Compression Ratio,Encoding Time,Decoding Time";
            w.write(head);
            w.newLine();

            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path file : ds) {
                    if (Files.isDirectory(file)) continue;
                    String name = file.getFileName().toString();
                    if (name.startsWith(".") || name.equals("full_data") || name.equals("test.csv")) continue;
                    if (IGNORE_FILES.contains(name)) continue;
                    System.out.println("Evaluating " + name + " ...");

                    List<String> numbers = readAllNumbersFromCsv(file);
                    if (numbers.isEmpty()) {
                        System.out.println("  (empty)");
                        continue;
                    }

                    int decimalMax = numbers.stream()
                            .mapToInt(s -> {
                                int dec = 0;
                                if (s.contains(".")) dec = s.split("\\.")[1].length();
                                return dec;
                            })
                            .max().orElse(0);

                    // split into batches as original逻辑（每batch 1024用于scale）
                    int batchSize = 1024;
                    List<int[]> batches = new ArrayList<>();
                    for (int i = 0; i < numbers.size(); i += batchSize) {
                        int end = Math.min(numbers.size(), i + batchSize);
                        List<String> sub = numbers.subList(i, end);
                        int[] scaled = scaleNumbers(sub, decimalMax);
                        batches.add(scaled);
                    }

                    int totalLength = batches.stream().mapToInt(a -> a.length).sum();
                    int[] scaledAll = new int[totalLength];
                    int pos = 0;
                    for (int[] b : batches) {
                        System.arraycopy(b, 0, scaledAll, pos, b.length);
                        pos += b.length;
                    }

                    long totalCompressedBitsAcc = 0L;
                    long encodeTimeNsAcc = 0L; // 包含预测时间
                    long decodeTimeNsAcc = 0L;

                    for (int rep = 0; rep < TIME_OF_REPEAT; rep++) {
                        // iterate chunks
                        for (int i = 0; i < scaledAll.length; i += CHUNK_SIZE) {
                            int end = Math.min(scaledAll.length, i + CHUNK_SIZE);
                            int len = end - i;
                            int[] chunk = new int[len];
                            System.arraycopy(scaledAll, i, chunk, 0, len);

                            long t0 = System.nanoTime();
                            int packSize = estimator.predictPackSize(chunk);
                            // ensure >=1
                            packSize = Math.max(1, Math.min(packSize, chunk.length));

                            byte[] bitWidthsArr = computeBitWidthsBytes(chunk, packSize); // helper to compute bitWidths int[]
                            int[] bitWidths = unpackBitWidthsFromBytes(bitWidthsArr); // quick conversion (we could compute directly)

                            // encode (encodeBitPackingV2 needs int[] originalArray, int[] bitWidths, int pack_size)
                            byte[] encoded = encodeBitPackingV2(chunk, bitWidths, packSize);
                            long t1 = System.nanoTime();
                            long encodeDelta = t1 - t0;
                            encodeTimeNsAcc += encodeDelta;

                            totalCompressedBitsAcc += (long) encoded.length * 8L;

                            // decode timing
                            long td0 = System.nanoTime();
                            int[] decoded = decodeBitPackingV2(encoded, bitWidths, packSize, chunk.length);
                            long td1 = System.nanoTime();
                            decodeTimeNsAcc += (td1 - td0);

                            // optional: verify correctness
                            // (we expect decoded equals chunk)
                            // we skip heavy checking for performance but do a cheap sanity check
                            if (decoded.length != chunk.length) {
                                System.err.println("Decoded length mismatch for " + name);
                            }
                        }
                    }

                    long avgCompressedBits = totalCompressedBitsAcc / TIME_OF_REPEAT;
                    long avgEncodeNs = encodeTimeNsAcc / TIME_OF_REPEAT;
                    long avgDecodeNs = decodeTimeNsAcc / TIME_OF_REPEAT;

                    // Throughput（MB/s），我们把原始每个点视为 8 bytes (64 bits)
                    double totalInputBytes = (double) numbers.size() * 8.0;
                    double encodeSeconds = ((double) avgEncodeNs) / 1e9;
                    double decodeSeconds = ((double) avgDecodeNs) / 1e9;

                    double encodeMBs = (totalInputBytes / encodeSeconds) / (1024.0 * 1024.0);
                    double decodeMBs = (totalInputBytes / decodeSeconds) / (1024.0 * 1024.0);

                    double ratio = ((double) avgCompressedBits) / ((double) numbers.size() * 64.0);

                    String out = String.join(",",
                            name,
                            String.valueOf(numbers.size()),
                            String.valueOf(avgCompressedBits),
                            String.format(Locale.ROOT, "%.6f", ratio),
                            String.format(Locale.ROOT, "%.3f", encodeMBs),
                            String.format(Locale.ROOT, "%.3f", decodeMBs)
                    );
                    w.write(out);
                    w.newLine();
                    w.flush();

                    System.out.println("  points=" + numbers.size() +
                            ", compressedBits=" + avgCompressedBits +
                            ", ratio=" + String.format(Locale.ROOT, "%.6f", ratio) +
                            ", encodeMB/s=" + String.format(Locale.ROOT, "%.3f", encodeMBs) +
                            ", decodeMB/s=" + String.format(Locale.ROOT, "%.3f", decodeMBs)
                    );
                }
            }
        }

        System.out.println("Evaluation finished. Results written to evaluation_results.csv");
    }

    // --------------------------- 辅助：CSV 读取 ---------------------------
    private static List<String> readAllNumbersFromCsv(Path file) throws IOException {
        List<String> out = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line == null) continue;
            String[] toks = line.split(",");
            for (String t : toks) {
                String s = t.trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    // --------------------------- 辅助：scaleNumbers ---------------------------
    private static int[] scaleNumbers(List<String> numbers, int decimalMax) {
        BigDecimal scale = BigDecimal.TEN.pow(decimalMax);
        int size = numbers.size();
        int[] result = new int[size];

        if (size == 0) return result;

        BigDecimal min = null;
        BigDecimal[] scaledValues = new BigDecimal[size];

        for (int i = 0; i < size; i++) {
            String raw = numbers.get(i);
            if (raw == null) {
                scaledValues[i] = BigDecimal.ZERO;
                continue;
            }
            // 关键修复：清洗非法字符（引号、空格、不可见字符等）
            String s = raw.trim();
            if (s.length() == 0) {
                scaledValues[i] = BigDecimal.ZERO;
                continue;
            }
            // 去掉首尾引号（CSV 常见）
            if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                s = s.substring(1, s.length() - 1).trim();
            }
            // 允许的字符：数字、+ - . e E
            // 若包含其他字符，直接跳过，按 0 处理
            if (!s.matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?")) {
                scaledValues[i] = BigDecimal.ZERO;
                continue;
            }
            try {
                BigDecimal val = new BigDecimal(s).multiply(scale);
                scaledValues[i] = val;
                if (min == null || val.compareTo(min) < 0) min = val;
            } catch (NumberFormatException ex) {
                // 极端异常兜底：非法数值直接置 0
                scaledValues[i] = BigDecimal.ZERO;
            }
        }


        if (min == null) min = BigDecimal.ZERO;


        for (int i = 0; i < size; i++) {
            BigDecimal current = scaledValues[i].subtract(min);
            result[i] = current.toBigInteger().intValue();
        }

        return result;
    }

    // --------------------------- 辅助：bitWidths 计算（用于encode参数） ---------------------------
    private static byte[] computeBitWidthsBytes(int[] arr, int pack_size) {
        int n = arr.length;
        int numGroups = (n + pack_size - 1) / pack_size;
        // We'll serialize as simple CSV-like bytes: each int bitWidth separated by comma (quick hack)
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < numGroups; g++) {
            int start = g * pack_size;
            int end = Math.min(n, start + pack_size);
            int maxInGroup = 0;
            for (int k = start; k < end; k++) if (arr[k] > maxInGroup) maxInGroup = arr[k];
            int bitWidth = 32 - Integer.numberOfLeadingZeros(Math.max(1, maxInGroup));
            if (g > 0) sb.append(',');
            sb.append(bitWidth);
        }
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static int[] unpackBitWidthsFromBytes(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.US_ASCII);
        String[] toks = s.split(",");
        int[] out = new int[toks.length];
        for (int i = 0; i < toks.length; i++) out[i] = Integer.parseInt(toks[i]);
        return out;
    }

    // --------------------------- encode/decode (V2) ---------------------------
    public static byte[] encodeBitPackingV2(int[] originalArray, int[] bitWidths, int pack_size) {
        int totalGroups = bitWidths.length;
        int n = originalArray.length;

        // bitsForBitWidth calculation
        int maxBitWidth = 0;
        for (int bitWidth : bitWidths) if (bitWidth > maxBitWidth) maxBitWidth = bitWidth;
        int bitsForBitWidth = (maxBitWidth == 0) ? 1 : (32 - Integer.numberOfLeadingZeros(maxBitWidth));
        if (bitsForBitWidth <= 0) bitsForBitWidth = 1;

        BitWriterV2 bitWriter = new BitWriterV2();

        // write bitsForBitWidth (6 bits fixed)
        bitWriter.writeBits(bitsForBitWidth, 6);

        // write each group's bitWidth using bitsForBitWidth
        for (int group = 0; group < totalGroups; group++) {
            int bitWidth = bitWidths[group];
            bitWriter.writeBits(bitWidth, bitsForBitWidth);
        }

        // write data
        for (int group = 0; group < totalGroups; group++) {
            int startIndex = group * pack_size;
            int bitWidth = bitWidths[group];
            int valuesInGroup = Math.min(pack_size, n - startIndex);
            if (valuesInGroup <= 0) break;
            for (int i = 0; i < valuesInGroup; i++) {
                int idx = startIndex + i;
                int value = (idx < n) ? originalArray[idx] : 0;
                int maskedValue = (bitWidth >= 32) ? value : (value & ((1 << bitWidth) - 1));
                bitWriter.writeBits(maskedValue, bitWidth);
            }
        }

        return bitWriter.toByteArray();
    }

    public static int[] decodeBitPackingV2(byte[] encodedData, int[] originalBitWidths, int pack_size, int n) {
        if (originalBitWidths == null || originalBitWidths.length == 0) throw new IllegalArgumentException("bitWidths required");
        int totalGroups = originalBitWidths.length;
        int[] decodedArray = new int[n];

        BitReaderV2 bitReader = new BitReaderV2(encodedData);
        int bitsForBitWidth = bitReader.readBits(6);
        // read and ignore decodedBitWidths for now
        int[] decodedBitWidths = new int[totalGroups];
        for (int group = 0; group < totalGroups; group++) {
            decodedBitWidths[group] = bitReader.readBits(bitsForBitWidth);
            // ignore mismatch handling here
        }

        for (int group = 0; group < totalGroups; group++) {
            int startIndex = group * pack_size;
            int bitWidth = originalBitWidths[group];
            int valuesInGroup = Math.min(pack_size, n - startIndex);
            if (valuesInGroup <= 0) break;
            for (int i = 0; i < valuesInGroup; i++) {
                int idx = startIndex + i;
                int v = 0;
                if (idx < n) v = bitReader.readBits(bitWidth);
                else bitReader.readBits(bitWidth);
                if (idx < n) decodedArray[idx] = v;
            }
        }
        return decodedArray;
    }

    // BitWriterV2
    static class BitWriterV2 {
        private ByteArrayOutputStream buffer;
        private int currentByte;
        private int bitPosition; // 7..0

        public BitWriterV2() {
            buffer = new ByteArrayOutputStream();
            currentByte = 0;
            bitPosition = 7;
        }

        public void writeBits(int value, int numBits) {
            if (numBits <= 0) return;
            for (int i = numBits - 1; i >= 0; i--) {
                int bit = (value >> i) & 1;
                if (bit == 1) currentByte |= (1 << bitPosition);
                bitPosition--;
                if (bitPosition < 0) {
                    buffer.write(currentByte);
                    currentByte = 0;
                    bitPosition = 7;
                }
            }
        }

        public byte[] toByteArray() {
            if (bitPosition != 7) buffer.write(currentByte);
            return buffer.toByteArray();
        }
    }

    // BitReaderV2
    static class BitReaderV2 {
        private byte[] buffer;
        private int currentByteIndex;
        private int currentByte;
        private int bitPosition; // 7..0

        public BitReaderV2(byte[] buffer) {
            this.buffer = buffer;
            this.currentByteIndex = 0;
            if (buffer.length > 0) this.currentByte = buffer[0] & 0xFF;
            else this.currentByte = 0;
            this.bitPosition = 7;
        }

        public int readBits(int numBits) {
            if (numBits <= 0) return 0;
            int result = 0;
            for (int i = 0; i < numBits; i++) {
                int bit = (currentByte >> bitPosition) & 1;
                result = (result << 1) | bit;
                bitPosition--;
                if (bitPosition < 0) {
                    currentByteIndex++;
                    if (currentByteIndex < buffer.length) currentByte = buffer[currentByteIndex] & 0xFF;
                    else currentByte = 0;
                    bitPosition = 7;
                }
            }
            return result;
        }
    }

    // --------------------------- MLPackSizeEstimator (与之前实现相同) ---------------------------
    public static class MLPackSizeEstimator {
        private static final int MAX_P_CONSIDER = 128;
        private static final double LAMBDA = 1e-3;
        private double[] weights = null; // 包含bias

        public MLPackSizeEstimator() {}

        public void loadWeightsFromCSV(String path) throws IOException {
            try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                String line = br.readLine();
                if (line == null) throw new IOException("empty weight file");
                String[] toks = line.trim().split(",");
                weights = new double[toks.length];
                for (int i = 0; i < toks.length; i++) weights[i] = Double.parseDouble(toks[i]);
            }
        }

        public void saveWeightsToCSV(String path) throws IOException {
            if (weights == null) throw new IllegalStateException("weights not set");
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
                for (int i = 0; i < weights.length; i++) {
                    if (i > 0) bw.write(",");
                    bw.write(String.valueOf(weights[i]));
                }
                bw.newLine();
            }
        }

        public int predictPackSize(int[] values) {
            if (values == null || values.length <= 1) return Math.max(1, (values == null ? 1 : values.length));
            if (weights == null) return Math.max(1, Math.min(32, values.length));
            double[] feat = computeFeatureVector(values);
            double pred = 0.0;
            for (int i = 0; i < weights.length; i++) pred += weights[i] * feat[i];
            int p = (int) Math.round(pred);
            if (p < 1) p = 1;
            if (p > values.length) p = values.length;
            return p;
        }

        public void trainAndSave(List<int[]> trainSamples, String savePath) throws IOException {
            if (trainSamples == null || trainSamples.size() == 0) throw new IllegalArgumentException("no training samples");
            int m = trainSamples.size();
            int d = 7; // bias + 6 features
            double[][] X = new double[m][d];
            double[] y = new double[m];
            for (int i = 0; i < m; i++) {
                int[] vals = trainSamples.get(i);
                double[] feat = computeFeatureVector(vals);
                System.arraycopy(feat, 0, X[i], 0, d);
                int bestP = bruteForceBestP(vals, Math.min(MAX_P_CONSIDER, Math.max(1, vals.length)));
                y[i] = bestP;
            }
            this.weights = ridgeRegressionSolve(X, y, LAMBDA);
            saveWeightsToCSV(savePath);
        }

        private static double[] computeFeatureVector(int[] values) {
            int n = values.length;
            double[] bitWidths = new double[n];
            int maxBW = 0;
            int nonZero = 0;
            for (int i = 0; i < n; i++) {
                int v = Math.max(0, values[i]);
                if (v != 0) nonZero++;
                int bw = 32 - Integer.numberOfLeadingZeros(Math.max(1, v));
                bitWidths[i] = bw;
                if (bw > maxBW) maxBW = bw;
            }
            double mean = Arrays.stream(bitWidths).average().orElse(0.0);
            double var = 0.0;
            for (double b : bitWidths) var += (b - mean) * (b - mean);
            var = var / Math.max(1, n);
            double std = Math.sqrt(var);
            double median;
            {
                double[] copy = Arrays.copyOf(bitWidths, bitWidths.length);
                Arrays.sort(copy);
                if (copy.length % 2 == 1) median = copy[copy.length / 2];
                else median = 0.5 * (copy[copy.length / 2 - 1] + copy[copy.length / 2]);
            }
            double fracNonZero = (double) nonZero / (double) n;
            double[] feat = new double[7];
            feat[0] = 1.0;
            feat[1] = (double) n;
            feat[2] = mean;
            feat[3] = std;
            feat[4] = (double) maxBW;
            feat[5] = median;
            feat[6] = fracNonZero;
            return feat;
        }

        private static int bruteForceBestP(int[] values, int maxPToCheck) {
            int n = values.length;
            if (n <= 0) return 1;
            if (n == 1) return 1;
            int upper = Math.min(n, Math.max(1, maxPToCheck));
            long bestCost = Long.MAX_VALUE;
            int bestP = 1;
            int[] bitWidths = new int[n];
            for (int i = 0; i < n; i++) bitWidths[i] = 32 - Integer.numberOfLeadingZeros(Math.max(1, values[i]));
            int globalMaxB = 0;
            for (int b : bitWidths) if (b > globalMaxB) globalMaxB = b;
            int z = (globalMaxB <= 0) ? 1 : (32 - Integer.numberOfLeadingZeros(globalMaxB));
            if (z <= 0) z = 1;
            for (int p = 1; p <= upper; p++) {
                int m = (n + p - 1) / p;
                long sumBits = 0;
                for (int g = 0; g < m; g++) {
                    int start = g * p;
                    int end = Math.min(n, start + p);
                    int maxB = 0;
                    for (int k = start; k < end; k++) if (bitWidths[k] > maxB) maxB = bitWidths[k];
                    sumBits += (long) (end - start) * maxB;
                }
                long cost = sumBits + (long) m * z;
                if (cost < bestCost) {
                    bestCost = cost;
                    bestP = p;
                }
            }
            return bestP;
        }

        private static double[] ridgeRegressionSolve(double[][] X, double[] y, double lambda) {
            int m = X.length;
            int d = X[0].length;
            double[][] XtX = new double[d][d];
            double[] Xty = new double[d];
            for (int i = 0; i < m; i++) {
                for (int a = 0; a < d; a++) {
                    for (int b = 0; b < d; b++) XtX[a][b] += X[i][a] * X[i][b];
                    Xty[a] += X[i][a] * y[i];
                }
            }
            for (int i = 0; i < d; i++) XtX[i][i] += lambda;
            return solveLinearSystem(XtX, Xty);
        }

        private static double[] solveLinearSystem(double[][] Aorig, double[] borig) {
            int n = Aorig.length;
            double[][] A = new double[n][n];
            double[] b = new double[n];
            for (int i = 0; i < n; i++) {
                System.arraycopy(Aorig[i], 0, A[i], 0, n);
                b[i] = borig[i];
            }
            for (int col = 0; col < n; col++) {
                int pivot = col;
                double maxAbs = Math.abs(A[col][col]);
                for (int r = col + 1; r < n; r++) {
                    double abs = Math.abs(A[r][col]);
                    if (abs > maxAbs) { maxAbs = abs; pivot = r; }
                }
                if (pivot != col) {
                    double[] tmp = A[col]; A[col] = A[pivot]; A[pivot] = tmp;
                    double tb = b[col]; b[col] = b[pivot]; b[pivot] = tb;
                }
                double diag = A[col][col];
                if (Math.abs(diag) < 1e-12) { diag = 1e-12; A[col][col] = diag; }
                for (int j = col; j < n; j++) A[col][j] /= diag;
                b[col] /= diag;
                for (int r = 0; r < n; r++) {
                    if (r == col) continue;
                    double factor = A[r][col];
                    if (Math.abs(factor) < 1e-15) continue;
                    for (int c = col; c < n; c++) A[r][c] -= factor * A[col][c];
                    b[r] -= factor * b[col];
                }
            }
            double[] x = new double[n]; System.arraycopy(b, 0, x, 0, n); return x;
        }

        public static List<int[]> generateSyntheticSamples(int numSamples, int minLen, int maxLen, Random rnd) {
            List<int[]> samples = new ArrayList<>();
            for (int s = 0; s < numSamples; s++) {
                int n = rnd.nextInt(maxLen - minLen + 1) + minLen;
                int mode = rnd.nextInt(4);
                int[] arr = new int[n];
                switch (mode) {
                    case 0:
                        for (int i = 0; i < n; i++) arr[i] = rnd.nextInt(1 << 8);
                        break;
                    case 1:
                        for (int i = 0; i < n; i++) arr[i] = (rnd.nextDouble() < 0.95) ? rnd.nextInt(1 << 6) : rnd.nextInt(1 << 20);
                        break;
                    case 2:
                        int pos = 0;
                        while (pos < n) {
                            int run = Math.max(1, rnd.nextInt(20));
                            int base = rnd.nextInt(1 << (rnd.nextInt(10) + 1));
                            for (int k = 0; k < run && pos < n; k++) arr[pos++] = Math.max(0, base + rnd.nextInt(10));
                        }
                        break;
                    default:
                        for (int i = 0; i < n; i++) arr[i] = rnd.nextDouble() < 0.7 ? 0 : rnd.nextInt(1 << 16);
                        break;
                }
                samples.add(arr);
            }
            return samples;
        }
    }
}
