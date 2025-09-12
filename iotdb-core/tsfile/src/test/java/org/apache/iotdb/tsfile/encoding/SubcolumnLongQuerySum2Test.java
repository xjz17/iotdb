package org.apache.iotdb.tsfile.encoding;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;

public class SubcolumnLongQuerySum2Test {

    public static void int2Bytes(int integer, int encode_pos, byte[] cur_byte) {
        cur_byte[encode_pos] = (byte) (integer >> 24);
        cur_byte[encode_pos + 1] = (byte) (integer >> 16);
        cur_byte[encode_pos + 2] = (byte) (integer >> 8);
        cur_byte[encode_pos + 3] = (byte) (integer);
    }

    public static void intByte2Bytes(int integer, int encode_pos, byte[] cur_byte) {
        cur_byte[encode_pos] = (byte) (integer);
    }

    public static void long2Bytes(long integer, int encode_pos, byte[] cur_byte) {
        cur_byte[encode_pos] = (byte) (integer >> 56);
        cur_byte[encode_pos + 1] = (byte) (integer >> 48);
        cur_byte[encode_pos + 2] = (byte) (integer >> 40);
        cur_byte[encode_pos + 3] = (byte) (integer >> 32);
        cur_byte[encode_pos + 4] = (byte) (integer >> 24);
        cur_byte[encode_pos + 5] = (byte) (integer >> 16);
        cur_byte[encode_pos + 6] = (byte) (integer >> 8);
        cur_byte[encode_pos + 7] = (byte) (integer);
    }

    public static int bytes2Integer(byte[] encoded, int start, int num) {
        int value = 0;

        for (int i = 0; i < num; i++) {
            value <<= 8;
            int b = encoded[i + start] & 0xFF;
            value |= b;
        }
        return value;
    }

    public static long bytes2Long(byte[] encoded, int start, int num) {
        long value = 0;

        for (int i = 0; i < num; i++) {
            value <<= 8;
            int b = encoded[i + start] & 0xFF;
            value |= b;
        }
        return value;
    }

    public static void Query(byte[] encoded_result, long target) {

        int encode_pos = 0;

        int data_length = ((encoded_result[encode_pos] & 0xFF) << 24) | ((encoded_result[encode_pos + 1] & 0xFF) << 16)
                |
                ((encoded_result[encode_pos + 2] & 0xFF) << 8) | (encoded_result[encode_pos + 3] & 0xFF);
        encode_pos += 4;

        int block_size = ((encoded_result[encode_pos] & 0xFF) << 24) | ((encoded_result[encode_pos + 1] & 0xFF) << 16) |
                ((encoded_result[encode_pos + 2] & 0xFF) << 8) | (encoded_result[encode_pos + 3] & 0xFF);
        encode_pos += 4;

        int num_blocks = data_length / block_size;

        // 查询结果
        long[] result = new long[data_length];
        int[] result_length = new int[1];

        for (int i = 0; i < num_blocks; i++) {
            encode_pos = BlockQuerySum(encoded_result, i, block_size, block_size, encode_pos, target, result,
                    result_length);
        }

        int remainder = data_length % block_size;

        if (remainder <= 3) {
            for (int i = 0; i < remainder; i++) {
                long value = bytes2Long(encoded_result, encode_pos, 8);
                // int value = ((encoded_result[encode_pos] & 0xFF) << 24) |
                //         ((encoded_result[encode_pos + 1] & 0xFF) << 16) |
                //         ((encoded_result[encode_pos + 2] & 0xFF) << 8) | (encoded_result[encode_pos + 3] & 0xFF);
                encode_pos += 8;
                result[result_length[0]] = value;
                result_length[0]++;
            }
        } else {
            encode_pos = BlockQuerySum(encoded_result, num_blocks, block_size, remainder, encode_pos, target,
                    result, result_length);
        }

        // for (int i = 0; i < result_length[0]; i++) {
        // System.out.print(result[i] + " ");
        // }
        // System.out.println();

    }

