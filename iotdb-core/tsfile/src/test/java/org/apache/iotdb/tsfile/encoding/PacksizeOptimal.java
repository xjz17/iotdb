package org.apache.iotdb.tsfile.encoding;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.apache.commons.lang3.ObjectUtils.min;

public class PacksizeOptimal {

    static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data", "test.csv", "POI-lat.csv",
            "POI-lon.csv", "Basel-wind.csv", "Basel-temp.csv", "Air-sensor.csv");
    private static final int CHUNK_SIZE = 1024;

    public static int getCount(long long1, int mask) {
        return ((int) (long1 & mask));
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
        int block_num = Math.min((numbers.size() - start) / 8, 1);
        for (int i = 0; i < block_num; i++) {
            pack8Values(numbers, start + i * 8, bit_width, encode_pos, encoded_result);
            encode_pos += bit_width;
        }
        return encode_pos;
    }

    /**
     * 解码函数 - 支持可变大小的数据块
     */
    public static int[] decodeBitPacking(byte[] compressedData, int[] bitWidths, int pack_size, int originalLength) {
        int[] result = new int[originalLength];
        int resultIndex = 0;

        // 1. 从压缩数据中解析 bitWidths
        int totalGroups = bitWidths.length;
        int bitWidthBits = totalGroups * 6;
        int bitWidthBytes = (bitWidthBits + 7) / 8; // 向上取整到字节

        int[] decodedBitWidths = new int[totalGroups];

        // 从压缩数据中读取 bitWidths
        int bitPos = 0;
        for (int group = 0; group < totalGroups; group++) {
            int bitWidth = 0;

            // 读取 6 位
            for (int bit = 0; bit < 6; bit++) {
                int byteIndex = bitPos / 8;
                int bitOffset = 7 - (bitPos % 8); // 高位在前

                int bitValue = (compressedData[byteIndex] >> bitOffset) & 1;
                bitWidth = (bitWidth << 1) | bitValue;

                bitPos++;
            }

            decodedBitWidths[group] = bitWidth;
        }

        // 2. 解码数据部分
        int decodePos = bitWidthBytes;

        for (int group = 0; group < totalGroups && resultIndex < originalLength; group++) {
            int bitWidth = decodedBitWidths[group];

            // 计算这个数据块中需要解码的值数量
            int valuesInGroup = Math.min(pack_size, originalLength - resultIndex);

            // 对于每个数据块，按照8个值一组进行解码
            for (int blockStart = 0; blockStart < valuesInGroup; blockStart += 8) {
                // 当前块中实际需要解码的值数量（最多8个）
                int valuesInBlock = Math.min(8, valuesInGroup - blockStart);

                // 解码8个值
                ArrayList<Integer> blockData = new ArrayList<>(8);
                unpack8Values(compressedData, decodePos, bitWidth, blockData);

                // 将解码的值复制到结果数组中
                for (int i = 0; i < valuesInBlock && resultIndex < originalLength; i++) {
                    result[resultIndex++] = blockData.get(i);
                }

                // 移动到下一个数据块
                decodePos += bitWidth;
            }
        }

        // 3. 将解码出的 bitWidths 复制回传入的数组（如果需要）
        System.arraycopy(decodedBitWidths, 0, bitWidths, 0, totalGroups);

        return result;
    }


    // 结合RMQ
    public static int findOptimalPackSizeallV3(int[] values) {
        int n = values.length;
        if (n < 8) return n;

        // 计算位宽数组
        int[] bitWidths = new int[n];
        int globalMax = 0;
        for (int i = 0; i < n; i++) {
            int value = values[i];
            if (value > globalMax) {
                globalMax = value;
            }
            bitWidths[i] = 64 - Long.numberOfLeadingZeros(Math.max(1, value));
        }

        int bitWidthGlobal = 64 - Long.numberOfLeadingZeros(Math.max(1, globalMax));
        int z = (int) Math.ceil(Math.log(bitWidthGlobal + 1) / Math.log(2));

        // 构建稀疏表（RMQ）
        int logN = 32 - Integer.numberOfLeadingZeros(n); // log2(n)
        int[][] st = new int[logN][n];

        // 初始化第一层
        System.arraycopy(bitWidths, 0, st[0], 0, n);

        // 构建稀疏表
        for (int k = 1; k < logN; k++) {
            int step = 1 << (k - 1);
            for (int i = 0; i + (1 << k) <= n; i++) {
                st[k][i] = Math.max(st[k - 1][i], st[k - 1][i + step]);
            }
        }

        // 预计算log2表
        int[] log2 = new int[n + 1];
        for (int i = 2; i <= n; i++) {
            log2[i] = log2[i / 2] + 1;
        }

        // 枚举所有可能的pack_size
        int bestPackSize = 1;
        long bestCost = Long.MAX_VALUE;
        int maxPackSize = n;

        for (int p = 1; p <= maxPackSize; p++) {
            int m = (n + p - 1) / p; // ceil(n/p)
            long cost = 0;

            // 计算前m-1个pack的成本
            for (int i = 0; i < m - 1; i++) {
                int start = i * p;
                int end = start + p - 1;

                // 使用RMQ查询区间最大值
                int k = log2[p];
                int maxBitWidth = Math.max(st[k][start], st[k][end - (1 << k) + 1]);

                cost += (long) p * maxBitWidth;
            }

            // 计算最后一个pack的成本
            if (m > 0) {
                int lastStart = (m - 1) * p;
                int lastEnd = n - 1;
                int r = n - lastStart;

                if (r > 0) {
                    int k = log2[r];
                    int lastMaxBitWidth = Math.max(st[k][lastStart],
                            st[k][lastEnd - (1 << k) + 1]);
                    cost += (long) r * lastMaxBitWidth;
                }
            }

            // 加上位宽信息的存储成本
            cost += (long) m * z;

            if (cost < bestCost) {
                bestCost = cost;
                bestPackSize = p;
            }
        }

//        if (bestPackSize == 1)
//            System.out.println(bestCost);

        return bestPackSize;
    }
    /**
     * 使用RMQ（稀疏表）快速计算区间最大值，找到最优的pack_size
     *
     * @param values 数值数组（已缩放和差分处理）
     * @return 最优的pack_size（8的倍数）
     */
    public static int findOptimalPackSize(int[] values) {
        int n = values.length;
        if (n < 8) return 8;

        // 计算全局最大位宽和z值
        int globalMax = 0;
        for (int value : values) {
            if (value > globalMax) {
                globalMax = value;
            }
        }
        int bitWidthGlobal = 64 - Long.numberOfLeadingZeros(globalMax);
        int z = (int) Math.ceil(Math.log(bitWidthGlobal + 1) / Math.log(2));

        // 预计算所有值的位宽
        int[] bitWidths = new int[n];
        for (int i = 0; i < n; i++) {
            bitWidths[i] = 64 - Long.numberOfLeadingZeros(Math.max(1, values[i]));
        }

        // 构建稀疏表用于快速区间最大值查询
        int k = (int)(Math.log(n) / Math.log(2)) + 1;
        int[][] st = new int[k][n];

        // 初始化第一层
        for (int i = 0; i < n; i++) {
            st[0][i] = bitWidths[i];
        }

        // 构建稀疏表
        for (int j = 1; j < k; j++) {
            for (int i = 0; i + (1 << j) <= n; i++) {
                st[j][i] = Math.max(st[j-1][i], st[j-1][i + (1 << (j-1))]);
            }
        }

        // 区间最大值查询函数
        java.util.function.BiFunction<Integer, Integer, Integer> queryMax = (l, r) -> {
            int len = r - l + 1;
            int j = (int)(Math.log(len) / Math.log(2));
            return Math.max(st[j][l], st[j][r - (1 << j) + 1]);
        };

        // 枚举所有可能的pack_size（8的倍数）
        int bestPackSize = 8;
        long bestCost = Long.MAX_VALUE;

        // 限制最大pack_size，避免性能问题
        int maxPackSize = Math.min(CHUNK_SIZE, n);
        maxPackSize = (maxPackSize / 8) * 8; // 调整为8的倍数

        for (int p = 8; p <= maxPackSize; p += 8) {
            int m = (n + p - 1) / p; // ceil(n/p)
            int r = n - (m - 1) * p; // 最后一个pack的大小

            long cost = 0;

            // 计算前m-1个pack的成本
            for (int i = 0; i < m - 1; i++) {
                int start = i * p;
                int end = start + p - 1;
                int maxBitWidth = queryMax.apply(start, end);
                cost += p * maxBitWidth;
            }

            // 计算最后一个pack的成本
            if (m > 0) {
                int lastStart = (m - 1) * p;
                int lastEnd = n - 1;
                int lastMaxBitWidth = queryMax.apply(lastStart, lastEnd);
                cost += r * lastMaxBitWidth;
            }

            // 加上位宽信息的存储成本
            cost += m * z;

            if (cost < bestCost) {
                bestCost = cost;
                bestPackSize = p;
            }
        }

        return bestPackSize;
    }
    /**
     * 暴力计算最优pack_size - O(n^2)复杂度，但能确保找到最优解
     * 对于每个可能的pack_size（8的倍数），都重新计算所有分组的成本
     *
     * @param values 数值数组（已缩放和差分处理）
     * @return 最优的pack_size（8的倍数）
     */
    public static int findOptimalPackSizeall(int[] values) {
        int n = values.length;
        if (n < 8) return 8;

        // 计算全局最大位宽和z值
        int globalMax = 0;
        for (int value : values) {
            if (value > globalMax) {
                globalMax = value;
            }
        }
        int bitWidthGlobal = 64 - Long.numberOfLeadingZeros(Math.max(1, globalMax));
        int z = (int) Math.ceil(Math.log(bitWidthGlobal + 1) / Math.log(2));

        // 枚举所有可能的pack_size（8的倍数）
        int bestPackSize = 8;
        long bestCost = Long.MAX_VALUE;

        // 限制最大pack_size，避免性能问题
        int maxPackSize = Math.min(256, n);
        maxPackSize = (maxPackSize / 8) * 8; // 调整为8的倍数

        // 对于每个可能的pack_size
        for (int p = 8; p <= maxPackSize; p += 8) {
            int m = (n + p - 1) / p; // ceil(n/p)
            int r = n - (m - 1) * p; // 最后一个pack的大小

            long cost = 0;

            // 计算前m-1个pack的成本
            for (int i = 0; i < m - 1; i++) {
                int start = i * p;
                int end = start + p - 1;

                // 遍历当前pack的所有值，找出最大位宽
                int maxBitWidth = 0;
                for (int j = start; j <= end; j++) {
                    int bitWidth = 64 - Long.numberOfLeadingZeros(Math.max(1, values[j]));
                    if (bitWidth > maxBitWidth) {
                        maxBitWidth = bitWidth;
                    }
                }

                cost += p * maxBitWidth;
            }

            // 计算最后一个pack的成本
            if (m > 0) {
                int lastStart = (m - 1) * p;
                int lastEnd = n - 1;

                // 遍历最后一个pack的所有值，找出最大位宽
                int lastMaxBitWidth = 0;
                for (int j = lastStart; j <= lastEnd; j++) {
                    int bitWidth = 64 - Long.numberOfLeadingZeros(Math.max(1, values[j]));
                    if (bitWidth > lastMaxBitWidth) {
                        lastMaxBitWidth = bitWidth;
                    }
                }

                cost += r * lastMaxBitWidth;
            }

            // 加上位宽信息的存储成本
            cost += m * z;

            if (cost < bestCost) {
                bestCost = cost;
                bestPackSize = p;
            }
        }

        return bestPackSize;
    }

    private static int[] scaleNumbers(List<String> numbers, int decimalMax) {
        BigDecimal scale = BigDecimal.TEN.pow(decimalMax);
        int size = numbers.size();
        int[] result = new int[size];

        if (size == 0) {
            return result;
        }

        BigDecimal min = null;
        BigDecimal[] scaledValues = new BigDecimal[size];

        for (int i = 0; i < size; i++) {
            BigDecimal val = new BigDecimal(numbers.get(i)).multiply(scale);
            scaledValues[i] = val;
            if (min == null || val.compareTo(min) < 0) {
                min = val;
            }
        }

        BigDecimal first = scaledValues[0].subtract(min);
        result[0] = first.toBigInteger().intValue();

        for (int i = 1; i < size; i++) {
            BigDecimal current = scaledValues[i].subtract(min);
            result[i] = current.toBigInteger().intValue();
        }

        return result;
    }

    public static byte[] encodeBitPacking(int[] paddedArray, int[] bitWidths, int pack_size) {
        int totalGroups = bitWidths.length;

        // 计算位宽部分所需的字节数
        int bitWidthBits = totalGroups * 6;
        int bitWidthBytes = (bitWidthBits + 7) / 8; // 向上取整到字节

        // 计算数据部分所需的字节数
        int dataBytes = 0;
        for (int group = 0; group < totalGroups; group++) {
            int bitWidth = bitWidths[group];
            int blocksInGroup = (pack_size + 7) / 8; // 向上取整
            dataBytes += bitWidth * blocksInGroup;
        }

        int totalBytes = bitWidthBytes + dataBytes;
        byte[] encodedResult = new byte[totalBytes];

        // 1. 编码 bitWidths 信息（每个用 6 位）
        int bitPos = 0;
        for (int group = 0; group < totalGroups; group++) {
            int bitWidth = bitWidths[group] & 0x3F; // 确保只用低 6 位

            // 将 6 位写入字节数组
            for (int bit = 5; bit >= 0; bit--) {
                int bitValue = (bitWidth >> bit) & 1;
                int byteIndex = bitPos / 8;
                int bitOffset = 7 - (bitPos % 8); // 高位在前

                if (bitValue == 1) {
                    encodedResult[byteIndex] |= (1 << bitOffset);
                }

                bitPos++;
            }
        }

        // 2. 编码数据部分
        int encodePos = bitWidthBytes;

        for (int group = 0; group < totalGroups; group++) {
            int startIndex = group * pack_size;
            int bitWidth = bitWidths[group];

            // 将这个数据块分成多个 8 个值的小块进行编码
            for (int blockStart = 0; blockStart < pack_size; blockStart += 8) {
                int actualBlockSize = Math.min(8, paddedArray.length - (startIndex + blockStart));

                // 收集当前块的值
                ArrayList<Integer> blockData = new ArrayList<>(8);
                for (int i = 0; i < 8; i++) {
                    int idx = startIndex + blockStart + i;
                    if (idx < paddedArray.length) {
                        blockData.add(paddedArray[idx]);
                    } else {
                        blockData.add(0); // 用 0 填充不足的部分
                    }
                }

                // 编码这个 8 个值的块
                encodePos = bitPacking(blockData, 0, bitWidth, encodePos, encodedResult);
            }
        }

        // 裁剪到实际大小
        byte[] finalResult = new byte[encodePos];
        System.arraycopy(encodedResult, 0, finalResult, 0, encodePos);

        return finalResult;
    }


    public static void main(String[] args) throws IOException {
        System.out.println("\nPerformance Testing...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/output_BP_RMQ";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);
        for (File file : Objects.requireNonNull(dir.listFiles())) {

            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;
            System.out.println(file.getName());
            String Output = outputDirstr + "/" + file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            // 更新表头，增加解压吞吐率列
            String[] head = {
                    "Input Direction",
                    "Encoding Algorithm",
                    "Encoding Time",
                    "Decoding Time",
                    "Points",
                    "Compressed Size",
                    "Compression Ratio"
            };
            writer.writeRecord(head); // write header to output file
            System.out.println("Processing " + file.getName() + "...");
            List<String> numbers = new ArrayList<>();
            List<Integer> decimalPlaces = new ArrayList<>();
            CsvReader csvReader = new CsvReader(file.getPath(), ',', StandardCharsets.UTF_8);
            while (csvReader.readRecord()) {
                for (String value : csvReader.getValues()) {
                    String numStr = value.trim();
                    if (!numStr.isEmpty()) {
                        numbers.add(numStr);
                        int decimal = 0, sigBits;
                        if (numStr.contains(".")) {
                            String[] parts = numStr.split("\\.");
                            decimal = parts[1].length();
                            sigBits = (int) ((parts[0].length() + decimal) * (Math.log(10) / Math.log(2)));
                        } else {
                            sigBits = (int) (numStr.length() * (Math.log(10) / Math.log(2)));
                        }
                        decimalPlaces.add(decimal);
                    }
                }
            }
            int time_of_repeat = 1;

            int decimalMax = decimalPlaces.stream().max(Integer::compare).orElse(0);

            // 分批处理，每1024个元素一批
            int batchSize = 1024;
            List<int[]> batches = new ArrayList<>();

            for (int i = 0; i < numbers.size(); i += batchSize) {
                int end = Math.min(numbers.size(), i + batchSize);
                List<String> batch = numbers.subList(i, end);
                int[] scaledBatch = scaleNumbers(batch, decimalMax);
                batches.add(scaledBatch);
            }

            // 计算总长度并拼接所有批次的结果
            int totalLength = batches.stream().mapToInt(arr -> arr.length).sum();
            int[] scaledInts_all = new int[totalLength];

            int currentIndex = 0;
            for (int[] batch : batches) {
                System.arraycopy(batch, 0, scaledInts_all, currentIndex, batch.length);
                currentIndex += batch.length;
            }
            long modelCost = 0;
            long modelTime = 0;
            long modelDecodeTime = 0; // 新增：解压时间

            for (int j = 0; j < time_of_repeat; j++) {
                int totalCost = 0;
                for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                    int end = Math.min(i + CHUNK_SIZE, numbers.size());
                    int[] scaledInts = new int[end - i];
                    if (end - i >= 0) System.arraycopy(scaledInts_all, i, scaledInts, 0, end - i);

                    long startTime = System.nanoTime();
                    // 使用优化的方法找到最优pack_size
                    int pack_size = findOptimalPackSizeall(scaledInts);
                    // 确保pack_size是8的倍数且至少为8
                    pack_size = Math.max(8, pack_size);


                    int remainder = scaledInts.length % 8;
                    int paddingLength = (remainder == 0) ? 0 : 8 - remainder;

                    // 创建新数组，长度补齐为8的倍数
                    int[] paddedArray = new int[scaledInts.length + paddingLength];
                    System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                    int actual_length = paddedArray.length;
                    int num_of_pack_size = (actual_length + pack_size - 1) / pack_size;
                    int[] bitWidths = new int[num_of_pack_size];

                    for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += pack_size) {
                        int maxInGroup = 0;
                        int end_index = Math.min(scaledInts_i + pack_size, actual_length);
                        for (int scaledInts_j = scaledInts_i; scaledInts_j < end_index; scaledInts_j++) {
                            if (paddedArray[scaledInts_j] > maxInGroup) {
                                maxInGroup = paddedArray[scaledInts_j];
                            }
                        }

                        int bitWidth = 64 - Long.numberOfLeadingZeros(Math.max(1, maxInGroup));
                        bitWidths[scaledInts_i / pack_size] = bitWidth;
                    }

                    byte[] compressedData = encodeBitPacking(paddedArray, bitWidths, pack_size);
                    long cur_cost = compressedData.length * 8L; // 转换为bit数
                    long duration = System.nanoTime() - startTime;
                    modelTime += (duration);
                    modelCost += cur_cost;

                    // 新增：测试解压性能
                    long startDecodeTime = System.nanoTime();
                    int[] decodedData = decodeBitPacking(compressedData, bitWidths, pack_size, scaledInts.length);
                    long decodeDuration = System.nanoTime() - startDecodeTime;
                    modelDecodeTime += decodeDuration;

                }

            }
            modelCost = modelCost / time_of_repeat;
            modelTime = (modelTime) / time_of_repeat;
            modelDecodeTime = (modelDecodeTime) / time_of_repeat; // 平均解压时间

            double model_ratio = (double) modelCost / (double) (numbers.size() * 64);
            double modelTime_throughput = (double) (numbers.size() * 8000L) / (double) (modelTime); // MB/s
            double modelDecodeTime_throughput = (double) (numbers.size() * 8000L) / (double) (modelDecodeTime); // MB/s

            // 更新输出记录，包含解压吞吐率
            String[] record = {
                    file.toString(),
                    "BP+RMQ",
                    String.valueOf(modelTime_throughput),
                    String.valueOf(modelDecodeTime_throughput),
                    String.valueOf(numbers.size()),
                    String.valueOf(modelCost),
                    String.valueOf(model_ratio)
            };
            writer.writeRecord(record);
            writer.close();

            System.out.println("Encoding throughput: " + modelTime_throughput + " MB/s");
            System.out.println("Decoding throughput: " + modelDecodeTime_throughput + " MB/s");
            System.out.println("Compression ratio: " + model_ratio);
        }
    }


    public static double[] computeBitWidthFeatures(int[] bitWidths) {
        int n = bitWidths.length;
        if (n == 0) {
            return new double[14];
        }

        double[] features = new double[10];

        // 1. 位宽的平均值/64
        double sum = 0;
        for (int bw : bitWidths) {
            sum += bw;
        }
        features[0] = sum / (n * 64.0);

        // 2. 位宽的中位数/64
        int[] sortedBitWidths = bitWidths.clone();
        Arrays.sort(sortedBitWidths);
        double median;
        if (n % 2 == 0) {
            median = (sortedBitWidths[n/2 - 1] + sortedBitWidths[n/2]) / 2.0;
        } else {
            median = sortedBitWidths[n/2];
        }
        features[1] = median / 64.0;

//        // 3. 位宽的极差/64
//        int minBw = sortedBitWidths[0];
//        int maxBw = sortedBitWidths[n-1];
//        features[2] = (maxBw - minBw) / 64.0;

        // 4. 位宽的标准差/64
        double mean = sum / n;
        double variance = 0;
        for (int bw : bitWidths) {
            variance += Math.pow(bw - mean, 2);
        }
        variance /= n;
        double stdDev = Math.sqrt(variance);
        features[2] = stdDev / 64.0;

        // 5-8. 位宽差分特征
        if (n > 1) {
            int[] diffs = new int[n-1];
            for (int i = 0; i < n-1; i++) {
                diffs[i] = Math.abs(bitWidths[i+1] - bitWidths[i]);
            }

            // 5. 位宽差分绝对均值/64
            double diffSum = 0;
            for (int diff : diffs) {
                diffSum += diff;
            }
            features[3] = diffSum / ((n-1) * 64.0);

            // 6. 位宽差分中位数/64
            int[] sortedDiffs = diffs.clone();
            Arrays.sort(sortedDiffs);
//            double diffMedian;
//            if ((n-1) % 2 == 0) {
//                diffMedian = (sortedDiffs[(n-1)/2 - 1] + sortedDiffs[(n-1)/2]) / 2.0;
//            } else {
//                diffMedian = sortedDiffs[(n-1)/2];
//            }
//            features[5] = diffMedian / 64.0;

//            // 7. 位宽差分极差/64
//            int minDiff = sortedDiffs[0];
//            int maxDiff = sortedDiffs[n-2];
//            features[4] = (maxDiff - minDiff) / 64.0;

            // 8. 位宽差分标准差/64
            double diffMean = diffSum / (n-1);
            double diffVariance = 0;
            for (int diff : diffs) {
                diffVariance += Math.pow(diff - diffMean, 2);
            }
            diffVariance /= (n-1);
            double diffStdDev = Math.sqrt(diffVariance);
            features[4] = diffStdDev / 64.0;
        } else {
            features[3] = 0;
            features[4] = 0;
//            features[6] = 0;
//            features[7] = 0;
        }

        // 9. 位宽游程平均长度/(n/8)
        double avgRunLength = 0;
        int runCount = 0;
        int currentRunLength = 1;
        for (int i = 1; i < n; i++) {
            if (bitWidths[i] == bitWidths[i-1]) {
                currentRunLength++;
            } else {
                avgRunLength += currentRunLength;
                runCount++;
                currentRunLength = 1;
            }
        }
        avgRunLength += currentRunLength;
        runCount++;
        avgRunLength /= runCount;
        features[5] = avgRunLength / (n / 8.0);

        // 10. 位宽局部最大值密度（中间值大于左右值的个数）
        int localMaxCount = 0;
        for (int i = 1; i < n-1; i++) {
            if (bitWidths[i] > bitWidths[i-1] && bitWidths[i] > bitWidths[i+1]) {
                localMaxCount++;
            }
        }
        features[6] = localMaxCount / (double)(n-2);

        // 11. 位宽局部最大值的平均相对幅度（每个局部最大值减相邻值的平均值）
        double totalRelativeAmplitude = 0;
        int localMaxWithAmplitude = 0;
        for (int i = 1; i < n-1; i++) {
            if (bitWidths[i] > bitWidths[i-1] && bitWidths[i] > bitWidths[i+1]) {
                double leftDiff = bitWidths[i] - bitWidths[i-1];
                double rightDiff = bitWidths[i] - bitWidths[i+1];
                totalRelativeAmplitude += (leftDiff + rightDiff) / 2.0;
                localMaxWithAmplitude++;
            }
        }
        features[7] = (localMaxWithAmplitude > 0) ? (totalRelativeAmplitude / localMaxWithAmplitude) / 64.0 : 0;

        // 12. 位宽熵
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        for (int bw : bitWidths) {
            frequencyMap.put(bw, frequencyMap.getOrDefault(bw, 0) + 1);
        }
        double entropy = 0;
        for (int count : frequencyMap.values()) {
            double probability = count / (double)n;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        // 归一化：最大熵为log2(64)=6
        features[8] = entropy / 6.0;

        // 13. 位宽单调段数量比例
        int monotonicSegments = 1;
        Boolean isIncreasing = null;
        for (int i = 1; i < n; i++) {
            if (bitWidths[i] > bitWidths[i-1]) {
                if (isIncreasing == null || !isIncreasing) {
                    monotonicSegments++;
                    isIncreasing = true;
                }
            } else if (bitWidths[i] < bitWidths[i-1]) {
                if (isIncreasing == null || isIncreasing) {
                    monotonicSegments++;
                    isIncreasing = false;
                }
            } else {
                // 相等时不变
            }
        }
        features[9] = monotonicSegments / (double)n;

//        // 14. 高位宽比例（大于32的比例）
//        int highBitWidthCount = 0;
//        for (int bw : bitWidths) {
//            if (bw > 32) {
//                highBitWidthCount++;
//            }
//        }
//        features[13] = highBitWidthCount / (double)n;

        return features;
    }
    public static void processChunkAndOutputFeatures(int[] scaledInts, CsvWriter datasetWriter, int chunkIndex) throws IOException {
        if (scaledInts.length == 0) return;

        // 计算每8个值的位宽序列
        int groupSize = 8;
        int numGroups = (scaledInts.length + groupSize - 1) / groupSize;
        int[] bitWidthsPerGroup = new int[numGroups];

        for (int i = 0; i < numGroups; i++) {
            int start = i * groupSize;
            int end = Math.min(start + groupSize, scaledInts.length);

            // 找到当前组中的最大值
            int maxInGroup = 0;
            for (int j = start; j < end; j++) {
                if (scaledInts[j] > maxInGroup) {
                    maxInGroup = scaledInts[j];
                }
            }

            // 计算最大值的位宽
            int bitWidth = 64 - Long.numberOfLeadingZeros(Math.max(1, maxInGroup));
            bitWidthsPerGroup[i] = bitWidth;
        }

        // 计算10个特征
        double[] features = computeBitWidthFeatures(bitWidthsPerGroup);

        // 计算optimal pack size
        int optimalPackSize = findOptimalPackSize(scaledInts);

        // 写入dataset.csv
        String[] datasetRecord = new String[12];
        datasetRecord[0] = String.valueOf(chunkIndex);  // 添加chunk索引作为第一列
        for (int f = 0; f < 10; f++) {
            datasetRecord[f + 1] = String.valueOf(features[f]);
        }
        datasetRecord[11] = String.valueOf(optimalPackSize);
        datasetWriter.writeRecord(datasetRecord);
    }

    @Test
    public void FeatureTest() throws IOException {
        System.out.println("\nPerformance Testing...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);

        // 创建dataset.csv文件用于存储特征和optimal pack size
        String datasetPath = outputDirstr + "/dataset.csv";
        CsvWriter datasetWriter = new CsvWriter(datasetPath, ',', StandardCharsets.UTF_8);

        // 写入dataset.csv的表头（增加chunk_index作为第一列）
        String[] datasetHeader = {
                "chunk_index",
                "mean_bitwidth",
                "median_bitwidth",
//                "range_bitwidth",
                "std_bitwidth",
                "mean_diff_bitwidth",
//                "median_diff_bitwidth",
//                "range_diff_bitwidth",
                "std_diff_bitwidth",
                "avg_run_length",
                "local_max_density",
                "local_max_amplitude",
                "entropy",
                "monotonic_segments",
//                "high_bitwidth_ratio",
                "optimal_pack_size"
        };
        datasetWriter.writeRecord(datasetHeader);

        // 用于跟踪chunk索引
        int globalChunkIndex = 0;

        for (File file : Objects.requireNonNull(dir.listFiles())) {

            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;
            System.out.println(file.getName());

            System.out.println("Processing " + file.getName() + "...");
            List<String> numbers = new ArrayList<>();
            List<Integer> decimalPlaces = new ArrayList<>();
            CsvReader csvReader = new CsvReader(file.getPath(), ',', StandardCharsets.UTF_8);
            while (csvReader.readRecord()) {
                for (String value : csvReader.getValues()) {
                    String numStr = value.trim();
                    if (!numStr.isEmpty()) {
                        numbers.add(numStr);
                        int decimal = 0, sigBits;
                        if (numStr.contains(".")) {
                            String[] parts = numStr.split("\\.");
                            decimal = parts[1].length();
                            sigBits = (int) ((parts[0].length() + decimal) * (Math.log(10) / Math.log(2)));
                        } else {
                            sigBits = (int) (numStr.length() * (Math.log(10) / Math.log(2)));
                        }
                        decimalPlaces.add(decimal);
                    }
                }
            }
            int time_of_repeat = 50;

            int decimalMax = decimalPlaces.stream().max(Integer::compare).orElse(0);

            // 分批处理，每1024个元素一批
            int batchSize = 1024;
            List<int[]> batches = new ArrayList<>();

            for (int i = 0; i < numbers.size(); i += batchSize) {
                int end = Math.min(numbers.size(), i + batchSize);
                List<String> batch = numbers.subList(i, end);
                int[] scaledBatch = scaleNumbers(batch, decimalMax);
                batches.add(scaledBatch);
            }

            // 计算总长度并拼接所有批次的结果
            int totalLength = batches.stream().mapToInt(arr -> arr.length).sum();
            int[] scaledInts_all = new int[totalLength];

            int currentIndex = 0;
            for (int[] batch : batches) {
                System.arraycopy(batch, 0, scaledInts_all, currentIndex, batch.length);
                currentIndex += batch.length;
            }

            for (int j = 0; j < time_of_repeat; j++) {
                int totalCost = 0;
                for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                    int end = Math.min(i + CHUNK_SIZE, numbers.size());
                    int[] scaledInts = new int[end - i];
                    if (end - i >= 0) System.arraycopy(scaledInts_all, i, scaledInts, 0, end - i);

                    // 处理chunk并输出特征到dataset.csv（只处理第一次迭代）
                    if (j == 0) {
                        processChunkAndOutputFeatures(scaledInts, datasetWriter, globalChunkIndex);
                        globalChunkIndex++;
                    }
                }
            }
        }

        // 关闭dataset.csv写入器
        datasetWriter.close();
        System.out.println("Dataset saved to: " + datasetPath);
        System.out.println("Total chunks processed: " + globalChunkIndex);
    }

    @Test
    public void RunLengthFeatureTest() throws IOException {
        System.out.println("\nRun Length Feature Testing...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);

        // 创建run-length.csv文件
        String runLengthPath = outputDirstr + "/run-length.csv";
        CsvWriter runLengthWriter = new CsvWriter(runLengthPath, ',', StandardCharsets.UTF_8);

        // 写入表头
        String[] header = {"run_length"};
        runLengthWriter.writeRecord(header);

        // 用于统计信息
        int totalRunLengths = 0;
        int totalChunks = 0;

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;

            System.out.println("Processing " + file.getName() + "...");
            List<String> numbers = new ArrayList<>();
            List<Integer> decimalPlaces = new ArrayList<>();
            CsvReader csvReader = new CsvReader(file.getPath(), ',', StandardCharsets.UTF_8);

            while (csvReader.readRecord()) {
                for (String value : csvReader.getValues()) {
                    String numStr = value.trim();
                    if (!numStr.isEmpty()) {
                        numbers.add(numStr);
                        int decimal = 0;
                        if (numStr.contains(".")) {
                            String[] parts = numStr.split("\\.");
                            decimal = parts[1].length();
                        }
                        decimalPlaces.add(decimal);
                    }
                }
            }
            csvReader.close();

            int decimalMax = decimalPlaces.stream().max(Integer::compare).orElse(0);

            // 分批处理
            int batchSize = 1024;
            List<int[]> batches = new ArrayList<>();

            for (int i = 0; i < numbers.size(); i += batchSize) {
                int end = Math.min(numbers.size(), i + batchSize);
                List<String> batch = numbers.subList(i, end);
                int[] scaledBatch = scaleNumbers(batch, decimalMax);
                batches.add(scaledBatch);
            }

            // 拼接所有批次的结果
            int totalLength = batches.stream().mapToInt(arr -> arr.length).sum();
            int[] scaledInts_all = new int[totalLength];
            int currentIndex = 0;
            for (int[] batch : batches) {
                System.arraycopy(batch, 0, scaledInts_all, currentIndex, batch.length);
                currentIndex += batch.length;
            }

            // 处理每个chunk
            for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                int end = Math.min(i + CHUNK_SIZE, numbers.size());
                int[] scaledInts = new int[end - i];
                if (end - i >= 0) System.arraycopy(scaledInts_all, i, scaledInts, 0, end - i);

                // 计算位宽序列
                int groupSize = 8;
                int numGroups = (scaledInts.length + groupSize - 1) / groupSize;
                int[] bitWidthsPerGroup = new int[numGroups];

                for (int g = 0; g < numGroups; g++) {
                    int start = g * groupSize;
                    int endGroup = Math.min(start + groupSize, scaledInts.length);
                    int maxInGroup = 0;
                    for (int j = start; j < endGroup; j++) {
                        if (scaledInts[j] > maxInGroup) {
                            maxInGroup = scaledInts[j];
                        }
                    }
                    int bitWidth = 64 - Long.numberOfLeadingZeros(Math.max(1, maxInGroup));
                    bitWidthsPerGroup[g] = bitWidth;
                }

                // 计算游程长度
                if (bitWidthsPerGroup.length > 0) {
                    List<Integer> runLengths = new ArrayList<>();
                    int currentRunLength = 1;

                    for (int j = 1; j < bitWidthsPerGroup.length; j++) {
                        if (bitWidthsPerGroup[j] == bitWidthsPerGroup[j-1]) {
                            currentRunLength++;
                        } else {
                            // 写入当前游程长度
                            runLengths.add(currentRunLength);
                            totalRunLengths++;
                            currentRunLength = 1;
                        }
                    }
                    // 写入最后一个游程长度
                    runLengths.add(currentRunLength);
                    totalRunLengths++;

                    // 将所有游程长度写入CSV文件（每行一个）
                    for (int runLength : runLengths) {
                        String[] record = {String.valueOf(runLength)};
                        runLengthWriter.writeRecord(record);
                    }

                    totalChunks++;
                }
            }
        }

        // 关闭写入器
        runLengthWriter.close();

        System.out.println("Run length file saved to: " + runLengthPath);
        System.out.println("Total chunks processed: " + totalChunks);
        System.out.println("Total run lengths recorded: " + totalRunLengths);

        // 显示一些统计信息
        System.out.println("\nRun length statistics saved to CSV file.");
        System.out.println("Each line in the CSV file represents one run length value.");
    }}