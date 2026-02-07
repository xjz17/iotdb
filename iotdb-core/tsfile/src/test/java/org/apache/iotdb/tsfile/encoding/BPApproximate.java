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

public class BPApproximate {
    static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data", "test.csv","POI-lat.csv",
            "POI-lon.csv","Basel-wind.csv","Basel-temp.csv","Air-sensor.csv");
    private static final int CHUNK_SIZE = 1024;
    private static final int BASELINE_PACK_SIZE = 8;

    // 压缩方案标记
    static final byte SCHEME_BASELINE = 0;  // 使用baseline bitpacking
    static final byte SCHEME_RLE = 1;       // 使用RLE分段压缩

    // 段数据结构
    static class Segment {
        int bitWidth;  // 段的位宽（0-63）
        int length;    // 段的长度（包含的组数）
        double cost;   // 段的成本
        int packSize;  // pack大小

        Segment(int bitWidth, int length, int packSize) {
            this.bitWidth = bitWidth;
            this.length = length;
            this.packSize = packSize;
            this.cost = calculateCost();
        }

        private double calculateCost() {
            // 段的成本包括段头成本(16bits)和数据存储成本
            return 16 + bitWidth * length * packSize;
        }
    }

    // 压缩结果
    static class CompressionResult {
        byte[] data;
        byte scheme;  // 0: baseline, 1: RLE
        int compressedSize;
        double compressionRatio;
        int packSize; // 使用的packsize

        CompressionResult(byte[] data, byte scheme, int packSize) {
            this.data = data;
            this.scheme = scheme;
            this.compressedSize = data.length * 8;
            this.packSize = packSize;
        }
    }

    // 分段评估结果
    static class SegmentationResult {
        List<Segment> segments;
        double totalCost;
        int numSegments;
        double compressionRatio;

        SegmentationResult(List<Segment> segments, int originalSize) {
            this.segments = segments;
            this.totalCost = segments.stream().mapToDouble(s -> s.cost).sum();
            this.numSegments = segments.size();
            this.compressionRatio = originalSize / this.totalCost;
        }
    }

    public static void main(String[] args) throws IOException {
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_BPRLE";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);
        for (File file : Objects.requireNonNull(dir.listFiles())) {

            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;
            System.out.println(file.getName());
            String Output = outputDirstr+"/"+file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            // 表头
            String[] head = {
                    "Input Direction",
                    "Encoding Algorithm",
                    "Encoding Time",
                    "Decoding Time",
                    "Points",
                    "Compressed Size",
                    "Compression Ratio",
                    "Selected Scheme",
                    "Average Segment Length",
                    "Pack Size Used"
            };
            writer.writeRecord(head);
            System.out.println("Processing " + file.getName() + "...");

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

            int time_of_repeat = 50;

            long modelCost = 0;
            long modelTime = 0;
            long modelDecodeTime = 0;
            int baselineCount = 0;
            int rleCount = 0;
            double avgSegmentLength = 0;
            int avgPackSizeUsed = 0;

            for(int j = 0; j < time_of_repeat; j++){
                int totalCost = 0;
                int totalSegments = 0;
                int totalPackSizeUsed = 0;

                for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                    List<String> chunkNumbers = numbers.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()));
                    if(chunkNumbers.size() < 8) continue;

                    int decimalMax = decimalPlaces.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()))
                            .stream().max(Integer::compare).orElse(0);

                    int[] scaledInts = scaleNumbers(chunkNumbers, decimalMax);

                    long startTime = System.nanoTime();

                    // 使用智能选择方案
                    CompressionResult compResult = encodeWithSmartSelection(scaledInts, 8);
                    int curCost = compResult.compressedSize;
                    totalPackSizeUsed += compResult.packSize;

                    // 统计方案选择
                    if (compResult.scheme == SCHEME_BASELINE) {
                        baselineCount++;
                    } else {
                        rleCount++;

                        // 获取分段信息
                        SegmentationResult segResult = getSegmentationInfo(scaledInts, compResult.packSize);
                        totalSegments += segResult.numSegments;
                    }

                    long duration = System.nanoTime() - startTime;
                    modelTime += duration;
                    modelCost += curCost;

                    // 解码测试
                    long startDecodeTime = System.nanoTime();
                    int[] decodedData = decodeWithSmartSelection(compResult.data, scaledInts.length, compResult.packSize);
                    long decodeDuration = System.nanoTime() - startDecodeTime;
                    modelDecodeTime += decodeDuration;
                }

                avgSegmentLength = totalSegments > 0 ? (double)numbers.size() / (totalSegments * 8) : 0;
                avgPackSizeUsed = numbers.size() > 0 ? totalPackSizeUsed / (numbers.size() / CHUNK_SIZE + 1) : 0;
            }

            // 计算统计结果
            modelCost /= time_of_repeat;
            modelTime = modelTime / time_of_repeat;
            modelDecodeTime = modelDecodeTime / time_of_repeat;

            double model_ratio = (double) modelCost / (double) (numbers.size()*64);
            double modelTime_throughput = modelTime > 0 ? (double)(numbers.size()*8000L) / (double) (modelTime) : 0;
            double modelDecodeTime_throughput = modelDecodeTime > 0 ? (double)(numbers.size()*8000L) / (double) (modelDecodeTime) : 0;

            // 确定选择的方案
            String selectedScheme = baselineCount > rleCount ? "BASELINE" : "RLE";

            // 输出结果
            String[] record = {
                    file.toString(),
                    "RLE_Only",
                    String.valueOf(modelTime_throughput),
                    String.valueOf(modelDecodeTime_throughput),
                    String.valueOf(numbers.size()),
                    String.valueOf(modelCost),
                    String.valueOf(model_ratio),
                    selectedScheme,
                    String.valueOf(avgSegmentLength),
                    String.valueOf(avgPackSizeUsed)
            };
            writer.writeRecord(record);
            writer.close();
        }
    }

    @Test
    public void TestVarPackSize() throws IOException {
        System.out.println("\nPerformance Testing with Variable Pack Sizes...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_BPRLE_vary_pack_size";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;

            System.out.println("Processing " + file.getName() + "...");
            String Output = outputDirstr + "/" + file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            // 表头
            String[] head = {
                    "Input Direction",
                    "Encoding Algorithm",
                    "Encoding Time",
                    "Decoding Time",
                    "Points",
                    "Compressed Size",
                    "Pack Size",
                    "Compression Ratio",
                    "Selected Scheme",
                    "Average Segment Length"
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

            int time_of_repeat = 10;

            for (int pack_size_exp = 0; pack_size_exp < 10; pack_size_exp++) {
                int pack_size = (int) Math.pow(2, pack_size_exp);
                System.out.println("Testing pack size: " + pack_size);

                long modelCost = 0;
                long modelTime = 0;
                long modelDecodeTime = 0;
                int baselineCount = 0;
                int rleCount = 0;
                double avgSegmentLength = 0;

                for (int j = 0; j < time_of_repeat; j++) {
                    int totalCost = 0;
                    int totalSegments = 0;

                    for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                        List<String> chunkNumbers = numbers.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()));
                        if (chunkNumbers.size() < 8) continue;

                        int decimalMax = decimalPlaces.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()))
                                .stream().max(Integer::compare).orElse(0);

                        int[] scaledInts = scaleNumbers(chunkNumbers, decimalMax);

                        long startTime = System.nanoTime();

                        // 使用智能选择方案
                        CompressionResult compResult = encodeWithSmartSelection(scaledInts, pack_size);
                        int cur_cost = compResult.compressedSize;

                        // 统计方案选择
                        if (compResult.scheme == SCHEME_BASELINE) {
                            baselineCount++;
                        } else {
                            rleCount++;

                            // 获取分段信息
                            SegmentationResult segResult = getSegmentationInfo(scaledInts, compResult.packSize);
                            totalSegments += segResult.numSegments;
                        }

                        long duration = System.nanoTime() - startTime;
                        modelTime += duration;
                        modelCost += cur_cost;

                        // 解码测试
                        long startDecodeTime = System.nanoTime();
                        int[] decodedData = decodeWithSmartSelection(compResult.data, scaledInts.length, compResult.packSize);
                        long decodeDuration = System.nanoTime() - startDecodeTime;
                        modelDecodeTime += decodeDuration;
                    }

                    avgSegmentLength = totalSegments > 0 ? (double)numbers.size() / (totalSegments * pack_size) : 0;
                }

                modelCost /= time_of_repeat;
                modelTime = modelTime / time_of_repeat;
                modelDecodeTime = modelDecodeTime / time_of_repeat;

                double model_ratio = (double) modelCost / (double) (numbers.size() * 64);
                double modelTime_throughput = modelTime > 0 ? (double) (numbers.size() * 8000) / (double) (modelTime) : 0;
                double modelDecodeTime_throughput = modelDecodeTime > 0 ? (double) (numbers.size() * 8000) / (double) (modelDecodeTime) : 0;

                // 确定选择的方案
                String selectedScheme = baselineCount > rleCount ? "BASELINE" : "RLE";

                String[] record = {
                        file.toString(),
                        "RLE_Only",
                        String.valueOf(modelTime_throughput),
                        String.valueOf(modelDecodeTime_throughput),
                        String.valueOf(numbers.size()),
                        String.valueOf(modelCost),
                        String.valueOf(pack_size),
                        String.valueOf(model_ratio),
                        selectedScheme,
                        String.valueOf(avgSegmentLength)
                };
                writer.writeRecord(record);
            }

            writer.close();
        }
    }

    // 智能选择编码方案
    private static CompressionResult encodeWithSmartSelection(int[] data, int packSize) {
        // 计算baseline方案的压缩成本
        BaselineCostResult baselineResult = calculateBaselineCost(data);

        // 计算RLE方案的压缩成本
        int rleCost = calculateRLECost(data, packSize);

        // 选择成本更小的方案
        if (baselineResult.cost <= rleCost) {
            // 使用baseline方案
            byte[] compressedData = encodeWithBaseline(data, baselineResult.packSize);
            return new CompressionResult(compressedData, SCHEME_BASELINE, baselineResult.packSize);
        } else {
            // 使用RLE方案
            byte[] compressedData = encodeWithRLE(data, packSize);
            return new CompressionResult(compressedData, SCHEME_RLE, packSize);
        }
    }

    // Baseline成本结果类
    static class BaselineCostResult {
        int cost;
        int packSize;

        BaselineCostResult(int cost, int packSize) {
            this.cost = cost;
            this.packSize = packSize;
        }
    }

    // 计算baseline方案的压缩成本，尝试packsize 8和16
    private static BaselineCostResult calculateBaselineCost(int[] data) {
        int bestCost = Integer.MAX_VALUE;
        int bestPackSize = 8;

        // 尝试packsize 8和16
        for (int packSize : new int[]{8, 16}) {
            int groupCount = (data.length + packSize - 1) / packSize;

            // baseline成本包括：方案标记(8bits) + packsize标记(8bits) + 每个组的位宽(每个组8bits) + 数据位
            int headerCost = 16; // 2字节 = 16bits (方案标记和packsize标记)
            int groupHeadersCost = groupCount * 8; // 每个组一个字节存储位宽

            // 计算每个组的位宽和
            int totalBitWidth = 0;
            for (int i = 0; i < groupCount; i++) {
                int startIdx = i * packSize;
                int endIdx = Math.min(startIdx + packSize, data.length);
                int maxVal = 0;

                for (int j = startIdx; j < endIdx; j++) {
                    if (data[j] > maxVal) {
                        maxVal = data[j];
                    }
                }
                int bitWidth = 32 - Integer.numberOfLeadingZeros(maxVal);
                totalBitWidth += bitWidth * (endIdx - startIdx);
            }

            int currentCost = headerCost + groupHeadersCost + totalBitWidth;

            if (currentCost < bestCost) {
                bestCost = currentCost;
                bestPackSize = packSize;
            }
        }

        return new BaselineCostResult(bestCost, bestPackSize);
    }

    // 计算RLE方案的压缩成本
    private static int calculateRLECost(int[] data, int packSize) {
        // 获取分段信息
        SegmentationResult segResult = getSegmentationInfo(data, packSize);

        // RLE成本：1字节（方案标记）+ 分段信息 + 数据位
        int headerCost = 8; // 1字节方案标记
        int segmentHeaderCost = 16 + segResult.numSegments * 16; // 2字节段数 + 每段2字节

        return headerCost + segmentHeaderCost + (int)segResult.totalCost;
    }

    // baseline编码方案，支持动态packsize
    private static byte[] encodeWithBaseline(int[] data, int packSize) {
        List<Byte> result = new ArrayList<>();

        // 方案标记
        result.add(SCHEME_BASELINE);

        // 存储packsize标记 (0表示8, 1表示16)
        byte packSizeFlag = (byte) (packSize == 8 ? 0 : 1);
        result.add(packSizeFlag);

        int groupCount = (data.length + packSize - 1) / packSize;
        List<Integer> bitWidths = new ArrayList<>();

        // 计算每个组的位宽并存储
        for (int i = 0; i < groupCount; i++) {
            int startIdx = i * packSize;
            int endIdx = Math.min(startIdx + packSize, data.length);
            int maxVal = 0;

            for (int j = startIdx; j < endIdx; j++) {
                if (data[j] > maxVal) {
                    maxVal = data[j];
                }
            }
            int bitWidth = 32 - Integer.numberOfLeadingZeros(maxVal);
            bitWidths.add(bitWidth);
            result.add((byte) bitWidth);
        }

        // 进行bit-packing
        int totalBits = 0;
        for (int i = 0; i < bitWidths.size(); i++) {
            int bitWidth = bitWidths.get(i);
            int valuesInGroup = Math.min(packSize, data.length - i * packSize);
            totalBits += bitWidth * valuesInGroup;
        }

        int totalBytes = (totalBits + 7) / 8;
        byte[] packedData = new byte[totalBytes];

        int currentBytePos = 0;
        int currentBitPos = 0;

        for (int i = 0; i < groupCount; i++) {
            int bitWidth = bitWidths.get(i);
            int startIdx = i * packSize;
            int endIdx = Math.min(startIdx + packSize, data.length);

            for (int j = startIdx; j < endIdx; j++) {
                int value = data[j];
                for (int bit = bitWidth - 1; bit >= 0; bit--) {
                    int currentBit = (value >> bit) & 1;
                    packedData[currentBytePos] |= (currentBit << (7 - currentBitPos));
                    currentBitPos++;
                    if (currentBitPos == 8) {
                        currentBytePos++;
                        currentBitPos = 0;
                    }
                }
            }
        }

        // 添加打包数据
        for (byte b : packedData) {
            result.add(b);
        }

        // 转换为byte数组
        byte[] finalResult = new byte[result.size()];
        for (int i = 0; i < result.size(); i++) {
            finalResult[i] = result.get(i);
        }

        return finalResult;
    }

    // 智能解码方案
    private static int[] decodeWithSmartSelection(byte[] encodedData, int originalLength, int packSize) {
        if (encodedData.length == 0) {
            return new int[originalLength];
        }

        // 读取方案标记
        byte scheme = encodedData[0];

        if (scheme == SCHEME_BASELINE) {
            return decodeBaseline(encodedData, originalLength);
        } else {
            return decodeRLE(encodedData, originalLength, packSize);
        }
    }

    // baseline解码方案，支持动态packsize
    private static int[] decodeBaseline(byte[] encodedData, int originalLength) {
        try {
            if (encodedData.length < 2) {
                return new int[originalLength];
            }

            int pos = 1; // 跳过方案标记

            // 读取packsize标记
            byte packSizeFlag = encodedData[pos++];
            int packSize = packSizeFlag == 0 ? 8 : 16;

            int groupCount = (originalLength + packSize - 1) / packSize;

            // 读取每个组的位宽
            int[] bitWidths = new int[groupCount];
            for (int i = 0; i < groupCount; i++) {
                if (pos >= encodedData.length) break;
                bitWidths[i] = encodedData[pos++] & 0xFF;
            }

            int[] result = new int[originalLength];
            int resultIndex = 0;
            int currentBitPos = 0;

            for (int groupIdx = 0; groupIdx < groupCount && resultIndex < originalLength; groupIdx++) {
                int bitWidth = bitWidths[groupIdx];
                int valuesInGroup = Math.min(packSize, originalLength - resultIndex);

                for (int i = 0; i < valuesInGroup && resultIndex < originalLength; i++) {
                    int value = 0;

                    for (int bit = 0; bit < bitWidth; bit++) {
                        if (pos >= encodedData.length) {
                            value = (value << 1);
                        } else {
                            int currentBit = (encodedData[pos] >> (7 - currentBitPos)) & 1;
                            value = (value << 1) | currentBit;
                        }
                        currentBitPos++;

                        if (currentBitPos == 8) {
                            pos++;
                            currentBitPos = 0;
                        }
                    }

                    result[resultIndex++] = value;
                }
            }

            return result;
        } catch (Exception e) {
            System.err.println("baseline解码异常: " + e.getMessage());
            e.printStackTrace();
            return new int[originalLength];
        }
    }

    // 获取分段信息
    private static SegmentationResult getSegmentationInfo(int[] data, int packSize) {
        int groupCount = data.length / packSize;
        int[] bitWidths = new int[groupCount];

        for (int i = 0; i < groupCount; i++) {
            int maxInGroup = 0;
            int startIdx = i * packSize;
            for (int j = 0; j < packSize; j++) {
                if (data[startIdx + j] > maxInGroup) {
                    maxInGroup = data[startIdx + j];
                }
            }
            int bitWidth = 32 - Integer.numberOfLeadingZeros(maxInGroup);
            bitWidths[i] = bitWidth;
        }

        List<Segment> segments = computeRLESegmentation(bitWidths, packSize);
        return new SegmentationResult(segments, data.length * 32); // 原始大小为每个值32位
    }

    // RLE编码方案
    public static byte[] encodeWithRLE(int[] data, int packSize) {
        List<Byte> result = new ArrayList<>();

        // 添加方案标记
        result.add(SCHEME_RLE);

        // 处理数据，确保长度是packSize的倍数
        int actual_length = data.length;
        int remainder = actual_length % packSize;
        if (remainder != 0) {
            int paddingLength = packSize - remainder;
            int[] paddedArray = new int[actual_length + paddingLength];
            System.arraycopy(data, 0, paddedArray, 0, actual_length);
            data = paddedArray;
            actual_length = paddedArray.length;
        }

        int groupCount = actual_length / packSize;
        int[] bitWidths = new int[groupCount];

        // 计算每个组的bitwidth
        for (int i = 0; i < groupCount; i++) {
            int maxInGroup = 0;
            int startIdx = i * packSize;
            for (int j = 0; j < packSize; j++) {
                if (data[startIdx + j] > maxInGroup) {
                    maxInGroup = data[startIdx + j];
                }
            }
            int bitWidth = 32 - Integer.numberOfLeadingZeros(maxInGroup);
            bitWidths[i] = bitWidth;
        }

        // 使用RLE分段算法
        List<Segment> segments = computeRLESegmentation(bitWidths, packSize);

        // 对segments进行编码
        List<Byte> rleEncoded = encodeSegments(segments);
        result.addAll(rleEncoded);

        // 进行bit-packing
        int totalBitPackedBytes = 0;
        for (Segment segment : segments) {
            totalBitPackedBytes += (segment.bitWidth * packSize * segment.length + 7) / 8;
        }

        byte[] bitPackedData = new byte[totalBitPackedBytes];
        int encodePos = 0;

        // 按照分段进行编码
        int groupIndex = 0;
        for (Segment segment : segments) {
            for (int g = 0; g < segment.length; g++) {
                int startIndex = groupIndex * packSize;
                ArrayList<Integer> groupData = new ArrayList<>();
                for (int j = 0; j < packSize; j++) {
                    if (startIndex + j < data.length) {
                        groupData.add(data[startIndex + j]);
                    } else {
                        groupData.add(0);
                    }
                }
                encodePos = bitPacking(groupData, 0, segment.bitWidth, encodePos, bitPackedData);
                groupIndex++;
            }
        }

        // 添加bit-packed数据
        for (byte b : bitPackedData) {
            result.add(b);
        }

        // 转换为byte数组
        byte[] finalResult = new byte[result.size()];
        for (int i = 0; i < result.size(); i++) {
            finalResult[i] = result.get(i);
        }
        return finalResult;
    }

    // RLE分段算法
    private static List<Segment> computeRLESegmentation(int[] bitWidths, int packSize) {
        List<Segment> segments = new ArrayList<>();
        if (bitWidths.length == 0) return segments;

        int currentBitWidth = bitWidths[0];
        int currentLength = 1;

        for (int i = 1; i < bitWidths.length; i++) {
            if (bitWidths[i] == currentBitWidth) {
                currentLength++;
                // 限制最大长度（因为length-1只有10bits，最大1023，所以length最大1024）
                if (currentLength > 1024) {
                    segments.add(new Segment(currentBitWidth, currentLength - 1, packSize));
                    currentBitWidth = bitWidths[i];
                    currentLength = 1;
                }
            } else {
                segments.add(new Segment(currentBitWidth, currentLength, packSize));
                currentBitWidth = bitWidths[i];
                currentLength = 1;
            }
        }
        segments.add(new Segment(currentBitWidth, currentLength, packSize));

        // 尝试合并相邻的段
        int l = segments.size();
        int sMax = segments.stream().mapToInt(s -> s.length).max().orElse(0);
        int z = (int) Math.ceil(Math.log(sMax + 1) / Math.log(2));

        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < segments.size() - 1; i++) {
                Segment current = segments.get(i);
                Segment next = segments.get(i + 1);

                if (current.bitWidth == next.bitWidth) {
                    int mergedLength = current.length + next.length;
                    // 检查合并后的长度是否超过限制
                    if (mergedLength > 1024) {
                        continue;
                    }
                    boolean condition1 = mergedLength <= (1 << z) - 1;
                    boolean condition2 = l <= z + 6;

                    if (condition1 || condition2) {
                        current.length = mergedLength;
                        segments.remove(i + 1);
                        l--;
                        sMax = Math.max(sMax, mergedLength);
                        z = (int) Math.ceil(Math.log(sMax + 1) / Math.log(2));
                        changed = true;
                        i--;
                    }
                }
            }
        }

        return segments;
    }

    // RLE解码方案
    public static int[] decodeRLE(byte[] encodedData, int originalLength, int packSize) {
        try {
            // 跳过方案标记（第一个字节）
            int startPos = 1;

            // 解码段列表
            List<Segment> segments = decodeSegments(encodedData, startPos);
            if (segments.isEmpty()) {
                return new int[originalLength];
            }

            int pos = startPos + 2 + segments.size() * 2; // 每个段2字节
            int[] result = new int[originalLength];
            int resultIndex = 0;
            int currentBitPos = 0;

            for (Segment segment : segments) {
                if (resultIndex >= originalLength) break;

                int totalValues = segment.length * packSize;
                int valuesToRead = Math.min(totalValues, originalLength - resultIndex);

                for (int i = 0; i < valuesToRead && resultIndex < originalLength; i++) {
                    int value = 0;

                    for (int bit = 0; bit < segment.bitWidth; bit++) {
                        if (pos >= encodedData.length) {
                            value = (value << 1);
                        } else {
                            int currentBit = (encodedData[pos] >> (7 - currentBitPos)) & 1;
                            value = (value << 1) | currentBit;
                        }
                        currentBitPos++;

                        if (currentBitPos == 8) {
                            pos++;
                            currentBitPos = 0;
                        }
                    }

                    if (resultIndex < originalLength) {
                        result[resultIndex++] = value;
                    }
                }

                int remainingValues = totalValues - valuesToRead;
                if (remainingValues > 0) {
                    int bitsToSkip = remainingValues * segment.bitWidth;
                    for (int bit = 0; bit < bitsToSkip; bit++) {
                        currentBitPos++;
                        if (currentBitPos == 8) {
                            pos++;
                            currentBitPos = 0;
                        }
                    }
                }
            }

            return result;
        } catch (Exception e) {
            System.err.println("解码异常: " + e.getMessage());
            return new int[originalLength];
        }
    }

    // 编码段信息：bitWidth用6bits存储，length-1用10bits存储
    private static List<Byte> encodeSegments(List<Segment> segments) {
        List<Byte> result = new ArrayList<>();
        if (segments.isEmpty()) return result;

        int segmentCount = segments.size();
        // 段数用2字节存储
        result.add((byte) (segmentCount >> 8));
        result.add((byte) segmentCount);

        for (Segment segment : segments) {
            // 验证bitWidth范围（0-63）
            if (segment.bitWidth < 0 || segment.bitWidth > 63) {
                throw new IllegalArgumentException("bitWidth超出范围(0-63): " + segment.bitWidth);
            }

            // 验证length范围（1-1024）
            if (segment.length < 1 || segment.length > 1024) {
                throw new IllegalArgumentException("length超出范围(1-1024): " + segment.length);
            }

            // 存储length-1（0-1023）
            int lengthMinusOne = segment.length - 1;

            // 将bitWidth（高6位）和length-1（低10位）组合成一个16位整数
            int combined = (segment.bitWidth << 10) | lengthMinusOne;

            // 存储为2个字节
            result.add((byte) (combined >> 8));
            result.add((byte) combined);
        }

        return result;
    }

    // 解码段信息：从2字节中解码bitWidth和length
    private static List<Segment> decodeSegments(byte[] data, int startPos) {
        List<Segment> segments = new ArrayList<>();
        if (startPos + 2 > data.length) return segments;

        int segmentCount = ((data[startPos] & 0xFF) << 8) | (data[startPos + 1] & 0xFF);
        if (segmentCount <= 0) return segments;

        int pos = startPos + 2;
        for (int i = 0; i < segmentCount && pos + 1 < data.length; i++) {
            // 读取2字节
            int combined = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;

            // 解码bitWidth（高6位）
            int bitWidth = (combined >> 10) & 0x3F; // 0x3F = 6位掩码

            // 解码length-1（低10位）并加1得到实际length
            int lengthMinusOne = combined & 0x3FF; // 0x3FF = 10位掩码
            int length = lengthMinusOne + 1;

            segments.add(new Segment(bitWidth, length, 0)); // packSize在解码时不重要
        }

        return segments;
    }

    // Bit-packing方法
    public static int bitPacking(ArrayList<Integer> numbers, int start, int bit_width, int encode_pos,
                                 byte[] encoded_result) {
        int totalCount = numbers.size() - start;
        int currentBytePos = encode_pos;
        int currentBitPos = 0;

        for (int i = 0; i < totalCount; i++) {
            int value = numbers.get(start + i);
            for (int bit = bit_width - 1; bit >= 0; bit--) {
                int currentBit = (value >> bit) & 1;
                encoded_result[currentBytePos] |= (currentBit << (7 - currentBitPos));
                currentBitPos++;
                if (currentBitPos == 8) {
                    currentBytePos++;
                    currentBitPos = 0;
                }
            }
        }
        return currentBytePos;
    }

    // 数字缩放方法
    private static int[] scaleNumbers(List<String> numbers, int decimalMax) {
        BigDecimal scale = BigDecimal.TEN.pow(decimalMax);
        int size = numbers.size();
        int[] result = new int[size];

        if (size == 0) return result;

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

    @Test
    public void TestVariableChunkSize() throws IOException {
        System.out.println("\nPerformance Testing with Variable Chunk Sizes (RLE)...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_BPRLE_vary_m";
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
                    "Compression Ratio",
                    "Selected Scheme",
                    "Average Segment Length",
                    "Pack Size Used"
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

            int time_of_repeat = 10; // 减少重复次数以加快测试速度
            int decimalMax = decimalPlaces.stream().max(Integer::compare).orElse(0);

            // 分批处理，每1024个元素一批进行scaling
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

                // 固定pack size为8
                int pack_size = 8;

                long modelCost = 0;
                long modelTime = 0;
                long modelDecodeTime = 0;
                int baselineCount = 0;
                int rleCount = 0;
                double avgSegmentLength = 0;
                int avgPackSizeUsed = 0;

                for (int j = 0; j < time_of_repeat; j++) {
                    int totalCost = 0;
                    int totalSegments = 0;
                    int totalPackSizeUsed = 0;

                    for (int i = 0; i < scaledInts_all.length; i += chunkSize) {
                        int end = Math.min(i + chunkSize, scaledInts_all.length);
                        int[] chunkData = new int[end - i];
                        System.arraycopy(scaledInts_all, i, chunkData, 0, end - i);

                        if (chunkData.length < 8) continue;

                        long startTime = System.nanoTime();

                        // 使用智能选择方案
                        CompressionResult compResult = encodeWithSmartSelection(chunkData, pack_size);
                        int curCost = compResult.compressedSize;
                        totalPackSizeUsed += compResult.packSize;

                        // 统计方案选择
                        if (compResult.scheme == SCHEME_BASELINE) {
                            baselineCount++;
                        } else {
                            rleCount++;

                            // 获取分段信息
                            SegmentationResult segResult = getSegmentationInfo(chunkData, compResult.packSize);
                            totalSegments += segResult.numSegments;
                        }

                        long duration = System.nanoTime() - startTime;
                        modelTime += duration;
                        modelCost += curCost;

                        // 解码测试
                        long startDecodeTime = System.nanoTime();
                        int[] decodedData = decodeWithSmartSelection(compResult.data, chunkData.length, compResult.packSize);
                        long decodeDuration = System.nanoTime() - startDecodeTime;
                        modelDecodeTime += decodeDuration;
                    }

                    avgSegmentLength = totalSegments > 0 ? (double) scaledInts_all.length / (totalSegments * pack_size) : 0;
                    avgPackSizeUsed = scaledInts_all.length > 0 ? totalPackSizeUsed / ((scaledInts_all.length / chunkSize) + 1) : 0;
                }

                // 计算平均值
                modelCost /= time_of_repeat;
                modelTime = modelTime / time_of_repeat;
                modelDecodeTime = modelDecodeTime / time_of_repeat;

                // 计算压缩比和吞吐量
                double model_ratio = (double) modelCost / (double) (scaledInts_all.length * 32); // 每个原始值32位
                double modelTime_throughput = modelTime > 0 ? (double) (scaledInts_all.length * 1000000L) / (double) modelTime : 0; // points/ms
                double modelDecodeTime_throughput = modelDecodeTime > 0 ? (double) (scaledInts_all.length * 1000000L) / (double) modelDecodeTime : 0;

                // 确定选择的方案
                String selectedScheme = baselineCount > rleCount ? "BASELINE" : "RLE";

                // 写入结果
                String[] record = {
                        String.valueOf(chunkSize),
                        file.toString(),
                        "RLE_Only",
                        String.valueOf(modelTime_throughput),
                        String.valueOf(modelDecodeTime_throughput),
                        String.valueOf(scaledInts_all.length),
                        String.valueOf(modelCost),
                        String.valueOf(model_ratio),
                        selectedScheme,
                        String.valueOf(avgSegmentLength),
                        String.valueOf(avgPackSizeUsed)
                };
                writer.writeRecord(record);
            }
            writer.close();
        }
    }
