package org.apache.iotdb.tsfile.encoding;

import org.junit.Test;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.*;

public class BPRL {

    static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data", "test.csv", "POI-lat.csv", "POI-lon.csv", "Basel-wind.csv", "Basel-temp.csv", "Air-sensor.csv");
    static final int CHUNK_SIZE = 1024;
    static final int INPUT_DIM = 16;  // 从8增加到16
    static final int HIDDEN_DIM1 = 64; // 第一隐藏层
    static final int HIDDEN_DIM2 = 32; // 第二隐藏层

    // ========== CSV loader & scaling helpers ==========
    static List<List<Integer>> loadDataFromCSV(String filename) {
        List<List<Integer>> sequences = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"?\\[([0-9,\\s]+)\\]\"?");
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
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
            if (s.isEmpty()) {
                vals[i] = BigDecimal.ZERO;
                continue;
            }
            s = s.replace(",", "");

            try {
                BigDecimal bd = new BigDecimal(s);
                BigDecimal scaled = bd.multiply(scale);
                BigDecimal rounded = scaled.setScale(0, RoundingMode.HALF_UP);
                vals[i] = rounded;
            } catch (Exception ex) {
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

        BigDecimal minv = vals[0];
        for (int i = 1; i < n; ++i) if (vals[i].compareTo(minv) < 0) minv = vals[i];

        for (int i = 0; i < n; ++i) {
            BigDecimal shifted = vals[i].subtract(minv);
            try {
                BigInteger bi = shifted.toBigIntegerExact();
                if (bi.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) result[i] = Long.MAX_VALUE;
                else if (bi.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) result[i] = Long.MIN_VALUE;
                else result[i] = bi.longValue();
            } catch (ArithmeticException ae) {
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

    // ========== Pack / Result / DecisionPoint ==========
    static class Pack {
        int size = 0;
        int maxBitWidth = 0;
        int startIndex = 0;
        List<Integer> indices = new ArrayList<>();
        List<Integer> bitWidths = new ArrayList<>();

        void addOctad(int index, int bitWidth) {
            if (size == 0) {
                startIndex = index;
                maxBitWidth = bitWidth;
            } else {
                if (bitWidth > maxBitWidth) maxBitWidth = bitWidth;
            }
            indices.add(index);
            bitWidths.add(bitWidth);
            size++;
        }

        long dataCost(long pack_size) {
            return pack_size * size * (long) maxBitWidth;
        }

        int logSize() {
            if (size <= 0) return 0;
            return 32 - Integer.numberOfLeadingZeros(size);
        }

        float efficiency(int packSize) {
            if (size == 0 || packSize == 0) return 0.0f;
            return (float) (size * maxBitWidth) / (packSize * 64.0f);
        }

        float bitWidthVariance() {
            if (size <= 1) return 0.0f;
            float sum = 0;
            for (int bw : bitWidths) {
                sum += Math.abs(bw - maxBitWidth);
            }
            return sum / (size * 64.0f);
        }
    }

    static class PackingResult {
        int packCount = 0;
        long dataCostA = 0;
        int bitWidthCostB = 0;
        int packSizeCostC = 0;
        long totalCost = 0;
        List<Pack> packs = new ArrayList<>();
        byte[] compressedData;

        void calculateCost(int maxLog) {
            bitWidthCostB = 6 * packCount;
            packSizeCostC = packCount * maxLog;
            totalCost = dataCostA + bitWidthCostB + packSizeCostC;
        }

        @Override
        public String toString() {
            return String.format("Packs: %d, Cost: %d (A=%d, B=%d, C=%d)", packCount, totalCost, dataCostA, bitWidthCostB, packSizeCostC);
        }

        float compressionRatio(long originalSize) {
            if (originalSize == 0) return 1.0f;
            return 1.0f - (float) totalCost / originalSize;
        }

        float averageEfficiency(int packSize) {
            if (packs.isEmpty()) return 0.0f;
            float sum = 0;
            for (Pack p : packs) {
                sum += p.efficiency(packSize);
            }
            return sum / packs.size();
        }

        float averageBitWidthVariance() {
            if (packs.isEmpty()) return 0.0f;
            float sum = 0;
            for (Pack p : packs) {
                sum += p.bitWidthVariance();
            }
            return sum / packs.size();
        }
    }

    static class DecisionPoint {
        int currentPackSize;
        int currentPackMaxB;
        int newOctadB;
        int packCount;
        int currentMaxLog;
        boolean action;
        float probability;
        float[] state;

        DecisionPoint(int cps, int cpm, int nob, int pc, int cml, boolean a, float p, float[] state) {
            currentPackSize = cps;
            currentPackMaxB = cpm;
            newOctadB = nob;
            packCount = pc;
            currentMaxLog = cml;
            action = a;
            probability = p;
            this.state = state != null ? state.clone() : null;
        }
    }

    // ========== 改进的探索策略 ==========
    static class ImprovedExploration {
        private float epsilon;
        private float epsilonDecay;
        private float minEpsilon;
        private float noiseStd;

        ImprovedExploration(float startEpsilon, float decay, float min, float noise) {
            this.epsilon = startEpsilon;
            this.epsilonDecay = decay;
            this.minEpsilon = min;
            this.noiseStd = noise;
        }

        boolean shouldExplore(Random rng) {
            return rng.nextFloat() < epsilon;
        }

        void decay() {
            epsilon = Math.max(minEpsilon, epsilon * epsilonDecay);
        }

        float addExplorationNoise(float probability, Random rng) {
            float noise = (float) rng.nextGaussian() * noiseStd;
            return Math.max(0.0f, Math.min(1.0f, probability + noise));
        }

        float getEpsilon() {
            return epsilon;
        }
    }

    // ========== 经验回放缓冲区 ==========
    static class ExperienceReplayBuffer {
        static class Experience {
            float[] state;
            boolean action;
            float reward;
            float[] nextState;
            boolean done;

            Experience(float[] s, boolean a, float r, float[] ns, boolean d) {
                state = s != null ? s.clone() : null;
                action = a;
                reward = r;
                nextState = ns != null ? ns.clone() : null;
                done = d;
            }
        }

        private List<Experience> buffer;
        private int capacity;
        private Random rng;

        ExperienceReplayBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new ArrayList<>(capacity);
            this.rng = new Random();
        }

        void add(Experience exp) {
            if (buffer.size() >= capacity) {
                buffer.remove(rng.nextInt(buffer.size()));
            }
            buffer.add(exp);
        }

        List<Experience> sample(int batchSize) {
            if (buffer.size() < batchSize) {
                return new ArrayList<>(buffer);
            }

            List<Experience> batch = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                batch.add(buffer.get(rng.nextInt(buffer.size())));
            }
            return batch;
        }

        int size() {
            return buffer.size();
        }

        void clear() {
            buffer.clear();
        }
    }

    // ========== 3-layer MLP with Dropout ==========
    static class ImprovedRLDecisionModel {
        float[] W1; // HIDDEN_DIM1 * INPUT_DIM
        float[] b1; // HIDDEN_DIM1
        float[] W2; // HIDDEN_DIM2 * HIDDEN_DIM1
        float[] b2; // HIDDEN_DIM2
        float[] W3; // HIDDEN_DIM2
        float b3;

        float learningRate = 0.01f;
        float lambda = 0.001f; // L2正则化系数
        float dropoutRate = 0.2f;
        boolean trainingMode = true;

        Random rng;

        ImprovedRLDecisionModel() {
            rng = new Random();

            // Xavier初始化
            W1 = new float[HIDDEN_DIM1 * INPUT_DIM];
            b1 = new float[HIDDEN_DIM1];
            W2 = new float[HIDDEN_DIM2 * HIDDEN_DIM1];
            b2 = new float[HIDDEN_DIM2];
            W3 = new float[HIDDEN_DIM2];

            float scale1 = (float) Math.sqrt(2.0 / (INPUT_DIM + HIDDEN_DIM1));
            float scale2 = (float) Math.sqrt(2.0 / (HIDDEN_DIM1 + HIDDEN_DIM2));
            float scale3 = (float) Math.sqrt(2.0 / (HIDDEN_DIM2 + 1));

            for (int i = 0; i < W1.length; ++i) W1[i] = (rng.nextFloat() - 0.5f) * scale1 * 2;
            for (int i = 0; i < b1.length; ++i) b1[i] = (rng.nextFloat() - 0.5f) * scale1 * 2;
            for (int i = 0; i < W2.length; ++i) W2[i] = (rng.nextFloat() - 0.5f) * scale2 * 2;
            for (int i = 0; i < b2.length; ++i) b2[i] = (rng.nextFloat() - 0.5f) * scale2 * 2;
            for (int i = 0; i < W3.length; ++i) W3[i] = (rng.nextFloat() - 0.5f) * scale3 * 2;
            b3 = (rng.nextFloat() - 0.5f) * scale3 * 2;
        }

        void setTrainingMode(boolean training) {
            this.trainingMode = training;
        }

        static float leakyRelu(float x) {
            return x > 0 ? x : 0.01f * x;
        }

        static float leakyReluDeriv(float x) {
            return x > 0 ? 1.0f : 0.01f;
        }

        static float sigmoid(float x) {
            if (x >= 0) {
                double z = Math.exp(-x);
                return (float) (1.0 / (1.0 + z));
            } else {
                double z = Math.exp(x);
                return (float) (z / (1.0 + z));
            }
        }

        float forward(float[] feat, float[] hidden1Out, float[] hidden2Out, float[] z1Out, float[] z2Out) {
            if (hidden1Out != null) Arrays.fill(hidden1Out, 0.0f);
            if (hidden2Out != null) Arrays.fill(hidden2Out, 0.0f);
            if (z1Out != null) Arrays.fill(z1Out, 0.0f);
            if (z2Out != null) Arrays.fill(z2Out, 0.0f);

            // 第一层
            float[] hidden1 = new float[HIDDEN_DIM1];
            for (int h = 0; h < HIDDEN_DIM1; ++h) {
                float z = b1[h];
                int base = h * INPUT_DIM;
                for (int j = 0; j < INPUT_DIM; ++j) {
                    z += W1[base + j] * feat[j];
                }
                if (z1Out != null) z1Out[h] = z;
                hidden1[h] = leakyRelu(z);

                // Dropout
                if (trainingMode && rng.nextFloat() < dropoutRate) {
                    hidden1[h] = 0.0f;
                }
            }

            // 第二层
            float[] hidden2 = new float[HIDDEN_DIM2];
            for (int h = 0; h < HIDDEN_DIM2; ++h) {
                float z = b2[h];
                int base = h * HIDDEN_DIM1;
                for (int j = 0; j < HIDDEN_DIM1; ++j) {
                    z += W2[base + j] * hidden1[j];
                }
                if (z2Out != null) z2Out[h] = z;
                hidden2[h] = leakyRelu(z);

                // Dropout
                if (trainingMode && rng.nextFloat() < dropoutRate) {
                    hidden2[h] = 0.0f;
                }
            }

            // 输出层
            float z3 = b3;
            for (int h = 0; h < HIDDEN_DIM2; ++h) {
                z3 += W3[h] * hidden2[h];
            }

            if (hidden1Out != null) System.arraycopy(hidden1, 0, hidden1Out, 0, HIDDEN_DIM1);
            if (hidden2Out != null) System.arraycopy(hidden2, 0, hidden2Out, 0, HIDDEN_DIM2);

            return sigmoid(z3);
        }

        float forward(float[] feat) {
            return forward(feat, null, null, null, null);
        }

        float trainWithExperience(List<ExperienceReplayBuffer.Experience> batch, int totalOctads) {
            if (batch == null || batch.isEmpty()) return 0.0f;

            float[] dW1 = new float[W1.length];
            float[] db1 = new float[b1.length];
            float[] dW2 = new float[W2.length];
            float[] db2 = new float[b2.length];
            float[] dW3 = new float[W3.length];
            float db3 = 0.0f;

            float totalLoss = 0.0f;
            int batchSize = batch.size();

            // 保存原始训练模式
            boolean originalTrainingMode = trainingMode;
            trainingMode = true;

            for (ExperienceReplayBuffer.Experience exp : batch) {
                float[] hidden1 = new float[HIDDEN_DIM1];
                float[] hidden2 = new float[HIDDEN_DIM2];
                float[] z1 = new float[HIDDEN_DIM1];
                float[] z2 = new float[HIDDEN_DIM2];

                float p = forward(exp.state, hidden1, hidden2, z1, z2);
                float pClipped = Math.min(Math.max(p, 1e-6f), 1.0f - 1e-6f);

                float piA = exp.action ? pClipped : (1.0f - pClipped);
                if (piA <= 0.0f) {
                    piA = 1e-6f;
                }

                // 添加L2正则化
                float l2Reg = 0.0f;
                for (int i = 0; i < W1.length; ++i) l2Reg += W1[i] * W1[i];
                for (int i = 0; i < W2.length; ++i) l2Reg += W2[i] * W2[i];
                for (int i = 0; i < W3.length; ++i) l2Reg += W3[i] * W3[i];
                l2Reg = lambda * l2Reg;

                float lossI = -exp.reward * (float) Math.log(piA) + l2Reg;

                if (Float.isNaN(lossI) || Float.isInfinite(lossI)) {
                    System.err.printf("Warning: loss is NaN or Infinite. p=%.8f, piA=%.8f, reward=%.8f\n", p, piA, exp.reward);
                    lossI = 0.0f;
                }

                totalLoss += lossI;

                // 计算梯度
                float dL_dz3 = exp.reward * (p - (exp.action ? 1.0f : 0.0f));

                // 输出层梯度
                for (int h = 0; h < HIDDEN_DIM2; ++h) {
                    dW3[h] += dL_dz3 * hidden2[h] + 2 * lambda * W3[h];
                }
                db3 += dL_dz3;

                // 第二隐藏层梯度
                for (int h = 0; h < HIDDEN_DIM2; ++h) {
                    float w3h = W3[h];
                    float dh = dL_dz3 * w3h;
                    float dReLU = leakyReluDeriv(z2[h]);
                    float dZ2 = dh * dReLU;

                    int base = h * HIDDEN_DIM1;
                    for (int j = 0; j < HIDDEN_DIM1; ++j) {
                        dW2[base + j] += dZ2 * hidden1[j] + 2 * lambda * W2[base + j];
                    }
                    db2[h] += dZ2;
                }

                // 第一隐藏层梯度
                for (int h1 = 0; h1 < HIDDEN_DIM1; ++h1) {
                    float dh1 = 0.0f;
                    for (int h2 = 0; h2 < HIDDEN_DIM2; ++h2) {
                        dh1 += W2[h2 * HIDDEN_DIM1 + h1] * leakyReluDeriv(z2[h2]) * dL_dz3 * W3[h2];
                    }

                    float dReLU1 = leakyReluDeriv(z1[h1]);
                    float dZ1 = dh1 * dReLU1;

                    int base = h1 * INPUT_DIM;
                    for (int j = 0; j < INPUT_DIM; ++j) {
                        dW1[base + j] += dZ1 * exp.state[j] + 2 * lambda * W1[base + j];
                    }
                    db1[h1] += dZ1;
                }
            }

            // 更新权重
            float lr = learningRate / batchSize;
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
            for (int i = 0; i < b2.length; ++i) {
                b2[i] -= lr * db2[i];
                b2[i] = clip(b2[i], -10f, 10f);
            }
            for (int i = 0; i < W3.length; ++i) {
                W3[i] -= lr * dW3[i];
                W3[i] = clip(W3[i], -10f, 10f);
            }
            b3 -= lr * db3;
            b3 = clip(b3, -10f, 10f);

            // 恢复训练模式
            trainingMode = originalTrainingMode;

            return totalLoss / batchSize;
        }

        static float clip(float v, float low, float high) {
            return Math.min(Math.max(v, low), high);
        }

        void saveModel(String filename) throws IOException {
            try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(filename))) {
                dos.writeInt(W1.length);
                for (float w : W1) dos.writeFloat(w);
                for (float b : b1) dos.writeFloat(b);

                dos.writeInt(W2.length);
                for (float w : W2) dos.writeFloat(w);
                for (float b : b2) dos.writeFloat(b);

                dos.writeInt(W3.length);
                for (float w : W3) dos.writeFloat(w);
                dos.writeFloat(b3);
            }
        }