    public static int BlockQuerySum(byte[] encoded_result, int block_index, int block_size, int remainder,
            int encode_pos, long target, long[] result, int[] result_length) {
        long[] min_delta = new long[3];

        // min_delta[0] = ((encoded_result[encode_pos] & 0xFF) << 24) | ((encoded_result[encode_pos + 1] & 0xFF) << 16) |
        //         ((encoded_result[encode_pos + 2] & 0xFF) << 8) | (encoded_result[encode_pos + 3] & 0xFF);
        // encode_pos += 4;

        min_delta[0] = bytes2Long(encoded_result, encode_pos, 8);
        encode_pos += 8;

        int m = encoded_result[encode_pos];
        encode_pos += 1;

        target -= min_delta[0];

        int[] candidate_indices = new int[remainder];
        int candidate_length = 0;
        for (int i = 0; i < remainder; i++) {
            candidate_indices[i] = i;
            candidate_length++;
        }

        if (m == 0) {
            if (target == 0) {
                result[result_length[0]] = min_delta[0] * remainder;
                result_length[0]++;
            }
            return encode_pos;
        }

        int bw = SubcolumnTest.bitWidth(block_size);

        int beta = encoded_result[encode_pos];
        encode_pos += 1;

        int l = (m + beta - 1) / beta;

        int[] bitWidthList = new int[l];

        encode_pos = SubcolumnLongTest.decodeBitPacking(encoded_result, encode_pos, 8, l, bitWidthList);

        long[][] subcolumnList = new long[l][remainder];

        int[] encodingType = new int[l];

        encode_pos = SubcolumnLongTest.decodeBitPacking(encoded_result, encode_pos, 1, l, encodingType);

        for (int i = l - 1; i >= 0; i--) {
            int type = encodingType[i];
            if (type == 0) {

                if (target < 0) {
                    encode_pos *= 8;
                    encode_pos += bitWidthList[i] * remainder;
                    encode_pos = (encode_pos + 7) / 8;
                    continue;
                }

                encode_pos *= 8;

                int new_length = 0;
                for (int j = 0; j < candidate_length; j++) {
                    int index = candidate_indices[j];

                    subcolumnList[i][index] = SubcolumnLongTest.bytesToInt(encoded_result,
                            encode_pos + index * bitWidthList[i], bitWidthList[i]);
                    long value = (target >> (i * beta)) & ((1 << beta) - 1);

                    if (subcolumnList[i][index] == value) {
                        candidate_indices[new_length] = index;
                        new_length++;
                    }
                }

                candidate_length = new_length;

                encode_pos += remainder * bitWidthList[i];
                encode_pos = (encode_pos + 7) / 8;

            } else {
                int index = ((encoded_result[encode_pos] & 0xFF) << 8) | (encoded_result[encode_pos + 1] & 0xFF);

                encode_pos += 2;

                if (target < 0) {
                    encode_pos *= 8;
                    encode_pos += bw * index;
                    encode_pos = (encode_pos + 7) / 8;

                    encode_pos *= 8;
                    encode_pos += bitWidthList[i] * index;
                    encode_pos = (encode_pos + 7) / 8;
                    continue;
                }

                int[] run_length = new int[index];
                int[] rle_values = new int[index];

                encode_pos = SubcolumnTest.decodeBitPacking(encoded_result, encode_pos, bw, index, run_length);
                encode_pos = SubcolumnTest.decodeBitPacking(encoded_result, encode_pos, bitWidthList[i], index,
                        rle_values);

                int new_length = 0;
                int rleIndex = 0;
                int currentPos = 0;
                long value = (target >> (i * beta)) & ((1 << beta) - 1);

                for (int j = 0; j < candidate_length; j++) {
                    int index_candidate = candidate_indices[j];

                    while (rleIndex < index && currentPos + run_length[rleIndex] <= index_candidate) {
                        currentPos += run_length[rleIndex];
                        rleIndex++;
                    }

                    if (rleIndex < index) {
                        if (rle_values[rleIndex] == value) {
                            candidate_indices[new_length] = index_candidate;
                            new_length++;
                        }
                    }
                }

                candidate_length = new_length;
            }
        }

        result[result_length[0]] = (min_delta[0] + target) * candidate_length;
        result_length[0]++;

        return encode_pos;
    }

    public static int getDecimalPrecision(String str) {
        // 查找小数点的位置
        int decimalIndex = str.indexOf(".");

        // 如果没有小数点，精度为0
        if (decimalIndex == -1) {
            return 0;
        }

        // 获取小数点后的部分并返回其长度
        return str.substring(decimalIndex + 1).length();
    }

