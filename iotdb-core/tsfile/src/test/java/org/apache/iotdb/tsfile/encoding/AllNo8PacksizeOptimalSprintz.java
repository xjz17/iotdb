package org.apache.iotdb.tsfile.encoding;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AllNo8PacksizeOptimalSprintz {

    static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data", "test.csv", "POI-lat.csv",
            "POI-lon.csv", "Basel-wind.csv", "Basel-temp.csv", "Air-sensor.csv");
    private static final int CHUNK_SIZE = 512;

    public static int getCount(long long1, int mask) {
        return ((int) (long1 & mask));
    }

    /**
     * 修改后的编码函数，支持任意大小的pack_size
     * 返回实际写入的字节数
     */
    public static int packValues(ArrayList<Integer> values, int offset, int count, int width, int encode_pos,
                                 byte[] encoded_result) {
        if (count <= 0) return encode_pos;

        int bufIdx = 0;
        int valueIdx = offset;
        int leftBit = 0;
        int totalBits = count * width;
        int totalBytes = (totalBits + 7) / 8; // 向上取整到字节

        while (valueIdx < count + offset) {
            int buffer = 0;
            int leftSize = 32;

            if (leftBit > 0) {
                buffer |= (values.get(valueIdx) << (32 - leftBit));
                leftSize -= leftBit;
                leftBit = 0;
                valueIdx++;
            }

            while (leftSize >= width && valueIdx < count + offset) {
                buffer |= (values.get(valueIdx) << (leftSize - width));
                leftSize -= width;
                valueIdx++;
            }

            if (leftSize > 0 && valueIdx < count + offset) {
                buffer |= (values.get(valueIdx) >>> (width - leftSize));
                leftBit = width - leftSize;
            }

            for (int j = 0; j < 4 && bufIdx < totalBytes; j++) {
                encoded_result[encode_pos] = (byte) ((buffer >>> ((3 - j) * 8)) & 0xFF);
                encode_pos++;
                bufIdx++;
                if (bufIdx >= totalBytes) {
                    return encode_pos;
                }
            }
        }
        return encode_pos;
    }

    /**
     * 修改后的解码函数，支持任意数量的值
     */
    public static void unpackValues(byte[] encoded, int offset, int count, int width, ArrayList<Integer> result_list) {
        if (count <= 0) return;

        int byteIdx = offset;
        long buffer = 0;
        int totalBits = 0;
        int valueIdx = 0;
        int totalBitsNeeded = count * width;
        int totalBytes = (totalBitsNeeded + 7) / 8;

        while (valueIdx < count) {
            while (totalBits < width && byteIdx - offset < totalBytes) {
                buffer = (buffer << 8) | (encoded[byteIdx] & 0xFF);
                byteIdx++;
                totalBits += 8;
            }

            while (totalBits >= width && valueIdx < count) {
                result_list.add((int) (buffer >>> (totalBits - width)));
                valueIdx++;
                totalBits -= width;
                buffer = buffer & ((1L << totalBits) - 1);
            }
        }
    }

    /**
     * 通用的bitpacking编码函数，支持任意pack_size
     */
    public static int bitPacking(ArrayList<Integer> numbers, int start, int count, int bit_width, int encode_pos,
                                 byte[] encoded_result) {
        if (count <= 0) return encode_pos;

        // 计算需要的字节数
        int totalBits = count * bit_width;
        int bytesNeeded = (totalBits + 7) / 8;

        // 确保数组有足够空间
        if (encode_pos + bytesNeeded > encoded_result.length) {
            byte[] newArray = new byte[encode_pos + bytesNeeded];
            System.arraycopy(encoded_result, 0, newArray, 0, encode_pos);
            encoded_result = newArray;
        }

        return packValues(numbers, start, count, bit_width, encode_pos, encoded_result);
    }

    /**
     * 解码函数 - 支持可变大小的数据块和非8倍数的pack_size
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
            if (valuesInGroup <= 0) break;

            // 计算这个数据块需要的字节数
            int bitsNeeded = valuesInGroup * bitWidth;
            int bytesNeeded = (bitsNeeded + 7) / 8;

            // 解码这个数据块
            ArrayList<Integer> groupData = new ArrayList<>(valuesInGroup);
            unpackValues(compressedData, decodePos, valuesInGroup, bitWidth, groupData);

            // 将解码的值复制到结果数组中
            for (int i = 0; i < groupData.size() && resultIndex < originalLength; i++) {
                result[resultIndex++] = groupData.get(i);
            }

            // 移动到下一个数据块
            decodePos += bytesNeeded;
        }

        // 3. 将解码出的 bitWidths 复制回传入的数组
        System.arraycopy(decodedBitWidths, 0, bitWidths, 0, totalGroups);

        return result;
    }

    /**
     * 使用RMQ（稀疏表）快速计算区间最大值，找到最优的pack_size
     * 现在可以返回任意整数，不一定是8的倍数
     */
    public static int findOptimalPackSize(int[] values) {
        int n = values.length;
        if (n < 8) return n; // 返回实际大小，而不是8

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

        // 枚举所有可能的pack_size（现在可以是任意整数，不再限制为8的倍数）
        int bestPackSize = 1;
        long bestCost = Long.MAX_VALUE;

        // 限制最大pack_size，避免性能问题
        int maxPackSize = Math.min(CHUNK_SIZE, n);

        for (int p = 1; p <= maxPackSize; p++) {
            int m = (n + p - 1) / p; // ceil(n/p)
            int r = n - (m - 1) * p; // 最后一个pack的大小

            long cost = 0;

            // 计算前m-1个pack的成本
            for (int i = 0; i < m - 1; i++) {
                int start = i * p;
                int end = start + p - 1;
                int maxBitWidth = queryMax.apply(start, end);
                // 计算实际存储需要的字节数：向上取整到字节
                int totalBits = p * maxBitWidth;
                int bytesNeeded = (totalBits + 7) / 8;
                cost += bytesNeeded * 8; // 转换为比特
            }

            // 计算最后一个pack的成本
            if (m > 0 && r > 0) {
                int lastStart = (m - 1) * p;
                int lastEnd = n - 1;
                int lastMaxBitWidth = queryMax.apply(lastStart, lastEnd);
                int totalBits = r * lastMaxBitWidth;
                int bytesNeeded = (totalBits + 7) / 8;
                cost += bytesNeeded * 8;
            }

            // 加上位宽信息的存储成本（每个pack用6位存储位宽）
            cost += m * z;

            if (cost < bestCost) {
                bestCost = cost;
                bestPackSize = p;
            }
        }

        return bestPackSize;
    }

    /**
     * 暴力计算最优pack_size - O(n^2)复杂度，支持非8倍数
     */
    public static int findOptimalPackSizeall(int[] values) {
        int n = values.length;
        if (n < 8) return n;

        // 计算全局最大位宽和z值
        int globalMax = 0;
        for (int value : values) {
            if (value > globalMax) {
                globalMax = value;
            }
        }
        int bitWidthGlobal = 64 - Long.numberOfLeadingZeros(Math.max(1, globalMax));
        int z = (int) Math.ceil(Math.log(bitWidthGlobal + 1) / Math.log(2));

        // 枚举所有可能的pack_size
        int bestPackSize = 1;
        long bestCost = Long.MAX_VALUE;

        // 限制最大pack_size，避免性能问题
        int maxPackSize = n;

        for (int p = 1; p <= maxPackSize; p++) {
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

                // 计算实际存储需要的字节数
                int totalBits = p * maxBitWidth;
                int bytesNeeded = (totalBits + 7) / 8;
                cost += bytesNeeded * 8;
            }

            // 计算最后一个pack的成本
            if (m > 0 && r > 0) {
                int lastStart = (m - 1) * p;
                int lastEnd = n - 1;

                int lastMaxBitWidth = 0;
                for (int j = lastStart; j <= lastEnd; j++) {
                    int bitWidth = 64 - Long.numberOfLeadingZeros(Math.max(1, values[j]));
                    if (bitWidth > lastMaxBitWidth) {
                        lastMaxBitWidth = bitWidth;
                    }
                }

                int totalBits = r * lastMaxBitWidth;
                int bytesNeeded = (totalBits + 7) / 8;
                cost += bytesNeeded * 8;
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

    /**
     * 修改后的编码函数，支持非8倍数的pack_size
     */
    public static byte[] encodeBitPacking(int[] originalArray, int[] bitWidths, int pack_size) {
        int totalGroups = bitWidths.length;
        int n = originalArray.length;

        // 计算位宽部分所需的字节数
        int bitWidthBits = totalGroups * 6;
        int bitWidthBytes = (bitWidthBits + 7) / 8;

        // 计算数据部分所需的最大字节数（预分配足够的空间）
        int maxDataBytes = 0;
        for (int group = 0; group < totalGroups; group++) {
            int bitWidth = bitWidths[group];
            int valuesInGroup = Math.min(pack_size, n - group * pack_size);
            if (valuesInGroup <= 0) continue;

            int totalBits = valuesInGroup * bitWidth;
            int bytesNeeded = (totalBits + 7) / 8;
            maxDataBytes += bytesNeeded;
        }

        int totalBytes = bitWidthBytes + maxDataBytes;
        byte[] encodedResult = new byte[totalBytes];

        // 1. 编码 bitWidths 信息（每个用 6 位）
        int bitPos = 0;
        for (int group = 0; group < totalGroups; group++) {
            int bitWidth = bitWidths[group] & 0x3F; // 确保只用低 6 位

            for (int bit = 5; bit >= 0; bit--) {
                int bitValue = (bitWidth >> bit) & 1;
                int byteIndex = bitPos / 8;
                int bitOffset = 7 - (bitPos % 8);

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

            // 这个pack实际有多少个值
            int valuesInGroup = Math.min(pack_size, n - startIndex);
            if (valuesInGroup <= 0) break;

            // 收集这个pack的所有值
            ArrayList<Integer> groupData = new ArrayList<>(valuesInGroup);
            for (int i = 0; i < valuesInGroup; i++) {
                int idx = startIndex + i;
                if (idx < n) {
                    groupData.add(originalArray[idx]);
                } else {
                    groupData.add(0); // 用 0 填充不足的部分
                }
            }

            // 编码这个pack
            encodePos = bitPacking(groupData, 0, valuesInGroup, bitWidth, encodePos, encodedResult);
        }

        // 裁剪到实际大小
        byte[] finalResult = new byte[encodePos];
        System.arraycopy(encodedResult, 0, finalResult, 0, encodePos);

        return finalResult;
    }

    public static int[] sprintz(int[] numbers) {
        int size = numbers.length;
        int[] result = new int[size];

        int first = numbers[0];
        result[0] = first;

        // 3. Process subsequent elements with delta + ZigZag encoding
        int prev = first;
        for (int i = 1; i < size; i++) {
            int current = numbers[i];
            int diff = current - prev;
            result[i] = (diff << 1) ^ (diff >> 31); // ZigZag encoding
            prev = current;
        }

        return result;
    }

    public static int[] sprintzDecode(int[] encodedData) {
        int size = encodedData.length;
        int[] result = new int[size];

        if (size == 0) return result;

        // 第一个元素是原始值
        result[0] = encodedData[0];

        // 后续元素需要ZigZag解码和累加
        int prev = result[0];
        for (int i = 1; i < size; i++) {
            int zigzagEncoded = encodedData[i];
            int diff = (zigzagEncoded >>> 1) ^ -(zigzagEncoded & 1); // ZigZag解码
            result[i] = prev + diff;
            prev = result[i];
        }

        return result;
    }


    @Test
    public void testVaryPackSize() throws IOException {
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/ElfTestData_camel";
        String outputDirStr = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/output_Sprintz_vary_pack_size";
        System.out.println("\nTesting Varying Pack Sizes...");
        File outputDir = new File(outputDirStr);
        if (!outputDir.exists()) outputDir.mkdir();

        File dir = new File(directory);
        int[] packSizes = {2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048};

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;

            System.out.println("\nTesting file: " + file.getName());
            String outputFile = outputDirStr + "/" + file.getName();
            CsvWriter writer = new CsvWriter(outputFile, ',', StandardCharsets.UTF_8);

            // 更新表头
            String[] head = {
                    "Input Direction",
                    "Encoding Algorithm",
                    "Encoding Time",
                    "Decoding Time",
                    "Points",
                    "Pack size",
                    "Compressed Size",
                    "Compression Ratio"
            };
            writer.writeRecord(head);

            // 读取数据
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
            int time_of_repeat = 1;

            // 缩放数据
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

            // 测试每个pack size
            for (int packSize : packSizes) {
                System.out.println("Testing pack size: " + packSize);

                long totalEncodeTime = 0;
                long totalDecodeTime = 0;
                long totalCompressedSize = 0;
                int totalPoints = 0;

                for (int j = 0; j < time_of_repeat; j++) {
                    for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                        int end = Math.min(i + CHUNK_SIZE, numbers.size());
                        int[] scaledInt = new int[end - i];
                        if (end - i >= 0) System.arraycopy(scaledInts_all, i, scaledInt, 0, end - i);

                        // 编码
                        long startEncodeTime = System.nanoTime();

                        int[] scaledInts = sprintz(scaledInt);

                        // 计算需要的组数
                        int numGroups = (scaledInts.length + packSize - 1) / packSize;
                        int[] bitWidths = new int[numGroups];

                        // 计算每个组的位宽
                        for (int group = 0; group < numGroups; group++) {
                            int startIdx = group * packSize;
                            int endIdx = Math.min(startIdx + packSize, scaledInts.length);

                            int maxInGroup = 0;
                            for (int idx = startIdx; idx < endIdx; idx++) {
                                if (scaledInts[idx] > maxInGroup) {
                                    maxInGroup = scaledInts[idx];
                                }
                            }

                            int bitWidth = 64 - Long.numberOfLeadingZeros(Math.max(1, maxInGroup));
                            bitWidths[group] = bitWidth;
                        }

                        // 编码数据
                        byte[] compressedData = encodeBitPacking(scaledInts, bitWidths, packSize);
                        long encodeDuration = System.nanoTime() - startEncodeTime;

                        // 解码
                        long startDecodeTime = System.nanoTime();
                        int[] decodedData = decodeBitPacking(compressedData, bitWidths, packSize, scaledInts.length);
                        int[] decodedInts = sprintzDecode(decodedData);
                        long decodeDuration = System.nanoTime() - startDecodeTime;

//                        // 验证解码结果
//                        boolean valid = true;
//                        for (int k = 0; k < scaledInts.length; k++) {
//                            if (scaledInts[k] != decodedData[k]) {
//                                System.err.println("Decode error at position " + k +
//                                        ": expected " + scaledInts[k] + ", got " + decodedData[k]);
//                                valid = false;
//                                break;
//                            }
//                        }
//
//                        if (!valid) {
//                            System.err.println("Decoding failed for pack size " + packSize);
//                        }

                        // 累加统计
                        totalEncodeTime += encodeDuration;
                        totalDecodeTime += decodeDuration;
                        totalCompressedSize += compressedData.length * 8L; // 转换为bits
                        totalPoints += scaledInts.length;
                    }
                }

                // 计算平均
                long avgEncodeTime = totalEncodeTime / time_of_repeat;
                long avgDecodeTime = totalDecodeTime / time_of_repeat;
                long avgCompressedSize = totalCompressedSize / time_of_repeat;

                // 计算吞吐率和压缩率
                double encodeThroughput = (double) (totalPoints * 8000L) / (double) avgEncodeTime; // MB/s
                double decodeThroughput = (double) (totalPoints * 8000L) / (double) avgDecodeTime; // MB/s
                double compressionRatio = (double) avgCompressedSize / (double) (totalPoints * 64);

                // 写入结果
                String[] record = {
                        file.toString(),
                        "BitPacking-sprintz",
                        String.valueOf(encodeThroughput),
                        String.valueOf(decodeThroughput),
                        String.valueOf(totalPoints),
                        String.valueOf(packSize),
                        String.valueOf(avgCompressedSize),
                        String.valueOf(compressionRatio)
                };
                writer.writeRecord(record);

                System.out.println("  Pack size: " + packSize +
                        ", Encode throughput: " + String.format("%.2f", encodeThroughput) + " MB/s" +
                        ", Decode throughput: " + String.format("%.2f", decodeThroughput) + " MB/s" +
                        ", Compression ratio: " + String.format("%.4f", compressionRatio));
            }

            writer.close();
            System.out.println("Results saved to: " + outputFile);
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("\nPerformance Testing...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/output_BP_RMQ_all_no8_sprintz";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);
        for (File file : Objects.requireNonNull(dir.listFiles())) {

            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;
            System.out.println(file.getName());
            String Output = outputDirstr + "/" + file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            String[] head = {
                    "Input Direction",
                    "Encoding Algorithm",
                    "Encoding Time",
                    "Decoding Time",
                    "Points",
                    "Compressed Size",
                    "Compression Ratio"
            };
            writer.writeRecord(head);
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
            long modelDecodeTime = 0;

            for (int j = 0; j < time_of_repeat; j++) {
                int totalCost = 0;
                for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                    int end = Math.min(i + CHUNK_SIZE, numbers.size());
                    int[] scaledInt = new int[end - i];
                    if (end - i >= 0) System.arraycopy(scaledInts_all, i, scaledInt, 0, end - i);

                    long startTime = System.nanoTime();
                    // 使用优化的方法找到最优pack_size（现在可以是任意整数）
                    int[] scaledInts = sprintz(scaledInt);
                    int pack_size = findOptimalPackSizeall(scaledInts);

                    // 确保pack_size至少为1
                    pack_size = Math.max(1, pack_size);

                    int num_of_pack_size = (scaledInts.length + pack_size - 1) / pack_size;
                    int[] bitWidths = new int[num_of_pack_size];

                    // 计算每个pack的位宽
                    for (int scaledInts_i = 0; scaledInts_i < scaledInts.length; scaledInts_i += pack_size) {
                        int maxInGroup = 0;
                        int end_index = Math.min(scaledInts_i + pack_size, scaledInts.length);
                        for (int scaledInts_j = scaledInts_i; scaledInts_j < end_index; scaledInts_j++) {
                            if (scaledInts[scaledInts_j] > maxInGroup) {
                                maxInGroup = scaledInts[scaledInts_j];
                            }
                        }

                        int bitWidth = 64 - Long.numberOfLeadingZeros(Math.max(1, maxInGroup));
                        bitWidths[scaledInts_i / pack_size] = bitWidth;
                    }

                    byte[] compressedData = encodeBitPacking(scaledInts, bitWidths, pack_size);
                    long cur_cost = compressedData.length * 8L; // 转换为bit数
                    long duration = System.nanoTime() - startTime;
                    modelTime += (duration);
                    modelCost += cur_cost;

                    // 测试解压性能
                    long startDecodeTime = System.nanoTime();
                    int[] decodedData = decodeBitPacking(compressedData, bitWidths, pack_size, scaledInts.length);
                    int[] scaledDecoded = sprintzDecode(decodedData);
                    long decodeDuration = System.nanoTime() - startDecodeTime;
                    modelDecodeTime += decodeDuration;

                }

            }
            modelCost = modelCost / time_of_repeat;
            modelTime = (modelTime) / time_of_repeat;
            modelDecodeTime = (modelDecodeTime) / time_of_repeat;

            double model_ratio = (double) modelCost / (double) (numbers.size() * 64);
            double modelTime_throughput = (double) (numbers.size() * 8000L) / (double) (modelTime);
            double modelDecodeTime_throughput = (double) (numbers.size() * 8000L) / (double) (modelDecodeTime);

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

            System.out.println("Optimal pack_size found, encoding throughput: " + modelTime_throughput + " MB/s");
            System.out.println("Decoding throughput: " + modelDecodeTime_throughput + " MB/s");
            System.out.println("Compression ratio: " + model_ratio);
        }
    }

    @Test
    public void BPTest() throws IOException {
        System.out.println("\nPerformance Testing...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/output_sprintz";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);
        for (File file : Objects.requireNonNull(dir.listFiles())) {

            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;
            System.out.println(file.getName());
            String Output = outputDirstr + "/" + file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            String[] head = {
                    "Input Direction",
                    "Encoding Algorithm",
                    "Encoding Time",
                    "Decoding Time",
                    "Points",
                    "Compressed Size",
                    "Compression Ratio"
            };
            writer.writeRecord(head);
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
            long modelCost = 0;
            long modelTime = 0;
            long modelDecodeTime = 0;

            for (int j = 0; j < time_of_repeat; j++) {
                int totalCost = 0;
                for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                    int end = Math.min(i + CHUNK_SIZE, numbers.size());
                    int[] scaledInt = new int[end - i];
                    if (end - i >= 0) System.arraycopy(scaledInts_all, i, scaledInt, 0, end - i);

                    long startTime = System.nanoTime();
                    // 使用优化的方法找到最优pack_size（现在可以是任意整数）
                    int[] scaledInts = sprintz(scaledInt);
                    int pack_size = 8;

                    // 确保pack_size至少为1
//                    pack_size = Math.max(1, pack_size);

                    int num_of_pack_size = (scaledInts.length + pack_size - 1) / pack_size;
                    int[] bitWidths = new int[num_of_pack_size];

                    // 计算每个pack的位宽
                    for (int scaledInts_i = 0; scaledInts_i < scaledInts.length; scaledInts_i += pack_size) {
                        int maxInGroup = 0;
                        int end_index = Math.min(scaledInts_i + pack_size, scaledInts.length);
                        for (int scaledInts_j = scaledInts_i; scaledInts_j < end_index; scaledInts_j++) {
                            if (scaledInts[scaledInts_j] > maxInGroup) {
                                maxInGroup = scaledInts[scaledInts_j];
                            }
                        }

                        int bitWidth = 64 - Long.numberOfLeadingZeros(Math.max(1, maxInGroup));
                        bitWidths[scaledInts_i / pack_size] = bitWidth;
                    }

                    byte[] compressedData = encodeBitPacking(scaledInts, bitWidths, pack_size);
                    long cur_cost = compressedData.length * 8L; // 转换为bit数
                    long duration = System.nanoTime() - startTime;
                    modelTime += (duration);
                    modelCost += cur_cost;

                    // 测试解压性能
                    long startDecodeTime = System.nanoTime();
                    int[] decodedData = decodeBitPacking(compressedData, bitWidths, pack_size, scaledInts.length);
                    int[] scaledDecoded = sprintzDecode(decodedData);
                    long decodeDuration = System.nanoTime() - startDecodeTime;
                    modelDecodeTime += decodeDuration;

                }

            }
            modelCost = modelCost / time_of_repeat;
            modelTime = (modelTime) / time_of_repeat;
            modelDecodeTime = (modelDecodeTime) / time_of_repeat;

            double model_ratio = (double) modelCost / (double) (numbers.size() * 64);
            double modelTime_throughput = (double) (numbers.size() * 8000L) / (double) (modelTime);
            double modelDecodeTime_throughput = (double) (numbers.size() * 8000L) / (double) (modelDecodeTime);

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

            System.out.println("Optimal pack_size found, encoding throughput: " + modelTime_throughput + " MB/s");
            System.out.println("Decoding throughput: " + modelDecodeTime_throughput + " MB/s");
            System.out.println("Compression ratio: " + model_ratio);
        }
    }


    @Test
    public void TestVariablePageSizeSprintz() throws IOException {
        System.out.println("\nPerformance Testing with Variable Chunk Sizes...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/output_sprintz_vary_page_size";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);

        // 定义要测试的chunk sizes (m*8 where m is 16, 32, 64, 128, 256, 512, 1024)
        int[] chunkSizes = {16*8, 32*8, 64*8, 128*8, 256*8, 512*8, 1024*8};

        for (File file : Objects.requireNonNull(dir.listFiles())) {

            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;
            System.out.println("Processing " + file.getName() + " with variable chunk sizes...");
            String Output = outputDirstr+"/"+file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            String[] head = {
                    "m",
                    "Input Direction",
                    "Encoding Algorithm",
                    "Encoding Time",
                    "Decoding Time",
                    "Points",
                    "Compressed Size",
                    "Pack Size",
                    "Compression Ratio"
            };
            writer.writeRecord(head);

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

            int time_of_repeat = 50; // 减少重复次数以加快测试速度

            int decimalMax = decimalPlaces.stream().max(Integer::compare).orElse(0);

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
            // 测试每个chunk size
            for (int chunkSize : chunkSizes) {
                System.out.println("Testing chunk size: " + chunkSize);
//                System.out.println(numbers.subList(0,1000));

                for(int pack_size_exp = 3; pack_size_exp < 4; pack_size_exp++) {
                    int pack_size = (int) Math.pow(2, pack_size_exp);
                    int modelCost = 0;
                    long modelTime = 0;
                    long modelDecodeTime = 0;


                    for (int j = 0; j < time_of_repeat; j++) {
                        int totalCost = 0;
                        for (int i = 0; i < numbers.size(); i += chunkSize) {

//                            List<String> chunkNumbers = numbers.subList(i, Math.min(i + chunkSize, numbers.size()));

//                            if (chunkNumbers.size() == 1 || chunkNumbers.size() == 2)
//                                continue;
//                            int decimalMax = decimalPlaces.subList(i, Math.min(i + chunkSize, numbers.size()))
//                                    .stream().max(Integer::compare).orElse(0);

                            int end = Math.min(i + chunkSize, numbers.size());
                            int[] scaledInt = new int[end-i];
                            if (end - i >= 0) System.arraycopy(scaledInts_all, i, scaledInt, 0, end - i);


                            long startTime = System.nanoTime();
                            int[] scaledInts = sprintz(scaledInt);


                            int remainder = scaledInts.length % pack_size;
                            int paddingLength = (remainder == 0) ? 0 : pack_size - remainder;

                            // 创建新数组，长度补齐为pack_size的倍数
                            int[] paddedArray = new int[scaledInts.length + paddingLength];
                            System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                            int actual_length = paddedArray.length;
                            int[] bitWidths = new int[actual_length / pack_size];

                            for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += pack_size) {
                                int maxInGroup = 0;
                                for (int scaledInts_j = scaledInts_i; scaledInts_j < scaledInts_i + pack_size; scaledInts_j++) {
                                    if (paddedArray[scaledInts_j] > maxInGroup) {
                                        maxInGroup = paddedArray[scaledInts_j];
                                    }
                                }

                                int bitWidth = 32 - Integer.numberOfLeadingZeros(maxInGroup);
                                bitWidths[scaledInts_i / pack_size] = bitWidth;
//                                System.out.println(bitWidth);
                            }

                            byte[] compressedData = encodeBitPacking(paddedArray, bitWidths, pack_size);
                            int cur_cost = compressedData.length * 8;
                            long duration = System.nanoTime() - startTime;

                            long startDecodeTime = System.nanoTime();
                            int[] decodedData = decodeBitPacking(compressedData, bitWidths, pack_size, scaledInts.length);
                            int[] decodedInts = sprintzDecode(decodedData);
                            long decodeDuration = System.nanoTime() - startDecodeTime;
                            modelDecodeTime += decodeDuration;

                            modelTime += (duration);
                            modelCost += cur_cost;
                        }
                    }

                    modelCost /= time_of_repeat;
                    modelTime = (modelTime) / time_of_repeat;
                    modelDecodeTime /= time_of_repeat;
                    double model_ratio = (double) modelCost / (double) (numbers.size() * 64);
                    double modelTime_throughput = (double) (numbers.size() * 8000) / (double) (modelTime);
                    double modelDecodeTime_throughput = (double) (numbers.size() * 8000) / (double) (modelTime);

                    String[] record = {
                            String.valueOf(chunkSize),
                            file.toString(),
                            "Sprintz",
                            String.valueOf(modelTime_throughput),
                            String.valueOf(modelDecodeTime_throughput),
                            String.valueOf(numbers.size()),
                            String.valueOf(modelCost),
                            String.valueOf(pack_size),
                            String.valueOf(model_ratio)
                    };
                    writer.writeRecord(record);
                }
            }
            writer.close();
//            break;
        }
    }


    @Test
    public void TestVariablePageSizeSprintzStar() throws IOException {
        System.out.println("\nPerformance Testing with Variable Chunk Sizes...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/output_SprintzStar_vary_page_size";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);

        // 定义要测试的chunk sizes (m*8 where m is 16, 32, 64, 128, 256, 512, 1024)
        int[] chunkSizes = {16*8, 32*8, 64*8, 128*8, 256*8, 512*8, 1024*8};

        for (File file : Objects.requireNonNull(dir.listFiles())) {

            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;
            System.out.println("Processing " + file.getName() + " with variable chunk sizes...");
            String Output = outputDirstr+"/"+file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            String[] head = {
                    "m",
                    "Input Direction",
                    "Encoding Algorithm",
                    "Encoding Time",
                    "Decoding Time",
                    "Points",
                    "Compressed Size",
//                    "Pack Size",
                    "Compression Ratio"
            };
            writer.writeRecord(head);

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

            int time_of_repeat = 50; // 减少重复次数以加快测试速度
//            int decimalMax = decimalPlaces.stream().max(Integer::compare).orElse(0);
//            int[] scaledInts_all = scaleNumbers(numbers, decimalMax);

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
            // 测试每个chunk size
            for (int chunkSize : chunkSizes) {
                System.out.println("Testing chunk size: " + chunkSize);
//                System.out.println(numbers.subList(0,1000));

                for(int pack_size_exp = 3; pack_size_exp < 4; pack_size_exp++) {
//                    int pack_size = (int) Math.pow(2, pack_size_exp);

                    int modelCost = 0;
                    long modelTime = 0;
                    long modelDecodeTime = 0;


                    for (int j = 0; j < time_of_repeat; j++) {
                        int totalCost = 0;
                        for (int i = 0; i < numbers.size(); i += chunkSize) {

//                            List<String> chunkNumbers = numbers.subList(i, Math.min(i + chunkSize, numbers.size()));

//                            if (chunkNumbers.size() == 1 || chunkNumbers.size() == 2)
//                                continue;
//                            int decimalMax = decimalPlaces.subList(i, Math.min(i + chunkSize, numbers.size()))
//                                    .stream().max(Integer::compare).orElse(0);

                            int end = Math.min(i + chunkSize, numbers.size());
                            int[] scaledInt = new int[end-i];
                            if (end - i >= 0) System.arraycopy(scaledInts_all, i, scaledInt, 0, end - i);


                            long startTime = System.nanoTime();
                            int[] scaledInts = sprintz(scaledInt);
                            int pack_size = findOptimalPackSizeall(scaledInts);
                            int remainder = scaledInts.length % pack_size;
                            int paddingLength = (remainder == 0) ? 0 : pack_size - remainder;

                            // 创建新数组，长度补齐为pack_size的倍数
                            int[] paddedArray = new int[scaledInts.length + paddingLength];
                            System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                            int actual_length = paddedArray.length;
                            int[] bitWidths = new int[actual_length / pack_size];

                            for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += pack_size) {
                                int maxInGroup = 0;
                                for (int scaledInts_j = scaledInts_i; scaledInts_j < scaledInts_i + pack_size; scaledInts_j++) {
                                    if (paddedArray[scaledInts_j] > maxInGroup) {
                                        maxInGroup = paddedArray[scaledInts_j];
                                    }
                                }

                                int bitWidth = 32 - Integer.numberOfLeadingZeros(maxInGroup);
                                bitWidths[scaledInts_i / pack_size] = bitWidth;
//                                System.out.println(bitWidth);
                            }

                            byte[] compressedData = encodeBitPacking(paddedArray, bitWidths, pack_size);
                            int cur_cost = compressedData.length * 8;
                            long duration = System.nanoTime() - startTime;

                            long startDecodeTime = System.nanoTime();
                            int[] decodedData = decodeBitPacking(compressedData, bitWidths, pack_size, scaledInts.length);
                            int[] decodedInts = sprintzDecode(decodedData);
                            long decodeDuration = System.nanoTime() - startDecodeTime;
                            modelDecodeTime += decodeDuration;

                            modelTime += (duration);
                            modelCost += cur_cost;
                        }
                    }

                    modelCost /= time_of_repeat;
                    modelTime = (modelTime) / time_of_repeat;
                    modelDecodeTime /= time_of_repeat;
                    double model_ratio = (double) modelCost / (double) (numbers.size() * 64);
                    double modelTime_throughput = (double) (numbers.size() * 8000) / (double) (modelTime);
                    double modelDecodeTime_throughput = (double) (numbers.size() * 8000) / (double) (modelTime);

                    String[] record = {
                            String.valueOf(chunkSize),
                            file.toString(),
                            "Sprintz-Star",
                            String.valueOf(modelTime_throughput),
                            String.valueOf(modelDecodeTime_throughput),
                            String.valueOf(numbers.size()),
                            String.valueOf(modelCost),
//                            String.valueOf(pack_size),
                            String.valueOf(model_ratio)
                    };
                    writer.writeRecord(record);
                }
            }
            writer.close();
//            break;
        }
    }

}