        void loadModel(String filename) throws IOException {
            try (DataInputStream dis = new DataInputStream(new FileInputStream(filename))) {
                int w1Len = dis.readInt();
                if (w1Len != W1.length) throw new IOException("W1 dimension mismatch");
                for (int i = 0; i < W1.length; i++) W1[i] = dis.readFloat();
                for (int i = 0; i < b1.length; i++) b1[i] = dis.readFloat();

                int w2Len = dis.readInt();
                if (w2Len != W2.length) throw new IOException("W2 dimension mismatch");
                for (int i = 0; i < W2.length; i++) W2[i] = dis.readFloat();
                for (int i = 0; i < b2.length; i++) b2[i] = dis.readFloat();

                int w3Len = dis.readInt();
                if (w3Len != W3.length) throw new IOException("W3 dimension mismatch");
                for (int i = 0; i < W3.length; i++) W3[i] = dis.readFloat();
                b3 = dis.readFloat();
            }
        }
    }

    // ========== 改进的奖励函数 ==========
    static class ImprovedRewardFunction {
        static float calculateReward(PackingResult rlResult, PackingResult baseline,
                                     int totalOctads, long originalSize, int packSize) {

            // 基础奖励：压缩率改进
            float rlCompression = rlResult.compressionRatio(originalSize);
            float baselineCompression = baseline.compressionRatio(originalSize);
            float compressionReward = rlCompression - baselineCompression;

            // 惩罚过多pack（相对基线）
            float packPenalty = 0;
            if (rlResult.packCount > baseline.packCount * 1.5f) {
                packPenalty = -0.2f * (rlResult.packCount - baseline.packCount) / totalOctads;
            }

            // 奖励pack效率
            float efficiencyReward = 0;
            float rlEfficiency = rlResult.averageEfficiency(packSize);
            float baselineEfficiency = baseline.averageEfficiency(packSize);
            efficiencyReward = (rlEfficiency - baselineEfficiency) * 0.5f;

            // 惩罚bitWidth方差（鼓励均匀性）
            float variancePenalty = 0;
            float rlVariance = rlResult.averageBitWidthVariance();
            float baselineVariance = baseline.averageBitWidthVariance();
            variancePenalty = (baselineVariance - rlVariance) * 0.3f;

            // 鼓励更大的pack（但不要太大）
            float sizeReward = 0;
            if (rlResult.packs.size() > 0) {
                float avgSize = 0;
                for (Pack p : rlResult.packs) avgSize += p.size;
                avgSize /= rlResult.packs.size();
                sizeReward = Math.min(0.1f, avgSize / 100.0f);
            }

            // 最终加权奖励
            float totalReward = compressionReward * 0.4f + efficiencyReward * 0.3f
                    + variancePenalty * 0.2f + sizeReward * 0.1f + packPenalty;

            return totalReward;
        }
    }

    // ========== Bitpacking utility methods ==========
    public static int getBitWidth(int num) {
        if (num == 0)
            return 1;
        else
            return 32 - Integer.numberOfLeadingZeros(num);
    }

    private static class BitWriter {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private long acc = 0L;
        private int accBits = 0;

        void writeBits(long bits, int bitCount) {
            if (bitCount == 0) return;
            if (bitCount == 64) {
                writeBits((bits >>> 32) & 0xFFFFFFFFL, 32);
                writeBits(bits & 0xFFFFFFFFL, 32);
                return;
            }
            long mask = (bitCount == 64) ? ~0L : ((1L << bitCount) - 1L);
            long v = bits & mask;
            acc = (acc << bitCount) | v;
            accBits += bitCount;
            while (accBits >= 8) {
                int shift = accBits - 8;
                int outb = (int) ((acc >>> shift) & 0xFFL);
                out.write(outb);
                if (shift > 0) {
                    acc &= ((1L << shift) - 1L);
                } else {
                    acc = 0L;
                }
                accBits = shift;
            }
        }

        byte[] finish() {
            if (accBits > 0) {
                int outb = (int) ((acc << (8 - accBits)) & 0xFFL);
                out.write(outb);
                acc = 0L;
                accBits = 0;
            }
            return out.toByteArray();
        }
    }

    public static final class BitReader {
        private final byte[] data;
        private int bitPos;

        public BitReader(byte[] data) {
            this(data, 0);
        }

        public BitReader(byte[] data, int byteOffset) {
            this.data = data;
            this.bitPos = byteOffset * 8;
        }

        public long readBits(int n) {
            if (n == 0) return 0L;
            if (n < 0 || n > 64) {
                throw new IllegalArgumentException("n must be between 0 and 64");
            }

            long result = 0L;
            int bitsRemaining = n;

            while (bitsRemaining > 0) {
                int byteIndex = bitPos >>> 3;
                int bitOffset = bitPos & 7;

                if (byteIndex >= data.length) {
                    result = (result << bitsRemaining);
                    bitPos += bitsRemaining;
                    return result;
                }

                int bitsFromCurrentByte = Math.min(8 - bitOffset, bitsRemaining);
                int curByte = data[byteIndex] & 0xFF;
                int shift = 8 - bitOffset - bitsFromCurrentByte;
                int chunk = (curByte >>> shift) & ((1 << bitsFromCurrentByte) - 1);

                result = (result << bitsFromCurrentByte) | chunk;

                bitPos += bitsFromCurrentByte;
                bitsRemaining -= bitsFromCurrentByte;
            }

            return result;
        }

        public int consumedBits() {
            return bitPos;
        }

        public int bitPosition() {
            return bitPos;
        }

        public int remainingBits() {
            return (data.length * 8) - bitPos;
        }
    }

    private static byte[] performBitPackingCompression64_fast(long[] dataArray, List<Pack> packs, int pack_size, int originalLength) throws IOException {
        int totalPacks = packs.size();
        int maxOctadsInAnyPack = 0;
        for (Pack p : packs) if (p.size > maxOctadsInAnyPack) maxOctadsInAnyPack = p.size;

        int bitsForCount = 1;
        while ((1L << bitsForCount) <= maxOctadsInAnyPack) bitsForCount++;
        if (bitsForCount <= 0) bitsForCount = 1;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(totalPacks);
        dos.writeByte(bitsForCount);
        dos.flush();

        BitWriter metaWriter = new BitWriter();

        for (Pack pack : packs) {
            metaWriter.writeBits(pack.size, bitsForCount);
            metaWriter.writeBits(pack.bitWidths.get(0), 6);
        }

        byte[] metaBytes = metaWriter.finish();
        dos.write(metaBytes);
        dos.flush();

        BitWriter dataWriter = new BitWriter();

        for (Pack pack : packs) {
            int packMaxBW = pack.maxBitWidth;
            for (int i = 0; i < pack.size; ++i) {
                int originalGroupIndex = pack.indices.get(0);
                int startPos = originalGroupIndex * pack_size;
                for (int j = 0; j < pack_size; ++j) {
                    long val = dataArray[startPos + j];
                    if (packMaxBW == 0) {
                        dataWriter.writeBits(0L, 0);
                    } else {
                        if (packMaxBW == 64) {
                            dataWriter.writeBits(val, 64);
                        } else {
                            long mask = (1L << packMaxBW) - 1L;
                            long masked = val & mask;
                            dataWriter.writeBits(masked, packMaxBW);
                        }
                    }
                }
            }
        }

        byte[] dataBytes = dataWriter.finish();
        dos.write(dataBytes);
        dos.flush();

        return baos.toByteArray();
    }

    // ========== 构建16维状态特征 ==========
    static float[] buildState(int currentPackSize, int currentPackMaxB, int newOctadB,
                              int packCount, int totalOctads, int currentIndex,
                              int currentMaxLog, int packSize) {
        float[] state = new float[INPUT_DIM];

        state[0] = currentPackSize / 100.0f;                   // 当前pack大小
        state[1] = currentPackMaxB / 64.0f;                   // 当前pack最大位宽
        state[2] = newOctadB / 64.0f;                         // 新octad位宽
        state[3] = Math.abs(newOctadB - currentPackMaxB) / 64.0f; // 位宽差异
        state[4] = (currentPackSize * currentPackMaxB) / (1024.0f * 64.0f); // 当前pack密度
        state[5] = packCount / 100.0f;                        // 已创建的pack数量
        state[6] = (totalOctads - currentIndex) / (float) totalOctads; // 剩余octad比例
        state[7] = currentMaxLog / 10.0f;                     // 最大log尺寸

        // 新增特征
        state[8] = (currentPackMaxB == 0 ? 0 : (float) newOctadB / currentPackMaxB); // 位宽比例
        state[9] = currentPackSize * currentPackMaxB / (float)(packSize * 64); // 当前pack效率

        // 预测合并后密度
        int newMaxBw = Math.max(currentPackMaxB, newOctadB);
        state[10] = ((currentPackSize + 1) * newMaxBw) / (float)(packSize * 64);

        state[11] = (packSize - currentPackSize) / (float) packSize; // pack剩余容量比例
        state[12] = (float) Math.log(currentPackSize + 1) / 10f; // pack大小对数特征
        state[13] = (float) Math.log(newOctadB + 1) / 10f;      // 位宽对数特征
        state[14] = (currentPackMaxB > newOctadB ? 1f : 0f);    // 是否当前位宽更大
        state[15] = currentIndex / (float) totalOctads;         // 进度

        return state;
    }

    // ========== 改进的贪婪算法基线 ==========
    static PackingResult greedyImprovedPackOctads(List<Integer> bitWidths, int pack_size) {
        PackingResult result = new PackingResult();
        List<Pack> packs = new ArrayList<>();

        // 第一阶段：基于位宽聚类
        Map<Integer, List<Integer>> bitWidthGroups = new TreeMap<>();
        for (int i = 0; i < bitWidths.size(); i++) {
            int bw = bitWidths.get(i);
            bitWidthGroups.computeIfAbsent(bw, k -> new ArrayList<>()).add(i);
        }

        // 对每个位宽组进行打包
        for (Map.Entry<Integer, List<Integer>> entry : bitWidthGroups.entrySet()) {
            List<Integer> indices = entry.getValue();
            int groupBw = entry.getKey();

            // 贪心打包：尽可能填满每个pack
            Pack currentPack = new Pack();
            for (int idx : indices) {
                if (currentPack.size == 0) {
                    currentPack.addOctad(idx, groupBw);
                } else if (currentPack.size < 100) { // pack容量限制
                    // 计算合并代价
                    int newMaxBw = Math.max(currentPack.maxBitWidth, groupBw);
                    long currentCost = currentPack.dataCost(1);
                    long mergeCost = (currentPack.size + 1) * newMaxBw;

                    if (mergeCost - currentCost <= groupBw * 2) { // 容忍度阈值
                        currentPack.maxBitWidth = newMaxBw;
                        currentPack.addOctad(idx, groupBw);
                    } else {
                        packs.add(currentPack);
                        currentPack = new Pack();
                        currentPack.addOctad(idx, groupBw);
                    }
                } else {
                    packs.add(currentPack);
                    currentPack = new Pack();
                    currentPack.addOctad(idx, groupBw);
                }
            }
            if (currentPack.size > 0) {
                packs.add(currentPack);
            }
        }

        // 第二阶段：尝试合并相似的pack
        List<Pack> mergedPacks = new ArrayList<>();
        packs.sort((a, b) -> Integer.compare(a.maxBitWidth, b.maxBitWidth));

        for (int i = 0; i < packs.size(); i++) {
            Pack p = packs.get(i);
            boolean merged = false;

            for (Pack mp : mergedPacks) {
                // 如果两个pack的位宽接近且合并后有效
                if (Math.abs(mp.maxBitWidth - p.maxBitWidth) <= 2 &&
                        mp.size + p.size <= 100) {
                    int newMaxBw = Math.max(mp.maxBitWidth, p.maxBitWidth);
                    long oldCost = mp.dataCost(1) + p.dataCost(1);
                    long newCost = (mp.size + p.size) * newMaxBw;

                    if (newCost <= oldCost + 64) { // 允许少量开销
                        // 合并pack
                        mp.size += p.size;
                        mp.maxBitWidth = newMaxBw;
                        mp.indices.addAll(p.indices);
                        mp.bitWidths.addAll(p.bitWidths);
                        merged = true;
                        break;
                    }
                }
            }

            if (!merged) {
                mergedPacks.add(p);
            }
        }

        // 计算最终结果
        result.packs = mergedPacks;
        result.packCount = mergedPacks.size();

        // 计算成本
        int globalMaxLog = 0;
        for (Pack p : mergedPacks) {
            result.dataCostA += p.dataCost(pack_size);
            int logSize = p.logSize();
            if (logSize > globalMaxLog) globalMaxLog = logSize;
        }

        result.calculateCost(globalMaxLog);
        return result;
    }

    // ========== 改进的packOctads方法 ==========
    static PackingResult packOctadsImproved(List<Integer> bitWidths, ImprovedRLDecisionModel model,
                                            List<DecisionPoint> decisionTrace, int pack_size,
                                            long[] dataArray, int originalLength,
                                            ImprovedExploration exploration) {
        PackingResult result = new PackingResult();
        Pack currentPack = new Pack();
        int globalMaxLog = 0;
        int packCount = 0;
        int totalOctads = bitWidths.size();

        Random localRng = ThreadLocalRandom.current();

        for (int i = 0; i < bitWidths.size(); ++i) {
            int b = bitWidths.get(i);

            if (currentPack.size == 0) {
                currentPack.addOctad(i, b);
            } else if (b == currentPack.maxBitWidth) {
                currentPack.addOctad(i, b);
            } else {
                // 构建16维特征
                float[] state = buildState(currentPack.size, currentPack.maxBitWidth, b,
                        packCount, totalOctads, i, globalMaxLog, pack_size);

                float probability = model.forward(state);

                // 应用探索噪声
                probability = exploration.addExplorationNoise(probability, localRng);

                boolean shouldMerge;
                if (exploration.shouldExplore(localRng)) {
                    shouldMerge = (localRng.nextFloat() > 0.5f);
                } else {
                    shouldMerge = probability > 0.5f;
                }

                if (decisionTrace != null) {
                    decisionTrace.add(new DecisionPoint(currentPack.size, currentPack.maxBitWidth,
                            b, packCount, globalMaxLog, shouldMerge,
                            probability, state));
                }

                if (shouldMerge) {
                    currentPack.addOctad(i, b);
                } else {
                    result.dataCostA += currentPack.dataCost(pack_size);
                    int logSize = currentPack.logSize();
                    if (logSize > globalMaxLog) globalMaxLog = logSize;
                    result.packs.add(currentPack);
                    packCount++;

                    currentPack = new Pack();
                    currentPack.addOctad(i, b);
                }
            }
        }

        if (currentPack.size > 0) {
            result.dataCostA += currentPack.dataCost(pack_size);
            int logSize = currentPack.logSize();
            if (logSize > globalMaxLog) globalMaxLog = logSize;
            result.packs.add(currentPack);
            packCount++;
        }

        result.packCount = packCount;
        result.calculateCost(globalMaxLog);

        if (dataArray != null) {
            try {
                result.compressedData = performBitPackingCompression64_fast(dataArray, result.packs, pack_size, originalLength);
            } catch (IOException e) {
                System.err.println("Compression failed: " + e.getMessage());
                result.compressedData = null;
            }
        }

        return result;
    }

    public static long[] fastDecompress(byte[] compressedData, int[] bitWidths, int packSize, int originalLength) {
        if (compressedData == null || compressedData.length == 0) {
            System.err.println("Compressed data is null or empty");
            return new long[0];
        }

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
            DataInputStream dis = new DataInputStream(bais);

            int totalPacks = dis.readUnsignedByte();
            int bitsForCount = dis.readUnsignedByte();

            int metaStartOffset = 2;
            BitReader metaReader = new BitReader(compressedData, metaStartOffset);

            List<PackInfo> packInfos = new ArrayList<>();

            for (int p = 0; p < totalPacks; ++p) {
                int octadCount = (int) metaReader.readBits(bitsForCount);
                int packBitWidth = (int) metaReader.readBits(6);
                if (packBitWidth == 63) packBitWidth = 64;

                packInfos.add(new PackInfo(octadCount, packBitWidth));
            }

            int metaBitsUsed = metaReader.consumedBits();
            int dataStartByte = metaStartOffset + (metaBitsUsed + 7) / 8;

            if (dataStartByte >= compressedData.length) {
                return new long[0];
            }

            BitReader dataReader = new BitReader(compressedData, dataStartByte);
            List<Long> resultList = new ArrayList<>();

            for (PackInfo packInfo : packInfos) {
                int octadCount = packInfo.octadCount;
                int bitWidth = packInfo.bitWidth;

                for (int i = 0; i < octadCount; ++i) {
                    for (int j = 0; j < packSize; ++j) {
                        long value;
                        if (bitWidth == 0) {
                            value = 0L;
                        } else if (bitWidth == 64) {
                            long high = dataReader.readBits(32);
                            long low = dataReader.readBits(32);
                            value = (high << 32) | low;
                        } else {
                            value = dataReader.readBits(bitWidth);
                        }
                        resultList.add(value);
                    }
                }
            }

            long[] result = new long[Math.min(resultList.size(), originalLength)];
            for (int i = 0; i < result.length; i++) {
                result[i] = resultList.get(i);
            }

            return result;

        } catch (Exception e) {
            System.err.println("Fast decompression failed: " + e.getMessage());
            e.printStackTrace();
            return new long[0];
        }
    }

    static class PackInfo {
        int octadCount;
        int bitWidth;

        PackInfo(int octadCount, int bitWidth) {
            this.octadCount = octadCount;
            this.bitWidth = bitWidth;
        }
    }

    // ========== 改进的训练方法 ==========
    static ImprovedRLDecisionModel trainImprovedModel(int epochs, String csvFilePath) {
        System.err.println("Training improved RL model...");
        ImprovedRLDecisionModel model = new ImprovedRLDecisionModel();
        List<List<Integer>> sequences = loadDataFromCSV(csvFilePath);

        if (sequences.isEmpty()) {
            System.err.println("No data loaded from CSV. Returning initial model.");
            return model;
        }

        System.err.println("Loaded " + sequences.size() + " sequences from CSV");

        // 创建经验回放缓冲区
        ExperienceReplayBuffer replayBuffer = new ExperienceReplayBuffer(10000);

        // 改进的探索策略
        ImprovedExploration exploration = new ImprovedExploration(0.5f, 0.995f, 0.05f, 0.1f);

        // 存储每个epoch的最佳奖励
        float bestAverageReward = -Float.MAX_VALUE;
        ImprovedRLDecisionModel bestModel = null;

        for (int epoch = 1; epoch <= epochs; ++epoch) {
            long startTime = System.nanoTime();
            float totalReward = 0.0f;
            float totalLoss = 0.0f;
            int processedSequences = 0;

            for (List<Integer> bitWidths : sequences) {
                // 获取改进的贪婪算法基线
                PackingResult baseline = greedyImprovedPackOctads(bitWidths, 1);
                long originalSize = bitWidths.size() * 64L;

                // 使用改进的算法收集决策轨迹
                List<DecisionPoint> decisionTrace = new ArrayList<>();
                PackingResult result = packOctadsImproved(
                        bitWidths, model, decisionTrace, 1, null, 0, exploration
                );

                // 计算改进的奖励
                float reward = ImprovedRewardFunction.calculateReward(
                        result, baseline, bitWidths.size(), originalSize, 1
                );

                totalReward += reward;

                // 存储经验到回放缓冲区
                for (DecisionPoint dp : decisionTrace) {
                    // 这里简化处理，实际应用中应该构建nextState
                    float[] nextState = buildState(
                            dp.action ? dp.currentPackSize + 1 : 1,
                            dp.action ? Math.max(dp.currentPackMaxB, dp.newOctadB) : dp.newOctadB,
                            0, // 下一个octad未知，设为0
                            dp.action ? dp.packCount : dp.packCount + 1,
                            bitWidths.size(),
                            -1, // 索引不适用
                            dp.currentMaxLog,
                            1
                    );

                    replayBuffer.add(new ExperienceReplayBuffer.Experience(
                            dp.state, dp.action, reward, nextState, false
                    ));
                }

                processedSequences++;
            }

            // 从回放缓冲区采样进行训练
            if (replayBuffer.size() >= 64) {
                List<ExperienceReplayBuffer.Experience> batch = replayBuffer.sample(64);
                float loss = model.trainWithExperience(batch, -1); // totalOctads参数不再需要
                totalLoss += loss;
            }

            exploration.decay();

            long durationMs = (System.nanoTime() - startTime) / 1_000_000L;
            float avgReward = totalReward / processedSequences;

            // 保存最佳模型
            if (avgReward > bestAverageReward) {
                bestAverageReward = avgReward;
                bestModel = new ImprovedRLDecisionModel();
                // 复制模型参数（简化处理，实际应该深度复制）
                System.arraycopy(model.W1, 0, bestModel.W1, 0, model.W1.length);
                System.arraycopy(model.b1, 0, bestModel.b1, 0, model.b1.length);
                System.arraycopy(model.W2, 0, bestModel.W2, 0, model.W2.length);
                System.arraycopy(model.b2, 0, bestModel.b2, 0, model.b2.length);
                System.arraycopy(model.W3, 0, bestModel.W3, 0, model.W3.length);
                bestModel.b3 = model.b3;
            }

            if (epoch % 5 == 0 || epoch == 1 || epoch == epochs) {
                System.out.printf("Epoch %d: Avg Reward = %.6f, Avg Loss = %.6f, Exploration = %.4f, Time = %d ms%n",
                        epoch,
                        avgReward,
                        totalLoss / Math.max(1, processedSequences),
                        exploration.getEpsilon(),
                        durationMs);
            } else {
                System.out.printf("Epoch %d done. Time = %d ms%n", epoch, durationMs);
            }
        }

        // 返回最佳模型
        return bestModel != null ? bestModel : model;
    }

    // ========== 性能测试 ==========
    static void performanceTest(ImprovedRLDecisionModel model, String directory, String outputDirStr) {
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
                    // 修正column head按照要求
                    String[] head = {
                            "Input Direction",
                            "Encoding Algorithm",
                            "Encoding Time",
                            "Points",
                            "Compressed Size",
                            "Pack Size",
                            "Compression Ratio"
                    };
                    writer.write(String.join(",", head) + "\n");

                    int time_of_repeat = 10;

                    for (int pack_size_exp = 3; pack_size_exp < 4; pack_size_exp++) {
                        int pack_size = (int) Math.pow(2, pack_size_exp);
                        BigDecimal modelCost = BigDecimal.ZERO;
                        BigDecimal modelTime = BigDecimal.ZERO;
                        BigDecimal modelDecodeTime = BigDecimal.ZERO;

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

                                int remainder = scaledInts.length % pack_size;
                                int padding = (remainder == 0) ? 0 : pack_size - remainder;
                                long[] padded = new long[scaledInts.length + padding];
                                System.arraycopy(scaledInts, 0, padded, 0, scaledInts.length);
                                if (padding > 0) Arrays.fill(padded, scaledInts.length, padded.length, 0L);

                                int groups = padded.length / pack_size;
                                int[] bitWidths = new int[groups];
                                int gidx = 0;
                                for (int si = 0; si < padded.length; si += pack_size) {
                                    long maxInGroup = 0;
                                    for (int sj = si; sj < si + pack_size; ++sj) {
                                        long v = padded[sj];
                                        if (v > maxInGroup) maxInGroup = v;
                                    }
                                    int bitWidth = 0;
                                    if (maxInGroup > 0) {
                                        bitWidth = 64 - Long.numberOfLeadingZeros(maxInGroup);
                                    } else {
                                        bitWidth = 0;
                                    }
                                    bitWidths[gidx++] = bitWidth;
                                }

                                List<Integer> bitWidthsList = new ArrayList<>(groups);
                                for (int x = 0; x < groups; ++x) bitWidthsList.add(bitWidths[x]);

                                // 使用改进的探索策略进行测试
                                ImprovedExploration testExploration = new ImprovedExploration(0.05f, 1.0f, 0.05f, 0.0f);
                                model.setTrainingMode(false);
                                PackingResult res = packOctadsImproved(bitWidthsList, model, null, pack_size, padded, scaledInts.length, testExploration);
                                long duration = System.nanoTime() - startTime;
                                modelTime = modelTime.add(BigDecimal.valueOf(duration));
                                modelCost = modelCost.add(BigDecimal.valueOf(res.compressedData.length * 8L));

                                if (res.compressedData != null) {
                                    long startDecodeTime = System.nanoTime();
                                    long[] decompressed = fastDecompress(res.compressedData, bitWidths, pack_size, scaledInts.length);
                                    long decodeDuration = System.nanoTime() - startDecodeTime;
                                    modelDecodeTime = modelDecodeTime.add(BigDecimal.valueOf(decodeDuration));
                                }
                            }
                        }

                        BigDecimal timeOfRepeatBD = BigDecimal.valueOf(time_of_repeat);
                        modelCost = modelCost.divide(timeOfRepeatBD, 10, RoundingMode.HALF_UP);
                        modelTime = modelTime.divide(timeOfRepeatBD, 10, RoundingMode.HALF_UP);
                        modelDecodeTime = modelDecodeTime.divide(timeOfRepeatBD, 10, RoundingMode.HALF_UP);

                        BigDecimal numbersSizeBD = BigDecimal.valueOf(numbers.size());
                        BigDecimal model_ratio = modelCost.divide(numbersSizeBD.multiply(BigDecimal.valueOf(64)), 10, RoundingMode.HALF_UP);
                        BigDecimal modelTime_throughput = numbersSizeBD.multiply(BigDecimal.valueOf(8000L)).divide(modelTime, 10, RoundingMode.HALF_UP);

                        // 写入数据，只包含7列（去掉Decoding Time）
                        writer.write(entry.toString() + ",");
                        writer.write("BP-RL-Improved-Complete,");
                        writer.write(modelTime_throughput.toPlainString() + ",");
                        writer.write(String.valueOf(numbers.size()) + ",");
                        writer.write(modelCost.toPlainString() + ",");
                        writer.write(String.valueOf(pack_size) + ",");
                        writer.write(model_ratio.toPlainString() + "\n");
                    }
                } catch (IOException e) {
                    System.err.println("Error writing output file for " + fname);
                }
            }
        } catch (IOException e) {
            System.err.println("Error iterating directory: " + directory);
        }
    }

    // ========== 主函数 ==========
    public static void main(String[] args) {
        String trainCsv = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/processed_data.csv";
        String dataDir = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outDir = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_BPRL";

        int epochs = 200; // 增加训练轮数

        if (args.length >= 1) trainCsv = args[0];
        if (args.length >= 2) dataDir = args[1];
        if (args.length >= 3) outDir = args[2];

        ImprovedRLDecisionModel model = new ImprovedRLDecisionModel();
        if (!trainCsv.isEmpty()) {
            model = trainImprovedModel(epochs, trainCsv);
            try {
                model.saveModel("improved_rl_model.bin");
                System.out.println("Model saved to improved_rl_model.bin");
            } catch (IOException e) {
                System.err.println("Failed to save model: " + e.getMessage());
            }
        } else {
            System.err.println("No training CSV given. Using randomly initialized model.");
        }

        if (!dataDir.isEmpty()) {
            performanceTest(model, dataDir, outDir);
        } else {
            System.err.println("No data directory provided for performanceTest. Exiting.");
        }
    }

    // ========== 测试方法 ==========
    @Test
    public void TestImprovedModelVarPackSize() {
        String trainCsv = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/processed_data.csv";
        String dataDir = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outDir = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_BPRL_vary_pack_size";

        int epochs = 100;

        long startTime = System.nanoTime();
        ImprovedRLDecisionModel model = trainImprovedModel(epochs, trainCsv);
        long modelTime = System.nanoTime() - startTime;
        System.out.println("Training time: " + modelTime / 1_000_000 + " ms");

        // 使用不同pack size进行测试
        performanceVarPackSizeTest(model, dataDir, outDir);
    }

    // ========== 不同pack size的测试 ==========
    static void performanceVarPackSizeTest(ImprovedRLDecisionModel model, String directory, String outputDirStr) {
        System.out.println("\nPerformance Testing with Varying Pack Sizes...");
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
                if (!fname.equals("EPM-Education.csv") && !fname.equals("TH-Climate.csv")) continue;
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
                    // 修正column head按照要求
                    String[] head = {
                            "Input Direction",
                            "Encoding Algorithm",
                            "Encoding Time",
                            "Points",
                            "Compressed Size",
                            "Pack Size",
                            "Compression Ratio"
                    };
                    writer.write(String.join(",", head) + "\n");

                    int time_of_repeat = 10;

                    for (int pack_size_exp = 0; pack_size_exp < 10; pack_size_exp++) {
                        int pack_size = (int) Math.pow(2, pack_size_exp);

                        BigDecimal modelCost = BigDecimal.ZERO;
                        BigDecimal modelTime = BigDecimal.ZERO;
                        BigDecimal modelDecodeTime = BigDecimal.ZERO;
                        long compressedSize = 0;

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

                                int remainder = scaledInts.length % pack_size;
                                int padding = (remainder == 0) ? 0 : pack_size - remainder;
                                long[] padded = new long[scaledInts.length + padding];
                                System.arraycopy(scaledInts, 0, padded, 0, scaledInts.length);
                                if (padding > 0) Arrays.fill(padded, scaledInts.length, padded.length, 0L);

                                int groups = padded.length / pack_size;
                                int[] bitWidths = new int[groups];
                                int gidx = 0;
                                for (int si = 0; si < padded.length; si += pack_size) {
                                    long maxInGroup = 0;
                                    for (int sj = si; sj < si + pack_size; ++sj) {
                                        long v = padded[sj];
                                        if (v > maxInGroup) maxInGroup = v;
                                    }
                                    int bitWidth = 0;
                                    if (maxInGroup > 0) {
                                        bitWidth = 64 - Long.numberOfLeadingZeros(maxInGroup);
                                    } else {
                                        bitWidth = 0;
                                    }
                                    bitWidths[gidx++] = bitWidth;
                                }

                                List<Integer> bitWidthsList = new ArrayList<>(groups);
                                for (int x = 0; x < groups; ++x) bitWidthsList.add(bitWidths[x]);

                                // RL策略
                                ImprovedExploration testExploration = new ImprovedExploration(0.05f, 1.0f, 0.05f, 0.0f);
                                model.setTrainingMode(false);
                                PackingResult res = packOctadsImproved(bitWidthsList, model, null, pack_size, padded, scaledInts.length, testExploration);
                                long duration = System.nanoTime() - startTime;
                                modelTime = modelTime.add(BigDecimal.valueOf(duration));
                                modelCost = modelCost.add(BigDecimal.valueOf(res.compressedData.length * 8L));

                                if (res.compressedData != null) {
                                    long startDecodeTime = System.nanoTime();
                                    long[] decompressed = fastDecompress(res.compressedData, bitWidths, pack_size, scaledInts.length);
                                    long decodeDuration = System.nanoTime() - startDecodeTime;
                                    modelDecodeTime = modelDecodeTime.add(BigDecimal.valueOf(decodeDuration));
                                }
                            }
                        }

                        BigDecimal timeOfRepeatBD = BigDecimal.valueOf(time_of_repeat);
                        modelCost = modelCost.divide(timeOfRepeatBD, 10, RoundingMode.HALF_UP);
                        modelTime = modelTime.divide(timeOfRepeatBD, 10, RoundingMode.HALF_UP);
                        modelDecodeTime = modelDecodeTime.divide(timeOfRepeatBD, 10, RoundingMode.HALF_UP);

                        BigDecimal numbersSizeBD = BigDecimal.valueOf(numbers.size());
                        BigDecimal model_ratio = modelCost.divide(numbersSizeBD.multiply(BigDecimal.valueOf(64)), 10, RoundingMode.HALF_UP);
                        BigDecimal modelTime_throughput = numbersSizeBD.multiply(BigDecimal.valueOf(8000L)).divide(modelTime, 10, RoundingMode.HALF_UP);

                        // 写入数据，只包含7列
                        writer.write(entry.toString() + ",");
                        writer.write("BP-RL-Improved-Complete,");
                        writer.write(modelTime_throughput.toPlainString() + ",");
                        writer.write(String.valueOf(numbers.size()) + ",");
                        writer.write(modelCost.toPlainString() + ",");
                        writer.write(String.valueOf(pack_size) + ",");
                        writer.write(model_ratio.toPlainString() + "\n");
                    }
                } catch (IOException e) {
                    System.err.println("Error writing output file for " + fname);
                }
            }
        } catch (IOException e) {
            System.err.println("Error iterating directory: " + directory);
        }
    }

    @Test
    public void TestVariableChunkSize() throws IOException {
        System.out.println("\nPerformance Testing with Variable Chunk Sizes (BP-RL-Improved)...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_BPRL_vary_m";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);

        // 定义要测试的chunk sizes (m*8 where m is 16, 32, 64, 128, 256, 512, 1024)
        int[] chunkSizes = {16*8, 32*8, 64*8, 128*8, 256*8, 512*8, 1024*8};

        // 尝试加载训练好的模型，如果没有则使用随机初始化的模型
        ImprovedRLDecisionModel model = new ImprovedRLDecisionModel();
        try {
            model.loadModel("improved_rl_model.bin");
            System.out.println("Loaded trained model from improved_rl_model.bin");
        } catch (IOException e) {
            System.out.println("No pre-trained model found. Using randomly initialized model.");
        }

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;

            System.out.println("Processing " + file.getName() + " with variable chunk sizes...");
            String Output = outputDirstr + "/" + file.getName();

            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(Output))) {
                // 表头
                String[] head = {
                        "m",
                        "Input Direction",
                        "Encoding Algorithm",
                        "Encoding Time",
                        "Decoding Time",
                        "Points",
                        "Compressed Size",
                        "Compression Ratio",
                        "Pack Size Used",
                        "Average Pack Efficiency",
                        "Average Pack Count"
                };
                writer.write(String.join(",", head));
                writer.newLine();

                // 读取数据
                List<String> numbers = new ArrayList<>();
                List<Integer> decimalPlaces = new ArrayList<>();

                try (BufferedReader br = Files.newBufferedReader(Paths.get(file.getPath()))) {
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
                }

                if (numbers.isEmpty()) {
                    System.out.println("Warning: No data in file " + file.getName());
                    continue;
                }

                int time_of_repeat = 5; // 减少重复次数以加快测试速度
                int decimalMax = decimalPlaces.stream().max(Integer::compare).orElse(0);

                // 分批处理，每1024个元素一批进行scaling
                int batchSize = 1024;
                List<long[]> batches = new ArrayList<>();

                for (int i = 0; i < numbers.size(); i += batchSize) {
                    int end = Math.min(numbers.size(), i + batchSize);
                    List<String> batch = numbers.subList(i, end);
                    long[] scaledBatch = scaleNumbers(batch, decimalMax);
                    batches.add(scaledBatch);
                }

                // 计算总长度并拼接所有批次的结果
                int totalLength = batches.stream().mapToInt(arr -> arr.length).sum();
                long[] scaledLongs_all = new long[totalLength];

                int currentIndex = 0;
                for (long[] batch : batches) {
                    System.arraycopy(batch, 0, scaledLongs_all, currentIndex, batch.length);
                    currentIndex += batch.length;
                }

                // 测试每个chunk size
                for (int chunkSize : chunkSizes) {
                    System.out.println("Testing chunk size: " + chunkSize);

                    // 固定pack size为8
                    int pack_size = 8;

                    BigDecimal modelCost = BigDecimal.ZERO;
                    BigDecimal modelTime = BigDecimal.ZERO;
                    BigDecimal modelDecodeTime = BigDecimal.ZERO;
                    int totalPacksUsed = 0;
                    float totalEfficiency = 0.0f;
                    int chunkProcessed = 0;

                    for (int j = 0; j < time_of_repeat; j++) {
                        BigDecimal totalCost = BigDecimal.ZERO;

                        for (int i = 0; i < scaledLongs_all.length; i += chunkSize) {
                            int end = Math.min(i + chunkSize, scaledLongs_all.length);
                            long[] chunkData = new long[end - i];
                            System.arraycopy(scaledLongs_all, i, chunkData, 0, end - i);

                            if (chunkData.length < 8) continue;

                            long startTime = System.nanoTime();

                            // 处理数据，确保长度是pack_size的倍数
                            int remainder = chunkData.length % pack_size;
                            int padding = (remainder == 0) ? 0 : pack_size - remainder;
                            long[] padded = new long[chunkData.length + padding];
                            System.arraycopy(chunkData, 0, padded, 0, chunkData.length);
                            if (padding > 0) Arrays.fill(padded, chunkData.length, padded.length, 0L);

                            // 计算每个组的位宽
                            int groups = padded.length / pack_size;
                            int[] bitWidths = new int[groups];
                            int gidx = 0;
                            for (int si = 0; si < padded.length; si += pack_size) {
                                long maxInGroup = 0;
                                for (int sj = si; sj < si + pack_size; ++sj) {
                                    long v = padded[sj];
                                    if (v > maxInGroup) maxInGroup = v;
                                }
                                int bitWidth = 0;
                                if (maxInGroup > 0) {
                                    bitWidth = 64 - Long.numberOfLeadingZeros(maxInGroup);
                                } else {
                                    bitWidth = 0;
                                }
                                bitWidths[gidx++] = bitWidth;
                            }

                            List<Integer> bitWidthsList = new ArrayList<>(groups);
                            for (int x = 0; x < groups; ++x) bitWidthsList.add(bitWidths[x]);

                            // 使用改进的RL模型进行打包决策
                            ImprovedExploration testExploration = new ImprovedExploration(0.05f, 1.0f, 0.05f, 0.0f);
                            model.setTrainingMode(false);
                            PackingResult res = packOctadsImproved(bitWidthsList, model, null, pack_size,
                                    padded, chunkData.length, testExploration);

                            long duration = System.nanoTime() - startTime;
                            modelTime = modelTime.add(BigDecimal.valueOf(duration));
                            modelCost = modelCost.add(BigDecimal.valueOf(res.compressedData.length * 8L));

                            // 统计信息
                            totalPacksUsed += res.packCount;
                            totalEfficiency += res.averageEfficiency(pack_size);
                            chunkProcessed++;

                            // 解码测试
                            if (res.compressedData != null) {
                                long startDecodeTime = System.nanoTime();
                                long[] decompressed = fastDecompress(res.compressedData, bitWidths, pack_size, chunkData.length);
                                long decodeDuration = System.nanoTime() - startDecodeTime;
                                modelDecodeTime = modelDecodeTime.add(BigDecimal.valueOf(decodeDuration));
                            }
                        }
                    }

                    // 计算平均值
                    BigDecimal timeOfRepeatBD = BigDecimal.valueOf(time_of_repeat);
                    modelCost = modelCost.divide(timeOfRepeatBD, 10, RoundingMode.HALF_UP);
                    modelTime = modelTime.divide(timeOfRepeatBD, 10, RoundingMode.HALF_UP);
                    modelDecodeTime = modelDecodeTime.divide(timeOfRepeatBD, 10, RoundingMode.HALF_UP);

                    float avgPackCount = chunkProcessed > 0 ? (float) totalPacksUsed / chunkProcessed : 0;
                    float avgEfficiency = chunkProcessed > 0 ? totalEfficiency / chunkProcessed : 0;

                    // 计算压缩比
                    BigDecimal numbersSizeBD = BigDecimal.valueOf(scaledLongs_all.length);
                    BigDecimal totalBits = numbersSizeBD.multiply(BigDecimal.valueOf(64)); // 原始数据每个值64位
                    BigDecimal modelRatio = modelCost.divide(totalBits, 10, RoundingMode.HALF_UP);

                    // 计算编码吞吐量（points/ms）
                    BigDecimal modelTimeThroughput = BigDecimal.ZERO;
                    if (modelTime.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal modelTimeMs = modelTime.divide(BigDecimal.valueOf(1000000), 10, RoundingMode.HALF_UP);
                        modelTimeThroughput = numbersSizeBD.divide(modelTimeMs, 10, RoundingMode.HALF_UP);
                    }

                    // 计算解码吞吐量（points/ms）
                    BigDecimal decodeThroughput = BigDecimal.ZERO;
                    if (modelDecodeTime.compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal modelDecodeTimeMs = modelDecodeTime.divide(BigDecimal.valueOf(1000000), 10, RoundingMode.HALF_UP);
                        decodeThroughput = numbersSizeBD.divide(modelDecodeTimeMs, 10, RoundingMode.HALF_UP);
                    }

                    // 写入结果
                    String[] record = {
                            String.valueOf(chunkSize),
                            file.toString(),
                            "BP-RL-Improved-Complete",
                            modelTimeThroughput.toPlainString(),
                            decodeThroughput.toPlainString(),
                            String.valueOf(scaledLongs_all.length),
                            modelCost.toPlainString(),
                            modelRatio.toPlainString(),
                            String.valueOf(pack_size),
                            String.format("%.6f", avgEfficiency),
                            String.format("%.2f", avgPackCount)
                    };
                    writer.write(String.join(",", record));
                    writer.newLine();
                }
            } catch (IOException e) {
                System.err.println("Error writing output file for " + file.getName());
            }
        }

        System.out.println("Variable chunk size testing completed.");
    }

    // ========== 简化的贪婪算法（用于兼容性） ==========
    static PackingResult greedyPackOctads(List<Integer> bitWidths) {
        return greedyImprovedPackOctads(bitWidths, 1);
    }
}