    public static String extractFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        File file = new File(path);
        String fileName = file.getName();

        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex == -1 || dotIndex == 0) {
            return fileName;
        }

        return fileName.substring(0, dotIndex);
    }

    @Test
    public void testQuery() throws IOException {
        String parent_dir = "D:/github/xjz17/subcolumn/";

        String input_parent_dir = parent_dir + "dataset/";
        
        String output_parent_dir = "D:/encoding-subcolumn/result/query_vs_block/";
        // String output_parent_dir = parent_dir + "result/query_vs_block/";

        // int[] block_size_list = { 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192 };
        int[] block_size_list = { 512 };

        HashMap<String, Integer> queryRange = new HashMap<>();
        queryRange.put("Bird-migration", 2600000);
        queryRange.put("Bitcoin-price", 170000000);
        queryRange.put("City-temp", 700);
        queryRange.put("Dewpoint-temp", 9600);
        queryRange.put("IR-bio-temp", -200);
        queryRange.put("PM10-dust", 2000);
        queryRange.put("Stocks-DE", 90000);
        queryRange.put("Stocks-UK", 30000);
        queryRange.put("Stocks-USA", 6000);
        queryRange.put("Wind-Speed", 60);
        queryRange.put("Wine-Tasting", 10);
        queryRange.put("Arade4", 10000000);
        queryRange.put("EPM-Education", 200);
        queryRange.put("POI-lat", 0);
        queryRange.put("Gov10", 100000);

        int repeatTime = 200;

        // repeatTime = 1;

        for (int block_size : block_size_list) {
            String outputPath = output_parent_dir + "subcolumn_long_query_sum2_block_" + block_size + ".csv";

            CsvWriter writer = new CsvWriter(outputPath, ',', StandardCharsets.UTF_8);
            writer.setRecordDelimiter('\n');

            String[] head = {
                    "Dataset",
                    "Encoding Algorithm",
                    "Encoding Time",
                    "Decoding Time",
                    "Points",
                    "Compressed Size",
                    "Compression Ratio"
            };
            writer.writeRecord(head);

            File directory = new File(input_parent_dir);
            // File[] csvFiles = directory.listFiles();
            File[] csvFiles = directory.listFiles((dir, name) -> name.endsWith(".csv"));

            for (File file : csvFiles) {
                String datasetName = extractFileName(file.toString());
                System.out.println(datasetName);

                InputStream inputStream = Files.newInputStream(file.toPath());

                CsvReader loader = new CsvReader(inputStream, StandardCharsets.UTF_8);
                ArrayList<Float> data1 = new ArrayList<>();

                int max_decimal = 0;
                while (loader.readRecord()) {
                    String f_str = loader.getValues()[0];
                    if (f_str.isEmpty()) {
                        continue;
                    }
                    int cur_decimal = getDecimalPrecision(f_str);
                    if (cur_decimal > max_decimal)
                        max_decimal = cur_decimal;
                    data1.add(Float.valueOf(f_str));
                }
                inputStream.close();
                long[] data2_arr = new long[data1.size()];
                long max_mul = (long) Math.pow(10, max_decimal);
                for (int i = 0; i < data1.size(); i++) {
                    data2_arr[i] = (long) (data1.get(i) * max_mul);
                }

                System.out.println(max_decimal);
                byte[] encoded_result = new byte[data2_arr.length * 8];

                long encodeTime = 0;
                long decodeTime = 0;
                double ratio = 0;
                double compressed_size = 0;

                int length = 0;

                long s = System.nanoTime();
                for (int repeat = 0; repeat < repeatTime; repeat++) {
                    length = SubcolumnLongTest.Encoder(data2_arr, block_size, encoded_result);
                }

                long e = System.nanoTime();
                encodeTime += ((e - s) / repeatTime);
                compressed_size += length;
                
                double ratioTmp;

                ratioTmp = compressed_size / (double) (data1.size() * Long.BYTES);

                System.out.println("Query");

                s = System.nanoTime();

                for (int repeat = 0; repeat < repeatTime; repeat++) {
                    Query(encoded_result, queryRange.get(datasetName));
                }

                e = System.nanoTime();
                decodeTime += ((e - s) / repeatTime);

                String[] record = {
                        datasetName,
                        "Sub-columns",
                        String.valueOf(encodeTime),
                        String.valueOf(decodeTime),
                        String.valueOf(data1.size()),
                        String.valueOf(compressed_size),
                        String.valueOf(ratio)
                };
                writer.writeRecord(record);

                System.out.println("block_size: " + block_size);

                System.out.println(ratio);
            }

            writer.close();
        }
    }

    @Test
    public void testQueryBeta() throws IOException {
        String parent_dir = "D:/github/xjz17/subcolumn/";

        String input_parent_dir = parent_dir + "dataset/";
        
        String output_parent_dir = "D:/encoding-subcolumn/result/query_vs_beta/";
        // String output_parent_dir = parent_dir + "result/query_vs_beta/";

        int[] beta_list = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
            24, 25, 26, 27, 28, 29, 30, 31 };

        int block_size = 512;

        HashMap<String, Integer> queryRange = new HashMap<>();
        
        queryRange.put("Bird-migration", 2600000);
        queryRange.put("Bitcoin-price", 170000000);
        queryRange.put("City-temp", 700);
        queryRange.put("Dewpoint-temp", 9600);
        queryRange.put("IR-bio-temp", -200);
        queryRange.put("PM10-dust", 2000);
        queryRange.put("Stocks-DE", 90000);
        queryRange.put("Stocks-UK", 30000);
        queryRange.put("Stocks-USA", 6000);
        queryRange.put("Wind-Speed", 60);
        queryRange.put("Wine-Tasting", 10);
        queryRange.put("Arade4", 10000000);
        queryRange.put("EPM-Education", 200);
        queryRange.put("POI-lat", 0);
        queryRange.put("Gov10", 100000);

        int repeatTime = 200;

        // repeatTime = 1;

        List<String> integerDatasets = new ArrayList<>();
        integerDatasets.add("Wine-Tasting");

        for (int beta : beta_list) {
            String outputPath = output_parent_dir + "subcolumn_query_sum2_beta_" + beta + ".csv";

            CsvWriter writer = new CsvWriter(outputPath, ',', StandardCharsets.UTF_8);
            writer.setRecordDelimiter('\n');

            String[] head = {
                    "Dataset",
                    "Encoding Algorithm",
                    "Encoding Time",
                    "Decoding Time",
                    "Points",
                    "Compressed Size",
                    "Compression Ratio"
            };
            writer.writeRecord(head);

            File directory = new File(input_parent_dir);
            // File[] csvFiles = directory.listFiles();
            File[] csvFiles = directory.listFiles((dir, name) -> name.endsWith(".csv"));

            for (File file : csvFiles) {
                String datasetName = extractFileName(file.toString());
                System.out.println(datasetName);

                InputStream inputStream = Files.newInputStream(file.toPath());

                CsvReader loader = new CsvReader(inputStream, StandardCharsets.UTF_8);
                ArrayList<Float> data1 = new ArrayList<>();

                int max_decimal = 0;
                while (loader.readRecord()) {
                    String f_str = loader.getValues()[0];
                    if (f_str.isEmpty()) {
                        continue;
                    }
                    int cur_decimal = getDecimalPrecision(f_str);
                    if (cur_decimal > max_decimal)
                        max_decimal = cur_decimal;
                    data1.add(Float.valueOf(f_str));
                }
                inputStream.close();
                long[] data2_arr = new long[data1.size()];
                long max_mul = (long) Math.pow(10, max_decimal);
                for (int i = 0; i < data1.size(); i++) {
                    data2_arr[i] = (long) (data1.get(i) * max_mul);
                }

                System.out.println(max_decimal);
                byte[] encoded_result = new byte[data2_arr.length * 8];

                long encodeTime = 0;
                long decodeTime = 0;
                double ratio = 0;
                double compressed_size = 0;

                int length = 0;

                long s = System.nanoTime();
                for (int repeat = 0; repeat < repeatTime; repeat++) {
                    length = SubcolumnLongBetaTest.Encoder(data2_arr, block_size, encoded_result, beta);
                }

                long e = System.nanoTime();
                encodeTime += ((e - s) / repeatTime);
                compressed_size += length;
                
                double ratioTmp;

                ratioTmp = compressed_size / (double) (data1.size() * Long.BYTES);

                ratio = ratioTmp;

                System.out.println("Query");

                s = System.nanoTime();

                for (int repeat = 0; repeat < repeatTime; repeat++) {
                    Query(encoded_result, queryRange.get(datasetName));
                }

                e = System.nanoTime();
                decodeTime += ((e - s) / repeatTime);

                String[] record = {
                        datasetName,
                        "Sub-columns",
                        String.valueOf(encodeTime),
                        String.valueOf(decodeTime),
                        String.valueOf(data1.size()),
                        String.valueOf(compressed_size),
                        String.valueOf(ratio)
                };
                writer.writeRecord(record);

                System.out.println("beta: " + beta);

                System.out.println(ratio);
            }

            writer.close();
        }
    }
}
