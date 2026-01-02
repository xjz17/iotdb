package org.apache.iotdb.tsfile.encoding;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FSprintz512 {
    private static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data","test.csv","POI-lat.csv",
            "POI-lon.csv","Air-sensor.csv","Basel-wind.csv","Basel-temp.csv");
    private static final int CHUNK_SIZE = 512;

    public static int getBitWith(long num) {
        if (num == 0)
            return 1;
        else
            return 64 - Long.numberOfLeadingZeros(num);
    }

    public static int getCount(long long1, int mask) {
        return ((int) (long1 & mask));
    }

    public static long getUniqueValue(long long1, int left_shift) {
        return ((long1) >> left_shift);
    }

    public static void longToBytes(long value, int encode_pos, byte[] cur_byte) {
        cur_byte[encode_pos] = (byte) (value >> 56);
        cur_byte[encode_pos + 1] = (byte) (value >> 48);
        cur_byte[encode_pos + 2] = (byte) (value >> 40);
        cur_byte[encode_pos + 3] = (byte) (value >> 32);
        cur_byte[encode_pos + 4] = (byte) (value >> 24);
        cur_byte[encode_pos + 5] = (byte) (value >> 16);
        cur_byte[encode_pos + 6] = (byte) (value >> 8);
        cur_byte[encode_pos + 7] = (byte) (value);
    }

    public static void intToBytes(int integer, int encode_pos, byte[] cur_byte) {
        cur_byte[encode_pos] = (byte) (integer >> 24);
        cur_byte[encode_pos + 1] = (byte) (integer >> 16);
        cur_byte[encode_pos + 2] = (byte) (integer >> 8);
        cur_byte[encode_pos + 3] = (byte) (integer);
    }

    public static long bytesToLong(byte[] encoded, int start) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value <<= 8;
            int b = encoded[i + start] & 0xFF;
            value |= b;
        }
        return value;
    }

    public static int bytesToInt(byte[] encoded, int start, int num) {
        int value = 0;
        if (num > 4) {
            System.out.println("bytesToInt error");
            return 0;
        }
        for (int i = 0; i < num; i++) {
            value <<= 8;
            int b = encoded[i + start] & 0xFF;
            value |= b;
        }
        return value;
    }

    public static void pack8Values(ArrayList<Long> values, int offset, int width, int encode_pos,
                                   byte[] encoded_result) {
        int bufIdx = 0;
        int valueIdx = offset;
        int leftBit = 0;

        while (valueIdx < 8 + offset) {
            long buffer = 0;
            int leftSize = 64;

            if (leftBit > 0) {
                buffer |= (values.get(valueIdx) << (64 - leftBit));
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

            for (int j = 0; j < 8; j++) {
                if (bufIdx >= width) return;
                encoded_result[encode_pos] = (byte) ((buffer >>> ((7 - j) * 8)) & 0xFF);
                encode_pos++;
                bufIdx++;
            }
        }
    }

    public static void unpack8Values(byte[] encoded, int offset, int width, ArrayList<Long> result_list) {
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
                result_list.add(buffer >>> (totalBits - width));
                valueIdx++;
                totalBits -= width;
                buffer = buffer & ((1L << totalBits) - 1);
            }
        }
    }

    public static int bitPacking(ArrayList<Long> numbers, int start, int bit_width, int encode_pos,
                                 byte[] encoded_result) {
        int block_num = (numbers.size() - start) / 8;
        for (int i = 0; i < block_num; i++) {
            pack8Values(numbers, start + i * 8, bit_width, encode_pos, encoded_result);
            encode_pos += bit_width;
        }
        return encode_pos;
    }

    public static ArrayList<Long> decodeBitPacking(
            byte[] encoded, int decode_pos, int bit_width, int block_size) {
        ArrayList<Long> result_list = new ArrayList<>();
        int block_num = (block_size - 1) / 8;

        for (int i = 0; i < block_num; i++) {
            unpack8Values(encoded, decode_pos, bit_width, result_list);
            decode_pos += bit_width;
        }
        return result_list;
    }

    private static long zigzagEncode(long n) {
        return (n << 1) ^ (n >> 63);
    }

    public static long[] scaleNumbers(List<String> numbers, int decimalMax) {
        long scale = (long) Math.pow(10, decimalMax);
        int size = numbers.size();
        long[] result = new long[size];

        if (size == 0) {
            return result;
        }

        long[] scaledValues = new long[size];
        for (int i = 0; i < size; i++) {
            String numStr = numbers.get(i);
            String[] parts = numStr.split("\\.");
            long whole = Long.parseLong(parts[0]);

            long fraction = 0;
            if (parts.length > 1) {
                String fractionStr = parts[1];
                if (fractionStr.length() < decimalMax) {
                    while (fractionStr.length() < decimalMax) {
                        fractionStr += "0";
                    }
                } else if (fractionStr.length() > decimalMax) {
                    fractionStr = fractionStr.substring(0, decimalMax);
                }
                fraction = Long.parseLong(fractionStr);
            }

            scaledValues[i] = whole * scale + fraction;
        }

        return scaledValues;
    }

    public static long[] sprintz(long[] numbers) {
        int size = numbers.length;
        long[] result = new long[size];

        long first = numbers[0];
        result[0] = first;

        long prev = first;
        for (int i = 1; i < size; i++) {
            long current = numbers[i];
            long diff = current - prev;
            result[i] = (diff << 1) ^ (diff >> 63); // ZigZag encoding for long
            prev = current;
        }

        return result;
    }

    public static byte[] encodeBitPacking(long[] paddedArray, int[] bitWidths, int pack_size) {
        List<Byte> result = new ArrayList<>();

        int totalGroups = bitWidths.length;
        int max_bit_width = 0;

        for (int bitWidth : bitWidths) {
            if (bitWidth > max_bit_width) {
                max_bit_width = bitWidth;
            }
        }

        int totalBitPackedBytes = (max_bit_width * pack_size * totalGroups + 7) / 8;
        byte[] bitPackedData = new byte[totalBitPackedBytes + totalGroups + 32];
        int encodePos = 0;

        for (int group = 0; group < totalGroups; group++) {
            int startIndex = group * pack_size;
            ArrayList<Long> groupData = new ArrayList<>();
            for (int i = 0; i < pack_size; i++) {
                if (startIndex + i < paddedArray.length) {
                    groupData.add(paddedArray[startIndex + i]);
                } else {
                    groupData.add(0L);
                }
            }

            bitPackedData[encodePos++] = (byte) bitWidths[group];
            encodePos = bitPacking(groupData, 0, bitWidths[group], encodePos, bitPackedData);
        }

        byte[] finalResult = new byte[encodePos];
        System.arraycopy(bitPackedData, 0, finalResult, 0, encodePos);

        return finalResult;
    }

    public static int computeMinPackingCost(int[] bitWidths, int fixed_pack, int pack_size) {
        int blocksize = bitWidths.length;
        int totalCost = 0;
        int numPacks = (int) Math.ceil((double) blocksize / fixed_pack);

        for (int pack = 0; pack < numPacks; pack++) {
            int start = pack * fixed_pack;
            int end = Math.min(start + fixed_pack, blocksize);

            int maxBitWidth = 0;
            for (int i = start; i < end; i++) {
                if (bitWidths[i] > maxBitWidth) {
                    maxBitWidth = bitWidths[i];
                }
            }

            totalCost += pack_size * (end - start) * maxBitWidth;
        }

        totalCost += 5 * blocksize / fixed_pack;
        return totalCost;
    }

    private static long zigzagDecode(long n) {
        return (n >>> 1) ^ -(n & 1);
    }

    public static long[] decodeBitPackingFull(byte[] compressedData, int originalLength, int packSize) {
        long[] result = new long[originalLength];
        int resultIndex = 0;
        int decodePos = 0;

        int totalGroups = (originalLength + packSize - 1) / packSize;
        long[] groupData = new long[packSize];

        for (int group = 0; group < totalGroups && resultIndex < originalLength; group++) {
            int bitWidth = compressedData[decodePos++] & 0xFF;

            if (bitWidth == 0) {
                int fillCount = Math.min(packSize, originalLength - resultIndex);
                Arrays.fill(result, resultIndex, resultIndex + fillCount, 0L);
                resultIndex += fillCount;
                continue;
            }

            int actualUnpacked = unpack8ValuesToArray(compressedData, decodePos, bitWidth, groupData);
            decodePos += bitWidth;

            int copyCount = Math.min(actualUnpacked, originalLength - resultIndex);
            System.arraycopy(groupData, 0, result, resultIndex, copyCount);
            resultIndex += copyCount;
        }

        return result;
    }

    private static int unpack8ValuesToArray(byte[] encoded, int offset, int width, long[] result) {
        int byteIdx = offset;
        long buffer = 0;
        int totalBits = 0;
        int valueIdx = 0;

        while (valueIdx < 8 && valueIdx < result.length) {
            while (totalBits < width) {
                buffer = (buffer << 8) | (encoded[byteIdx] & 0xFF);
                byteIdx++;
                totalBits += 8;
            }

            while (totalBits >= width && valueIdx < 8 && valueIdx < result.length) {
                result[valueIdx] = buffer >>> (totalBits - width);
                valueIdx++;
                totalBits -= width;
                buffer = buffer & ((1L << totalBits) - 1);
            }
        }

        return valueIdx;
    }

    public static long[] sprintzDecode(long[] encodedData) {
        int size = encodedData.length;
        long[] result = new long[size];

        if (size == 0) return result;

        result[0] = encodedData[0];
        long prev = result[0];

        for (int i = 1; i < size; i++) {
            long zigzagEncoded = encodedData[i];
            long diff = (zigzagEncoded >>> 1) ^ -(zigzagEncoded & 1);
            result[i] = prev + diff;
            prev = result[i];
        }

        return result;
    }

    public static double[] unscaleNumbers(long[] scaledValues, int decimalMax) {
        double scale = Math.pow(10, decimalMax);
        int size = scaledValues.length;
        double[] result = new double[size];

        for (int i = 0; i < size; i++) {
            result[i] = scaledValues[i] / scale;
        }

        return result;
    }

    public static long[] decompress(byte[] compressedData, int originalLength, int decimalMax, int packSize) {
        long[] bitUnpacked = decodeBitPackingFull(compressedData, originalLength, packSize);
        long[] sprintzDecoded = sprintzDecode(bitUnpacked);
        return sprintzDecoded;
    }

    public static long[] decodeBitPacking(byte[] compressedData, int[] bitWidths, int pack_size, int originalLength) {
        long[] result = new long[originalLength];
        int resultIndex = 0;
        int decodePos = 0;

        for (int group = 0; group < bitWidths.length && resultIndex < originalLength; group++) {
            int bitWidth = compressedData[decodePos++] & 0xFF;
            ArrayList<Long> groupData = new ArrayList<>(pack_size);
            unpack8Values(compressedData, decodePos, bitWidth, groupData);

            int copyLength = Math.min(pack_size, originalLength - resultIndex);
            for (int i = 0; i < copyLength; i++) {
                result[resultIndex++] = groupData.get(i);
            }

            decodePos += bitWidth;
        }

        return result;
    }

    // 以下测试方法也需要相应修改，将int[]改为long[]
    @Test
    public void printDataTest() throws IOException {
        System.out.println("\nPerformance Testing...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/bitwidth";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;

            System.out.println(file.getName());
            String Output = outputDirstr + "/" + file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            String[] head = {"BP"};
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
                        int decimal = 0;
                        if (numStr.contains(".")) {
                            String[] parts = numStr.split("\\.");
                            decimal = parts[1].length();
                        }
                        decimalPlaces.add(decimal);
                    }
                }
            }

            for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                List<String> chunkNumbers = numbers.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()));
                if (chunkNumbers.size() == 1 || chunkNumbers.size() == 2)
                    continue;

                int decimalMax = decimalPlaces.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()))
                        .stream().max(Integer::compare).orElse(0);

                long[] scaledInts = scaleNumbers(chunkNumbers, decimalMax);
                int remainder = scaledInts.length % 8;
                int paddingLength = (remainder == 0) ? 0 : 8 - remainder;

                long[] paddedArray = new long[scaledInts.length + paddingLength];
                System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                int actual_length = paddedArray.length;
                int[] bitWidths = new int[actual_length / 8];

                for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += 8) {
                    long maxInGroup = 0;
                    for (int scaledInts_j = scaledInts_i; scaledInts_j < scaledInts_i + 8; scaledInts_j++) {
                        if (paddedArray[scaledInts_j] > maxInGroup) {
                            maxInGroup = paddedArray[scaledInts_j];
                        }
                    }

                    int bitWidth = 64 - Long.numberOfLeadingZeros(maxInGroup);
                    bitWidths[scaledInts_i / 8] = bitWidth;

                    String[] record = {String.valueOf(bitWidth)};
                    writer.writeRecord(record);
                }
            }
            writer.close();
        }
    }

    @Test
    public void printSprintzDataTest() throws IOException {
        System.out.println("\nPerformance Testing...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/bitwidth_sprintz";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;

            System.out.println(file.getName());
            String Output = outputDirstr + "/" + file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            String[] head = {"Sprintz"};
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
                        int decimal = 0;
                        if (numStr.contains(".")) {
                            String[] parts = numStr.split("\\.");
                            decimal = parts[1].length();
                        }
                        decimalPlaces.add(decimal);
                    }
                }
            }

            for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                List<String> chunkNumbers = numbers.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()));
                if (chunkNumbers.size() == 1 || chunkNumbers.size() == 2)
                    continue;

                int decimalMax = decimalPlaces.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()))
                        .stream().max(Integer::compare).orElse(0);

                long[] scalingInt = scaleNumbers(chunkNumbers, decimalMax);
                long[] scaledInts = sprintz(scalingInt);

                int remainder = scaledInts.length % 8;
                int paddingLength = (remainder == 0) ? 0 : 8 - remainder;

                long[] paddedArray = new long[scaledInts.length + paddingLength];
                System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                int actual_length = paddedArray.length;
                int[] bitWidths = new int[actual_length / 8];

                for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += 8) {
                    long maxInGroup = 0;
                    for (int scaledInts_j = scaledInts_i; scaledInts_j < scaledInts_i + 8; scaledInts_j++) {
                        if (paddedArray[scaledInts_j] > maxInGroup) {
                            maxInGroup = paddedArray[scaledInts_j];
                        }
                    }

                    int bitWidth = 64 - Long.numberOfLeadingZeros(maxInGroup);
                    bitWidths[scaledInts_i / 8] = bitWidth;

                    String[] record = {String.valueOf(bitWidth)};
                    writer.writeRecord(record);
                }
            }
            writer.close();
        }
    }

    public static void main(String[] args) throws IOException {
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
            int modelCost = 0;
            long modelTime = 0;
            long modelDecodeTime = 0;

            for(int j = 0; j < time_of_repeat; j++) {
                for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                    List<String> chunkNumbers = numbers.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()));
                    if (chunkNumbers.size() == 1 || chunkNumbers.size() == 2)
                        continue;

                    int decimalMax = decimalPlaces.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()))
                            .stream().max(Integer::compare).orElse(0);

                    long[] scalingInt = scaleNumbers(chunkNumbers, decimalMax);
                    long startTime = System.nanoTime();
                    long[] scaledInts = sprintz(scalingInt);

                    int remainder = scaledInts.length % 8;
                    int paddingLength = (remainder == 0) ? 0 : 8 - remainder;

                    long[] paddedArray = new long[scaledInts.length + paddingLength];
                    System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                    int actual_length = paddedArray.length;
                    int[] bitWidths = new int[actual_length / 8];

                    for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += 8) {
                        long maxInGroup = 0;
                        for (int scaledInts_j = scaledInts_i; scaledInts_j < scaledInts_i + 8; scaledInts_j++) {
                            if (paddedArray[scaledInts_j] > maxInGroup) {
                                maxInGroup = paddedArray[scaledInts_j];
                            }
                        }

                        int bitWidth = 64 - Long.numberOfLeadingZeros(maxInGroup);
                        bitWidths[scaledInts_i / 8] = bitWidth;
                    }

                    byte[] compressedData = encodeBitPacking(paddedArray, bitWidths, 8);
                    int cur_cost = compressedData.length * 8;
                    long duration = System.nanoTime() - startTime;

                    long decodeStartTime = System.nanoTime();
                    long[] decodedData = decodeBitPacking(compressedData, bitWidths, 8, scaledInts.length);
                    long[] sprintzData = sprintzDecode(decodedData);
                    long decodeDuration = System.nanoTime() - decodeStartTime;
                    modelDecodeTime += decodeDuration;

                    if (j == 0) {
                        for (int k = 0; k < sprintzData.length; k++) {
                            if (sprintzData[k] != scalingInt[k]) {
                                System.out.println(k + "\t" + sprintzData[k]);
                            }
                        }
                    }

                    modelTime += duration;
                    modelCost += cur_cost;
                }
            }

            modelCost /= time_of_repeat;
            modelTime = modelTime / time_of_repeat;
            modelDecodeTime /= time_of_repeat;
            double model_ratio = (double) modelCost / (double) (numbers.size() * 64);
            double modelTime_throughput = (double) (numbers.size() * 8000) / (double) (modelTime);
            double modelDecodeTime_throughput = (double) (numbers.size() * 8000) / (double) (modelDecodeTime);

            String[] record = {
                    file.toString(),
                    "Sprintz",
                    String.valueOf(modelTime_throughput),
                    String.valueOf(modelDecodeTime_throughput),
                    String.valueOf(numbers.size()),
                    String.valueOf(modelCost),
                    String.valueOf(model_ratio)
            };
            writer.writeRecord(record);
            writer.close();
        }
    }

    @Test
    public void TestVarPackSize() throws IOException {
        System.out.println("\nPerformance Testing...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_SPRINTZ_vary_pack_size";
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
                    "Points",
                    "Compressed Size",
                    "Pack Size",
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

            for (int pack_size_exp = 3; pack_size_exp < 10; pack_size_exp++) {
                int pack_size = (int) Math.pow(2, pack_size_exp);
                int modelCost = 0;
                long modelTime = 0;

                for (int j = 0; j < time_of_repeat; j++) {
                    for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {
                        List<String> chunkNumbers = numbers.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()));
                        if (chunkNumbers.size() == 1 || chunkNumbers.size() == 2)
                            continue;

                        int decimalMax = decimalPlaces.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()))
                                .stream().max(Integer::compare).orElse(0);

                        long[] scaledInt = scaleNumbers(chunkNumbers, decimalMax);
                        long startTime = System.nanoTime();
                        long[] scaledInts = sprintz(scaledInt);

                        int remainder = scaledInts.length % pack_size;
                        int paddingLength = (remainder == 0) ? 0 : pack_size - remainder;

                        long[] paddedArray = new long[scaledInts.length + paddingLength];
                        System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                        int actual_length = paddedArray.length;
                        int[] bitWidths = new int[actual_length / pack_size];

                        for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += pack_size) {
                            long maxInGroup = 0;
                            for (int scaledInts_j = scaledInts_i; scaledInts_j < scaledInts_i + pack_size; scaledInts_j++) {
                                if (paddedArray[scaledInts_j] > maxInGroup) {
                                    maxInGroup = paddedArray[scaledInts_j];
                                }
                            }

                            int bitWidth = 64 - Long.numberOfLeadingZeros(maxInGroup);
                            bitWidths[scaledInts_i / pack_size] = bitWidth;
                        }

                        byte[] compressedData = encodeBitPacking(paddedArray, bitWidths, pack_size);
                        int cur_cost = compressedData.length * 8;
                        long duration = System.nanoTime() - startTime;

                        modelTime += duration;
                        modelCost += cur_cost;
                    }
                }

                modelCost /= time_of_repeat;
                modelTime = modelTime / time_of_repeat;
                double model_ratio = (double) modelCost / (double) (numbers.size() * 64);
                double modelTime_throughput = (double) (numbers.size() * 8000) / (double) (modelTime);

                String[] record = {
                        file.toString(),
                        "SPRINTZ",
                        String.valueOf(modelTime_throughput),
                        String.valueOf(numbers.size()),
                        String.valueOf(modelCost),
                        String.valueOf(pack_size),
                        String.valueOf(model_ratio)
                };
                writer.writeRecord(record);
            }
            writer.close();
        }
    }

    @Test
    public void TestVariableChunkSize() throws IOException {
        System.out.println("\nPerformance Testing with Variable Chunk Sizes...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_sprintz_vary_m";
        File outputDir = new File(outputDirstr);

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);

        int[] chunkSizes = {16 * 8, 32 * 8, 64 * 8, 128 * 8, 256 * 8, 512 * 8, 1024 * 8};

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;

            System.out.println("Processing " + file.getName() + " with variable chunk sizes...");
            String Output = outputDirstr + "/" + file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            String[] head = {
                    "m",
                    "Input Direction",
                    "Encoding Algorithm",
                    "Encoding Time",
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

            int time_of_repeat = 50;
            int decimalMax = decimalPlaces.stream().max(Integer::compare).orElse(0);

            int batchSize = 1024;
            List<long[]> batches = new ArrayList<>();

            for (int i = 0; i < numbers.size(); i += batchSize) {
                int end = Math.min(numbers.size(), i + batchSize);
                List<String> batch = numbers.subList(i, end);
                long[] scaledBatch = scaleNumbers(batch, decimalMax);
                batches.add(scaledBatch);
            }

            int totalLength = batches.stream().mapToInt(arr -> arr.length).sum();
            long[] scaledInts_all = new long[totalLength];

            int currentIndex = 0;
            for (long[] batch : batches) {
                System.arraycopy(batch, 0, scaledInts_all, currentIndex, batch.length);
                currentIndex += batch.length;
            }

            for (int chunkSize : chunkSizes) {
                System.out.println("Testing chunk size: " + chunkSize);

                for (int pack_size_exp = 3; pack_size_exp < 4; pack_size_exp++) {
                    int pack_size = (int) Math.pow(2, pack_size_exp);
                    int modelCost = 0;
                    long modelTime = 0;

                    for (int j = 0; j < time_of_repeat; j++) {
                        for (int i = 0; i < numbers.size(); i += chunkSize) {
                            int end = Math.min(i + chunkSize, numbers.size());
                            long[] scaledInt = new long[end - i];
                            if (end - i >= 0) System.arraycopy(scaledInts_all, i, scaledInt, 0, end - i);

                            long startTime = System.nanoTime();
                            long[] scaledInts = sprintz(scaledInt);
                            int remainder = scaledInts.length % pack_size;
                            int paddingLength = (remainder == 0) ? 0 : pack_size - remainder;

                            long[] paddedArray = new long[scaledInts.length + paddingLength];
                            System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                            int actual_length = paddedArray.length;
                            int[] bitWidths = new int[actual_length / pack_size];

                            for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += pack_size) {
                                long maxInGroup = 0;
                                for (int scaledInts_j = scaledInts_i; scaledInts_j < scaledInts_i + pack_size; scaledInts_j++) {
                                    if (paddedArray[scaledInts_j] > maxInGroup) {
                                        maxInGroup = paddedArray[scaledInts_j];
                                    }
                                }

                                int bitWidth = 64 - Long.numberOfLeadingZeros(maxInGroup);
                                bitWidths[scaledInts_i / pack_size] = bitWidth;
                            }

                            byte[] compressedData = encodeBitPacking(paddedArray, bitWidths, pack_size);
                            int cur_cost = compressedData.length * 8;
                            long duration = System.nanoTime() - startTime;

                            modelTime += duration;
                            modelCost += cur_cost;
                        }
                    }

                    modelCost /= time_of_repeat;
                    modelTime = modelTime / time_of_repeat;
                    double model_ratio = (double) modelCost / (double) (numbers.size() * 64);
                    double modelTime_throughput = (double) (numbers.size() * 8000) / (double) (modelTime);

                    String[] record = {
                            String.valueOf(chunkSize / 8),
                            file.toString(),
                            "BP",
                            String.valueOf(modelTime_throughput),
                            String.valueOf(numbers.size()),
                            String.valueOf(modelCost),
                            String.valueOf(pack_size),
                            String.valueOf(model_ratio)
                    };
                    writer.writeRecord(record);
                }
            }
            writer.close();
        }
    }
}