//
//    // 在类中添加这个方法，用于计算Baseline方案的压缩成本
//    private static BaselineCostResult calculateBaselineCost(int[] data) {
//        int bestCost = Integer.MAX_VALUE;
//        int bestPackSize = 8;
//
//        // 尝试packsize 8和16
//        for (int packSize : new int[]{8, 16}) {
//            int groupCount = (data.length + packSize - 1) / packSize;
//
//            // baseline成本包括：方案标记(8bits) + packsize标记(8bits) + 每个组的位宽(每个组8bits) + 数据位
//            int headerCost = 16; // 2字节 = 16bits (方案标记和packsize标记)
//            int groupHeadersCost = groupCount * 8; // 每个组一个字节存储位宽
//
//            // 计算每个组的位宽和
//            int totalBitWidth = 0;
//            for (int i = 0; i < groupCount; i++) {
//                int startIdx = i * packSize;
//                int endIdx = Math.min(startIdx + packSize, data.length);
//                int maxVal = 0;
//
//                for (int j = startIdx; j < endIdx; j++) {
//                    if (data[j] > maxVal) {
//                        maxVal = data[j];
//                    }
//                }
//                int bitWidth = 32 - Integer.numberOfLeadingZeros(maxVal);
//                totalBitWidth += bitWidth * (endIdx - startIdx);
//            }
//
//            int currentCost = headerCost + groupHeadersCost + totalBitWidth;
//
//            if (currentCost < bestCost) {
//                bestCost = currentCost;
//                bestPackSize = packSize;
//            }
//        }
//
//        return new BaselineCostResult(bestCost, bestPackSize);
//    }
//
//    // 在类中添加这个方法，用于获取分段信息
//    private static SegmentationResult getSegmentationInfo(int[] data, int packSize) {
//        int groupCount = data.length / packSize;
//        int[] bitWidths = new int[groupCount];
//
//        for (int i = 0; i < groupCount; i++) {
//            int maxInGroup = 0;
//            int startIdx = i * packSize;
//            for (int j = 0; j < packSize; j++) {
//                if (data[startIdx + j] > maxInGroup) {
//                    maxInGroup = data[startIdx + j];
//                }
//            }
//            int bitWidth = 32 - Integer.numberOfLeadingZeros(maxInGroup);
//            bitWidths[i] = bitWidth;
//        }
//
//        List<Segment> segments = computeRLESegmentation(bitWidths, packSize);
//        return new SegmentationResult(segments, data.length * 32); // 原始大小为每个值32位
//    }
}