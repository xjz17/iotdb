package org.apache.iotdb.tsfile.encoding;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FBitpacking512 {
//    private static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data","test.csv","POI-lat.csv",
//            "POI-lon.csv","Air-sensor.csv","Basel-temp.csv");
    static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data", "test.csv","POI-lat.csv","POI-lon.csv","Basel-wind.csv","Basel-temp.csv","Air-sensor.csv");
    private static final int CHUNK_SIZE = 1024;

    public static int getBitWith(int num) {
        if (num == 0)
            return 1;
        else
            return 32 - Integer.numberOfLeadingZeros(num);
    }

    public static int getCount(long long1, int mask) {
        return ((int) (long1 & mask));
    }

    public static int getUniqueValue(long long1, int left_shift) {
        return ((int) ((long1) >> left_shift));
    }

    public static void int2Bytes(int integer, int encode_pos, byte[] cur_byte) {
        cur_byte[encode_pos] = (byte) (integer >> 24);
        cur_byte[encode_pos + 1] = (byte) (integer >> 16);
        cur_byte[encode_pos + 2] = (byte) (integer >> 8);
        cur_byte[encode_pos + 3] = (byte) (integer);
    }

    public static void intByte2Bytes(int integer, int encode_pos, byte[] cur_byte) {
        cur_byte[encode_pos] = (byte) (integer);
    }

    private static void long2intBytes(long integer, int encode_pos, byte[] cur_byte) {
        cur_byte[encode_pos] = (byte) (integer >> 24);
        cur_byte[encode_pos + 1] = (byte) (integer >> 16);
        cur_byte[encode_pos + 2] = (byte) (integer >> 8);
        cur_byte[encode_pos + 3] = (byte) (integer);
    }

    public static int bytes2Integer(byte[] encoded, int start, int num) {
        int value = 0;
        if (num > 4) {
            System.out.println("bytes2Integer error");
            return 0;
        }
        for (int i = 0; i < num; i++) {
            value <<= 8;
            int b = encoded[i + start] & 0xFF;
            value |= b;
        }
        return value;
    }

    private static long bytesLong2Integer(byte[] encoded, int decode_pos) {
        long value = 0;
        for (int i = 0; i < 4; i++) {
            value <<= 8;
            int b = encoded[i + decode_pos] & 0xFF;
            value |= b;
        }
        return value;
    }

    public static void pack8Values(ArrayList<Integer> values, int offset, int width, int encode_pos,
                                   byte[] encoded_result) {
        int bufIdx = 0;
        int valueIdx = offset;
        // remaining bits for the current unfinished Integer
        int leftBit = 0;

        while (valueIdx < 8 + offset) {
            // buffer is used for saving 32 bits as a part of result
            int buffer = 0;
            // remaining size of bits in the 'buffer'
            int leftSize = 32;

            // encode the left bits of current Integer to 'buffer'
            if (leftBit > 0) {
                buffer |= (values.get(valueIdx) << (32 - leftBit));
                leftSize -= leftBit;
                leftBit = 0;
                valueIdx++;
            }

            while (leftSize >= width && valueIdx < 8 + offset) {
                // encode one Integer to the 'buffer'
                buffer |= (values.get(valueIdx) << (leftSize - width));
                leftSize -= width;
                valueIdx++;
            }
            // If the remaining space of the buffer can not save the bits for one Integer,
            if (leftSize > 0 && valueIdx < 8 + offset) {
                // put the first 'leftSize' bits of the Integer into remaining space of the
                // buffer
                buffer |= (values.get(valueIdx) >>> (width - leftSize));
                leftBit = width - leftSize;
            }

            // put the buffer into the final result
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
        // total bits which have read from 'buf' to 'buffer'. i.e.,
        // number of available bits to be decoded.
        int totalBits = 0;
        int valueIdx = 0;

        while (valueIdx < 8) {
            // If current available bits are not enough to decode one Integer,
            // then add next byte from buf to 'buffer' until totalBits >= width
            while (totalBits < width) {
                buffer = (buffer << 8) | (encoded[byteIdx] & 0xFF);
                byteIdx++;
                totalBits += 8;
            }

            // If current available bits are enough to decode one Integer,
            // then decode one Integer one by one until left bits in 'buffer' is
            // not enough to decode one Integer.
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

    private static int[] scaleNumbers(List<String> numbers, int decimalMax) {
        // 1. 预先计算缩放因子
        BigDecimal scale = BigDecimal.TEN.pow(decimalMax);
        int size = numbers.size();
        int[] result = new int[size];

        if (size == 0) {
            return result;
        }

        // 2. 单次遍历完成所有转换和最小值查找
        BigDecimal min = null;
        BigDecimal[] scaledValues = new BigDecimal[size];

        for (int i = 0; i < size; i++) {
            BigDecimal val = new BigDecimal(numbers.get(i)).multiply(scale);
            scaledValues[i] = val;
            if (min == null || val.compareTo(min) < 0) {
                min = val;
            }
        }

        // 3. 处理第一个元素
        BigDecimal first = scaledValues[0].subtract(min);
        result[0] = first.toBigInteger().intValue();

        // 4. 处理后续元素（差分+ZigZag）
        for (int i = 1; i < size; i++) {
            BigDecimal current = scaledValues[i].subtract(min);
            result[i]=current.toBigInteger().intValue();
        }

        return result;
    }

    public static void main(String[] args) throws IOException {
        // 示例数据（实际应替换为真实时间序列）
        System.out.println("\nPerformance Testing...");
//        String csvFilePath = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/processed_data.csv";
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_BP";
        File outputDir = new File(outputDirstr);

//        RLDecisionModel trainedModel = trainModel(20, csvFilePath);
        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);
        for (File file : Objects.requireNonNull(dir.listFiles())) {

            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;
//            if(!file.getName().equals("Stocks-DE.csv")) continue;
            System.out.println(file.getName());
            String Output = outputDirstr+"/"+file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            String[] head = {
                    "Input Direction",
                    "Encoding Algorithm",
                    "Encoding Time",
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
            int time_of_repeat = 50;
//            System.out.println(numbers.size());


            // 方法：强化学习
//            long modelStart =  System.nanoTime();
            int modelCost = 0;
            long modelTime = 0;
            for(int j=0;j<time_of_repeat;j++){
                int totalCost = 0;
                for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {

                    List<String> chunkNumbers = numbers.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()));
                    if(chunkNumbers.size()==1 || chunkNumbers.size()==2)
                        continue;

                    int decimalMax = decimalPlaces.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()))
                            .stream().max(Integer::compare).orElse(0);


                    int[] scaledInts = scaleNumbers(chunkNumbers, decimalMax);

                    long startTime = System.nanoTime();
                    int remainder = scaledInts.length % 8;
                    int paddingLength = (remainder == 0) ? 0 : 8 - remainder;

                    // 创建新数组，长度补齐为8的倍数
                    int[] paddedArray = new int[scaledInts.length + paddingLength];
                    System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                    int actual_length = paddedArray.length;
                    int[] bitWidths = new int[actual_length / 8]; // 存储每8个值的位宽结果

                    for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += 8) {
                        // 1. 找出当前8个元素中的最大值
                        int maxInGroup = 0;
                        for (int scaledInts_j = scaledInts_i; scaledInts_j < scaledInts_i + 8; scaledInts_j++) {
                            if (paddedArray[scaledInts_j] > maxInGroup) {
                                maxInGroup = paddedArray[scaledInts_j];
                            }
                        }

                        // 2. 计算该最大值的去头零位宽
                        int bitWidth = 32 - Integer.numberOfLeadingZeros(maxInGroup);

                        // 3. 存储结果
                        bitWidths[scaledInts_i / 8] = bitWidth;
                    }

                    int fixed_block = CHUNK_SIZE/40;
                    int cur_cost = computeMinPackingCost(bitWidths,fixed_block,8);

//                    PackingResult result = packOctads(bitWidths, model, null); // 禁用决策跟踪
                    long duration = System.nanoTime() - startTime;
                    modelTime += (duration);
                    modelCost +=  cur_cost;
//                    if(i==0)
//                        for (int episode = 0; episode < 10; episode++) {
//                            trainEpisode(scaledInts, episode);
//                        }
//                    List<Integer> optimalK = predictOptimalK(scaledInts);
//                    System.out.println("Optimal k sequence: " + optimalK);
                }

            }
            modelCost /=time_of_repeat;
            modelTime = (modelTime)/time_of_repeat;
            double model_ratio = (double) modelCost / (double) (numbers.size()*64);
            double modelTime_throughput = (double)(numbers.size()*8000)/ (double) (modelTime);
            String[] record = {
                    file.toString(),
                    "BP",
                    String.valueOf(modelTime_throughput),
                    String.valueOf(numbers.size()),
                    String.valueOf(modelCost),
                    String.valueOf(model_ratio)
            };
            writer.writeRecord(record);
            writer.close();
//            break;
        }

    }

    @Test
    public void TestVarPackSize() throws IOException {
        // 示例数据（实际应替换为真实时间序列）
        System.out.println("\nPerformance Testing...");
//        String csvFilePath = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/processed_data.csv";
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_BP_vary_pack_size";
        File outputDir = new File(outputDirstr);

//        RLDecisionModel trainedModel = trainModel(20, csvFilePath);
        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);
        for (File file : Objects.requireNonNull(dir.listFiles())) {

            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;
//            if(!file.getName().equals("Stocks-DE.csv")) continue;
            System.out.println(file.getName());
            String Output = outputDirstr+"/"+file.getName();
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
            int time_of_repeat = 50;
//            System.out.println(numbers.size());

            for(int pack_size_exp = 3; pack_size_exp < 10; pack_size_exp++) {
                int pack_size = (int) Math.pow(2, pack_size_exp);
                // 方法：强化学习
                int modelCost = 0;
                long modelTime = 0;
                for (int j = 0; j < time_of_repeat; j++) {
                    int totalCost = 0;
                    for (int i = 0; i < numbers.size(); i += CHUNK_SIZE) {

                        List<String> chunkNumbers = numbers.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()));
                        if (chunkNumbers.size() == 1 || chunkNumbers.size() == 2)
                            continue;

                        int decimalMax = decimalPlaces.subList(i, Math.min(i + CHUNK_SIZE, numbers.size()))
                                .stream().max(Integer::compare).orElse(0);


                        int[] scaledInts = scaleNumbers(chunkNumbers, decimalMax);

                        long startTime = System.nanoTime();
                        int remainder = scaledInts.length % pack_size;
                        int paddingLength = (remainder == 0) ? 0 : pack_size - remainder;

                        // 创建新数组，长度补齐为8的倍数
                        int[] paddedArray = new int[scaledInts.length + paddingLength];
                        System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                        int actual_length = paddedArray.length;
                        int[] bitWidths = new int[actual_length / pack_size]; // 存储每8个值的位宽结果

//                        int cost_bits = 0;
                        for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += pack_size) {
                            // 1. 找出当前8个元素中的最大值
                            int maxInGroup = 0;
                            for (int scaledInts_j = scaledInts_i; scaledInts_j < scaledInts_i + pack_size; scaledInts_j++) {
                                if (paddedArray[scaledInts_j] > maxInGroup) {
                                    maxInGroup = paddedArray[scaledInts_j];
                                }
                            }

                            // 2. 计算该最大值的去头零位宽
                            int bitWidth = 32 - Integer.numberOfLeadingZeros(maxInGroup);

                            // 3. 存储结果
                            bitWidths[scaledInts_i / pack_size] = bitWidth;
//                            cost_bits += (bitWidth*pack_size);
                        }

                        int fixed_block = CHUNK_SIZE / pack_size;
                        byte[] compressedData = encodeBitPacking(paddedArray, bitWidths, pack_size);
                        int cur_cost = compressedData.length * 8; // 转换为bit数
//                        int cur_cost = computeMinPackingCost(bitWidths, fixed_block, pack_size);

//                    PackingResult result = packOctads(bitWidths, model, null); // 禁用决策跟踪
                        long duration = System.nanoTime() - startTime;
                        modelTime += (duration);
                        modelCost += cur_cost;
//                    if(i==0)
//                        for (int episode = 0; episode < 10; episode++) {
//                            trainEpisode(scaledInts, episode);
//                        }
//                    List<Integer> optimalK = predictOptimalK(scaledInts);
//                    System.out.println("Optimal k sequence: " + optimalK);
                    }

                }
                modelCost /= time_of_repeat;
                modelTime = (modelTime) / time_of_repeat;
                double model_ratio = (double) modelCost / (double) (numbers.size() * 64);
                double modelTime_throughput = (double) (numbers.size() * 8000) / (double) (modelTime);
                String[] record = {
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
            writer.close();
//            break;
        }

    }
    public static byte[] encodeBitPacking(int[] paddedArray, int[] bitWidths, int pack_size) {
        List<Byte> result = new ArrayList<>();

        // 1. 对bitWidths进行RLE编码
//        List<Byte> rleEncoded = encodeRLE(bitWidths);

        // 2. 将RLE编码的bitWidths写入结果
        // 首先写入RLE数据的长度（4字节）
//        int rleLength = rleEncoded.size();
//        result.add((byte) (rleLength >> 24));
//        result.add((byte) (rleLength >> 16));
//        result.add((byte) (rleLength >> 8));
//        result.add((byte) rleLength);

        // 写入RLE数据
//        result.addAll(rleEncoded);

        // 3. 对paddedArray进行bit-packing
        int totalGroups = bitWidths.length;

        // 计算bit-packed数据的总字节数 - 修正计算方式
//        int totalBitPackedBytes = (cost_bits+7)/8;
//        for (int i = 0; i < totalGroups; i++) {
//            // 每组需要 ceil(8 * bitWidth / 8) = bitWidth 字节
//            totalBitPackedBytes += bitWidths[i];
//        }

        // 确保数组足够大，添加一些额外空间以防万一
        int max_bit_width = 0;

        for (int i = 0; i < totalGroups; i++) {
            if (bitWidths[i] > max_bit_width) {
                max_bit_width = bitWidths[i];
            }
        }
        int totalBitPackedBytes = (max_bit_width*pack_size*totalGroups+7)/8;
        byte[] bitPackedData = new byte[totalBitPackedBytes +32];
        bitPackedData[0] = (byte) max_bit_width;
        int encodePos = 1;
        // 对每组数据进行bit-packing
        for (int group = 0; group < totalGroups; group++) {
            int startIndex = group * pack_size;
            ArrayList<Integer> groupData = new ArrayList<>();
            for (int i = 0; i < pack_size; i++) {
                if (startIndex + i < paddedArray.length) {
                    groupData.add(paddedArray[startIndex + i]);
                } else {
                    groupData.add(0); // 用0填充不足的部分
                }
            }

//            bitPackedData[encodePos++] = (byte) bitWidths[group];
            encodePos = bitPacking(groupData, 0,max_bit_width , encodePos, bitPackedData);
        }


        // 4. 将bit-packed数据写入结果（只写入实际使用的部分）
        for (int i = 0; i < encodePos; i++) {
            result.add(bitPackedData[i]);
        }

        // 转换为byte数组返回
        byte[] finalResult = new byte[result.size()];
        for (int i = 0; i < result.size(); i++) {
            finalResult[i] = result.get(i);
        }

        return finalResult;
    }
    public static int computeMinPackingCost(int[] bitWidths, int fixed_pack, int pack_size) {
        int blocksize= bitWidths.length;
//        int minCost = Integer.MAX_VALUE;

        // Try all possible pack sizes from 1 to CHUNK_SIZE
//        for (int p = 1; p <= blocksize; p++) {
        int totalCost = 0;
        int numPacks = (int) Math.ceil((double) blocksize / fixed_pack);

        // Calculate cost for each pack
        for (int pack = 0; pack < numPacks; pack++) {
            int start = pack * fixed_pack;
            int end = Math.min(start + fixed_pack, blocksize);


            // Find max bitWidth in current pack
            int maxBitWidth = 0;
            for (int i = start; i < end; i++) {
                if (bitWidths[i] > maxBitWidth) {
                    maxBitWidth = bitWidths[i];
                }
            }

            // Add to cost: 8 * p * maxBitWidth
            totalCost += pack_size * (end-start) * maxBitWidth;
        }

        // Add the chunk cost: 5 * CHUNK_SIZE / p
        totalCost += 5 * blocksize / fixed_pack;

//            // Update minimum cost
//            if (totalCost < minCost) {
//                minCost = totalCost;
//            }
//        }
        return totalCost;
    }
//    public static void main(String[] args) {
//        // Example 1
//        int[] bitWidths1 = {4, 8, 12, 16, 20, 24, 28, 32};
//        System.out.println("Example 1 cost: " + computeMinPackingCost(bitWidths1));
//
//        // Example 2
//        int[] bitWidths2 = {5, 5, 5, 5, 5, 5, 5, 5};
//        System.out.println("Example 2 cost: " + computeMinPackingCost(bitWidths2));
//
//        // Example 3 (random)
//        int[] bitWidths3 = {3, 7, 2, 9, 5, 8, 1, 6};
//        System.out.println("Example 3 cost: " + computeMinPackingCost(bitWidths3));
//    }
}