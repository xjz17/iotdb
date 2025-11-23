package org.apache.iotdb.tsfile.encoding;
// EfficientOctadPackingMLP_optimized.java
// Java 17+

import org.junit.Test;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.*;

public class EfficientOctadPackingMLPSprintz {

    static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data", "test.csv","POI-lat.csv","POI-lon.csv","Basel-wind.csv","Basel-temp.csv","Air-sensor.csv");
    static final int CHUNK_SIZE = 1024;
    static final int INPUT_DIM = 5;
    static final int HIDDEN_DIM = 48;

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

        float forwardProb(float[] feat, float[] outHidden, float[] outZ1) {
            if (outHidden != null) Arrays.fill(outHidden, 0.0f);
            if (outZ1 != null) Arrays.fill(outZ1, 0.0f);

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

            float z2 = b2;
            if (outHidden != null) {
                for (int h = 0; h < HIDDEN_DIM; ++h) z2 += W2[h] * outHidden[h];
            } else {
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

                float pClipped = Math.min(Math.max(p, 1e-6f), 1.0f - 1e-6f);

                float piA = dp.action ? pClipped : (1.0f - pClipped);
                if (piA <= 0.0f) {
                    piA = 1e-6f;
                }
                float lossI = -reward * (float) Math.log(piA);

                if (Float.isNaN(lossI) || Float.isInfinite(lossI)) {
                    System.err.printf("Warning: loss is NaN or Infinite. p=%.8f, piA=%.8f, reward=%.8f\n", p, piA, reward);
                    lossI = 0.0f;
                }

                totalLoss += lossI;

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

    // ========== Bitpacking utility methods (legacy 8-values helpers kept) ==========
    public static int getBitWidth(int num) {
        if (num == 0)
            return 1;
        else
            return 32 - Integer.numberOfLeadingZeros(num);
    }

    public static void pack8Values(ArrayList<Integer> values, int offset, int width, int encode_pos,
                                   byte[] encoded_result) {
        int bufIdx = 0;
        int valueIdx = offset;
        int leftBit = 0;

        while (valueIdx < 8 + offset) {
            int buffer = 0;
            int leftSize = 32;

            if (leftBit > 0) {
                buffer |= (values.get(valueIdx) << (32 - leftBit));
                leftSize -= leftBit;
                leftBit = 0;
                valueIdx++;
            }

            while (leftSize >= width && valueIdx < 8 + offset) {
                buffer |= (values.get(valueIdx) << (leftSize - width));
                leftSize -= width;
                valueIdx++;
            }
            if (leftSize > 0 && valueIdx < 8 + offset) {
                buffer |= (values.get(valueIdx) >>> (width - leftSize));
                leftBit = width - leftSize;
            }

            for (int j = 0; j < 4; j++) {
                encoded_result[encode_pos] = (byte) ((buffer >>> ((3 - j) * 8)) & 0xFF);
                encode_pos++;
                bufIdx++;
                if (bufIdx >= width) {
                    return;
                }
            }
        }
    }

    public static void unpack8Values(byte[] encoded, int offset, int width, ArrayList<Integer> result_list) {
        int byteIdx = offset;
        long buffer = 0;
        int totalBits = 0;
        int valueIdx = 0;

        while (valueIdx < 8) {
            while (totalBits < width) {
                buffer = (buffer << 8) | (encoded[byteIdx] & 0xFF);
                byteIdx++;
                totalBits += 8;
            }

            while (totalBits >= width && valueIdx < 8) {
                result_list.add((int) (buffer >>> (totalBits - width)));
                valueIdx++;
                totalBits -= width;
                buffer = buffer & ((1L << totalBits) - 1);
            }
        }
    }

    public static int bitPacking(ArrayList<Integer> numbers, int start, int bit_width, int encode_pos,
                                 byte[] encoded_result) {
        int block_num = (numbers.size() - start) / 8;
        for (int i = 0; i < block_num; i++) {
            pack8Values(numbers, start + i * 8, bit_width, encode_pos, encoded_result);
            encode_pos += bit_width;
        }
        return encode_pos;
    }

    public static ArrayList<Integer> decodeBitPacking(
            byte[] encoded, int decode_pos, int bit_width, int block_size) {
        ArrayList<Integer> result_list = new ArrayList<>();
        int block_num = (block_size - 1) / 8;

        for (int i = 0; i < block_num; i++) { // bitpacking
            unpack8Values(encoded, decode_pos, bit_width, result_list);
            decode_pos += bit_width;
        }
        return result_list;
    }

    // ========== 64-bit-capable canonical encoder/decoder (fast, primitive-based) ==========
    // DecodedResult wraps decoded padded values and originalLength (so caller can trim).
    public static class DecodedResult {
        public final long[] values; // padded values: totalGroups * packSize
        public final int originalLength; // original (unpadded) length
        public final int packSize;
        public DecodedResult(long[] values, int originalLength, int packSize) {
            this.values = values;
            this.originalLength = originalLength;
            this.packSize = packSize;
        }
    }

    /**
     * Fast primitive-based encoder (no BigInteger). Preserves MSB-first ordering per value.
     * Header layout:
     *   int magic (0x4250524C)
     *   int originalLength
     *   int pack_size
     *   int totalGroups
     *   totalGroups bytes: bitWidth (0..64)
     * followed by bitstream (MSB-first per value)
     */
    public static byte[] encodeBitPackingCanonical64_fast(long[] paddedArray, int[] bitWidths, int pack_size, int originalLength) throws IOException {
        if (bitWidths == null) throw new IllegalArgumentException("bitWidths null");
        int totalGroups = bitWidths.length;
        if (paddedArray.length < totalGroups * pack_size) throw new IllegalArgumentException("paddedArray too small");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

//        dos.writeInt(0x4250524C); // "BPRL"
//        dos.writeInt(originalLength);
//        dos.writeInt(pack_size);
//        dos.writeInt(totalGroups);

        // write bitWidths (1 byte each)
        for (int bw : bitWidths) {
            if (bw < 0 || bw > 64) throw new IOException("Unsupported bitWidth (must be 0..64): " + bw);
            dos.writeByte(bw);
        }
        dos.flush();

        // Bitstream build using a primitive BitWriter
        BitWriter bwriter = new BitWriter();

        int dataIndex = 0;
        for (int g = 0; g < totalGroups; ++g) {
            int bw = bitWidths[g];
            for (int k = 0; k < pack_size; ++k) {
                long rawVal = paddedArray[dataIndex++];
//                if (bw == 0) {
//                    // nothing to append
//                } else if (bw == 64) {
//                    // write 64 bits MSB-first by splitting into two 32-bit writes (safe)
//                    bwriter.writeBits((rawVal >>> 32) & 0xFFFFFFFFL, 32);
//                    bwriter.writeBits(rawVal & 0xFFFFFFFFL, 32);
//                } else {
                long mask = (bw == 64) ? ~0L : ((1L << bw) - 1L);
                long masked = rawVal & mask;
                bwriter.writeBits(masked, bw);
//                }
            }
        }

        byte[] bitBytes = bwriter.finish();

        dos.write(bitBytes);
        dos.flush();
        return baos.toByteArray();
    }

    private static class BitWriter {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private long acc = 0L; // holds currently buffered bits (lowest "accBits" bits are valid)
        private int accBits = 0; // number of bits in acc

        // writeBits expects bits in LSB-aligned form (i.e., "masked" value). We append MSB-first as: acc = (acc << bitCount) | bits
        void writeBits(long bits, int bitCount) {
            if (bitCount == 0) return;
            if (bitCount == 64) {
                // split into two 32-bit writes to avoid shifting by 64
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

    /**
     * Fast primitive-based decoder corresponding to encodeBitPackingCanonical64_fast
     */
    public static DecodedResult decodeBitPackingCanonical64_fast(byte[] encoded) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(encoded));
        int magic = dis.readInt();
        if (magic != 0x4250524C) throw new IOException("Bad magic");
        int originalLength = dis.readInt();
        int pack_size = dis.readInt();
        int totalGroups = dis.readInt();

        int[] bitWidths = new int[totalGroups];
        for (int i = 0; i < totalGroups; ++i) bitWidths[i] = dis.readUnsignedByte();

        // read remaining bytes as bitstream
        ByteArrayOutputStream rest = new ByteArrayOutputStream();
        int b;
        while ((b = dis.read()) != -1) rest.write(b);
        byte[] bitstream = rest.toByteArray();

        long[] result = new long[totalGroups * pack_size];
        int resIdx = 0;

        BitReader reader = new BitReader(bitstream);

        for (int g = 0; g < totalGroups; ++g) {
            int bw = bitWidths[g];
            for (int k = 0; k < pack_size; ++k) {
                if (bw == 0) {
                    result[resIdx++] = 0L;
                } else if (bw == 64) {
                    long high = reader.readBits(32);
                    long low = reader.readBits(32);
                    long v = (high << 32) | (low & 0xFFFFFFFFL);
                    result[resIdx++] = v;
                } else {
                    long v = reader.readBits(bw);
                    // sign-safe conversion: v is unsigned value fitting in bw bits; we store it as long
                    result[resIdx++] = v;
                }
            }
        }

        return new DecodedResult(result, originalLength, pack_size);
    }

    private static class BitReader {
        final byte[] data;
        private long acc = 0L;
        private int accBits = 0;
        private int idx = 0;

        BitReader(byte[] data) {
            this.data = data;
        }

        long readBits(int bitCount) throws IOException {
            if (bitCount == 0) return 0L;
            while (accBits < bitCount) {
                if (idx < data.length) {
                    acc = (acc << 8) | (data[idx++] & 0xFFL);
                    accBits += 8;
                } else {
                    // pad with zeros if stream ends prematurely
                    acc = (acc << (bitCount - accBits));
                    accBits = bitCount;
                }
            }
            int shift = accBits - bitCount;
            long mask = (bitCount == 64) ? ~0L : ((1L << bitCount) - 1L);
            long v = (acc >>> shift) & mask;
            if (shift > 0) {
                acc &= ((1L << shift) - 1L);
            } else {
                acc = 0L;
            }
            accBits = shift;
            return v;
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
    public static long[] sprintz(long[] numbers) {
        int size = numbers.length;
        long[] result = new long[size];

        if (size == 0) return result; // 空数组直接返回

        long first = numbers[0];
        result[0] = first;

        long prev = first;
        for (int i = 1; i < size; i++) {
            long current = numbers[i];
            long diff = current - prev;
            // ZigZag 编码: 正数 -> 偶数, 负数 -> 奇数
            result[i] = (diff << 1) ^ (diff >> 63);
            prev = current;
        }

        return result;
    }
    /**
     * Sprintz解码 - 从差分编码恢复原始数据 (long版本)
     */
    public static long[] sprintzDecode(long[] encodedData) {
        int size = encodedData.length;
        long[] result = new long[size];

        if (size == 0) return result;

        // 第一个元素是原始值
        result[0] = encodedData[0];

        // 后续元素需要ZigZag解码和累加
        long prev = result[0];
        for (int i = 1; i < size; i++) {
            long zigzagEncoded = encodedData[i];
            // ZigZag解码: (n >>> 1) ^ (-(n & 1))
            long diff = (zigzagEncoded >>> 1) ^ -(zigzagEncoded & 1);
            result[i] = prev + diff;
            prev = result[i];
        }

        return result;
    }

    public static long[] decompressData(byte[] compressedData, int originalLength, int packSize) {
        try {
            // 解码bit-packed数据
            DecodedResult decodedResult = decodeBitPackingCanonical64_fast(compressedData);

            // 提取解码后的数据（只取原始长度，去除填充）
            long[] decodedValues = Arrays.copyOf(decodedResult.values, originalLength);

            // Sprintz解码恢复原始数据
            return sprintzDecode(decodedValues);
        } catch (IOException e) {
            System.err.println("Decompression failed: " + e.getMessage());
            return new long[0];
        }
    }

    /**
     * 快速解压函数 - 直接从编码后的数据解压
     * 假设数据格式为: [original data after sprintz encoding]
     */
    public static long[] fastDecompress(byte[] compressedData, int[] bitWidths, int packSize, int originalLength) {
        try {
            // 这里需要根据实际的压缩格式来解析
            // 假设compressedData包含bit-packed数据
            ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
            DataInputStream dis = new DataInputStream(bais);

            // 读取bit-packed数据
            int totalGroups = bitWidths.length;
            long[] result = new long[totalGroups * packSize];
            int resultIndex = 0;

            BitReader reader = new BitReader(compressedData);

            for (int g = 0; g < totalGroups; ++g) {
                int bw = bitWidths[g];
                for (int k = 0; k < packSize; ++k) {
                    if (bw == 0) {
                        result[resultIndex++] = 0L;
                    } else if (bw == 64) {
                        long high = reader.readBits(32);
                        long low = reader.readBits(32);
                        long v = (high << 32) | (low & 0xFFFFFFFFL);
                        result[resultIndex++] = v;
                    } else {
                        long v = reader.readBits(bw);
                        result[resultIndex++] = v;
                    }
                }
            }

            // 只取原始长度的数据并Sprintz解码
            long[] trimmedResult = Arrays.copyOf(result, originalLength);
            return sprintzDecode(trimmedResult);

        } catch (IOException e) {
            System.err.println("Fast decompression failed: " + e.getMessage());
            return new long[0];
        }
    }
    // ========== packOctads (updated to accept originalLength for compression) ==========
    static PackingResult packOctads(List<Integer> bitWidths, RLDecisionModel model, List<DecisionPoint> decisionTrace, int pack_size, long[] dataArray, int originalLength) {
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
                feat[1] = currentPack.maxBitWidth / 64.0f;
                feat[2] = b / 64.0f;
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

        // 执行实际的bitpacking压缩（如果提供了 dataArray）
        if (dataArray != null) {
//            System.out.println(pack_size);
            result.compressedData = performBitPackingCompression64_fast(dataArray, result.packs, pack_size, originalLength);
        }

        return result;
    }

    // faster compression using primitive based encoder
    private static byte[] performBitPackingCompression64_fast(long[] dataArray, List<Pack> packs, int pack_size, int originalLength) {
        // 计算总的数据组数
        int totalGroups = 0;
        for (Pack pack : packs) {
            totalGroups += pack.size;
        }

        // 准备 bitWidths 数组和 paddedArray (long)
        int[] bitWidths = new int[totalGroups];
        long[] paddedArray = new long[totalGroups * pack_size];

        int groupIndex = 0;
        int dataIndex = 0;

        for (Pack pack : packs) {
            for (int i = 0; i < pack.size; i++) {
                int originalGroupIndex = pack.indices.get(i);
                bitWidths[groupIndex] = pack.bitWidths.get(i);

                int startPos = originalGroupIndex * pack_size;
                for (int j = 0; j < pack_size; j++) {
                    long val = 0L;
//                    if (startPos + j < dataArray.length) {
                    val = dataArray[startPos + j];
//                    } else {
//                        val = 0L;
//                    }
//                    if (val < 0) {
//                        // negative is unusual (scaleNumbers should produce >=0 after shifting); still, allow via masking semantics.
//                        System.err.println("Warning: value negative; treating as unsigned 64-bit representation. val=" + val + " groupIndex=" + groupIndex + " pos=" + j);
//                    }
                    paddedArray[dataIndex++] = val;
                }
                groupIndex++;
            }
        }
//        return null;

        try {
            return encodeBitPackingCanonical64_fast(paddedArray, bitWidths, pack_size, originalLength);
        } catch (IOException e) {
            System.err.println("Encoding failed: " + e.getMessage());
            return null;
        }
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
                // training does not perform actual compression, pass dataArray=null and originalLength=0
                PackingResult result = packOctads(bitWidths, model, decisionTrace, 8, null, 0);

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
    // ========== performanceTest (optimized internals) ==========
    static void performanceTestPack8(RLDecisionModel model, String directory, String outputDirStr) {
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
                    writer.write("Input Direction,Encoding Algorithm,Encoding Time,Decoding Time,Points,Compressed Size,Pack Size,Compression Ratio\n");

                    int time_of_repeat = 50;

                    for(int pack_size_exp = 3; pack_size_exp < 4; pack_size_exp++) {
                        int pack_size = (int) Math.pow(2, pack_size_exp);
                        long modelCost = 0;
                        long modelTime = 0;
                        long compressedSize = 0;
                        long modelDecodeTime = 0;

                        for (int rep = 0; rep < time_of_repeat; ++rep) {
                            for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                                int end = Math.min(numbers.size(), i + CHUNK_SIZE);
                                if (end - i <= 2) continue;
                                List<String> chunkNumbers = numbers.subList(i, end);
                                int decimalMax = 0;
                                for (int k = i; k < end; ++k) {
                                    if (decimalPlaces.get(k) > decimalMax) decimalMax = decimalPlaces.get(k);
                                }

                                long[] scaledInt = scaleNumbers(chunkNumbers, decimalMax);
                                long startTime = System.nanoTime();
                                long[] scaledInts = sprintz(scaledInt);

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

                                // pass original length (un-padded) so decoder can trim
                                List<Integer> bitWidthsList = new ArrayList<>(groups);
                                for (int x = 0; x < groups; ++x) bitWidthsList.add(bitWidths[x]);

                                PackingResult res = packOctads(bitWidthsList, model, null, pack_size, padded, scaledInts.length);
                                long duration = System.nanoTime() - startTime;


                                // 使用快速解压
                                if (res.compressedData != null) {
                                    long decodeStartTime = System.nanoTime();
                                    long[] decompressed = fastDecompress(res.compressedData, bitWidths, pack_size, scaledInts.length);
                                    long decodeDuration = System.nanoTime() - decodeStartTime;
                                    modelDecodeTime += decodeDuration;
//                                    System.out.println(decodeDuration);
                                }

                                modelTime += duration;
                                modelCost += res.totalCost;

                            }
                        }

                        modelCost /= time_of_repeat;
                        modelTime /= time_of_repeat;
                        modelDecodeTime /= time_of_repeat;

                        double model_ratio = (double) modelCost / (double) (numbers.size() * 64); // compressed / original bytes
                        double modelTime_throughput = (double) (numbers.size() * 8000) / (double) modelTime; // points/ms
                        double decodeThroughput = (double) (numbers.size() * 8000) / modelDecodeTime; // points per second
                        writer.write(entry.toString() + ",");
                        writer.write("SPRINTZ-RL,");
                        writer.write(String.valueOf(modelTime_throughput) + ",");
                        writer.write(String.valueOf(decodeThroughput) + ",");                        writer.write(String.valueOf(numbers.size()) + ",");
                        writer.write(String.valueOf(modelCost) + ",");
                        writer.write(String.valueOf(pack_size) + ",");
                        writer.write(String.valueOf(model_ratio) + "\n");
                    }
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
        String outDir = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_sprintz_rl";// args.length > 2 ? args[2] : "./output_BPRL";

        int epochs = 80;

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
            performanceTestPack8(model, dataDir, outDir);
        } else {
            System.err.println("No data directory provided for performanceTest. Exiting.");
        }
    }


    // ========== performanceTest (optimized internals) ==========
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
                    writer.write("Input Direction,Encoding Algorithm,Encoding Time,Points,Compressed Size,Pack Size,Compression Ratio\n");

                    int time_of_repeat = 100;

                    for(int pack_size_exp = 3; pack_size_exp < 9; pack_size_exp++) {
                        int pack_size = (int) Math.pow(2, pack_size_exp);
                        long modelCost = 0;
                        long modelTime = 0;
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

                                // pass original length (un-padded) so decoder can trim
                                List<Integer> bitWidthsList = new ArrayList<>(groups);
                                for (int x = 0; x < groups; ++x) bitWidthsList.add(bitWidths[x]);

                                PackingResult res = packOctads(bitWidthsList, model, null, pack_size, padded, scaledInts.length);
                                long duration = System.nanoTime() - startTime;
                                modelTime += duration;
                                modelCost += res.totalCost;

                                if (rep == 0) {
                                    compressedSize += (res.compressedData != null) ? res.compressedData.length : 0;
                                }
                            }
                        }

                        modelCost /= time_of_repeat;
                        modelTime /= time_of_repeat;
                        double model_ratio = (double) modelCost / (double) (numbers.size() * 64); // compressed / original bytes
                        double modelTime_throughput = (double) (numbers.size() * 1000) / (double) modelTime; // points/ms

                        writer.write(entry.toString() + ",");
                        writer.write("BP-RL,");
                        writer.write(String.valueOf(modelTime_throughput) + ",");
                        writer.write(String.valueOf(numbers.size()) + ",");
                        writer.write(String.valueOf(modelCost) + ",");
                        writer.write(String.valueOf(pack_size) + ",");
                        writer.write(String.valueOf(model_ratio) + "\n");
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
    public void TestVarPackSize() {
        String trainCsv = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/processed_data.csv";// args.length > 0 ? args[0] : "";
        String dataDir = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";//args.length > 1 ? args[1] : "";
        String outDir = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_sprintz_rl_vary_pack_size";// args.length > 2 ? args[2] : "./output_BPRL";

        int epochs = 80;

        RLDecisionModel model = new RLDecisionModel();
        model = trainModel(epochs, trainCsv);
        performanceTest(model, dataDir, outDir);
    }
}
