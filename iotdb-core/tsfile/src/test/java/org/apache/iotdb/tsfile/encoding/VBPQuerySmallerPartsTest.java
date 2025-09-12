package org.apache.iotdb.tsfile.encoding;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

public class VBPQuerySmallerPartsTest {

    public static void int2Bytes(int integer, int encode_pos, byte[] cur_byte) {
        cur_byte[encode_pos] = (byte) (integer >> 24);
        cur_byte[encode_pos + 1] = (byte) (integer >> 16);
        cur_byte[encode_pos + 2] = (byte) (integer >> 8);
        cur_byte[encode_pos + 3] = (byte) (integer);
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

    public static int bitWidth(int value) {
        return 32 - Integer.numberOfLeadingZeros(value);
    }

    public static int bitWidth(long value) {
        return 64 - Long.numberOfLeadingZeros(value);
    }

    public static int BlockEncoder(long[] data, int block_index, int block_size, int remainder,
            int encode_pos, ArrayList<VBPIndexLong> indexList, byte[] encoded_result) {

        long[] block_data = new long[remainder];
        System.arraycopy(data, block_index * block_size, block_data, 0, remainder);

        long min_value = Long.MAX_VALUE;
        long max_value = Long.MIN_VALUE;
        for (long value : block_data) {
            if (value < min_value) {
                min_value = value;
            }
            if (value > max_value) {
                max_value = value;
            }
        }

        for (int i = 0; i < remainder; i++) {
            block_data[i] -= min_value;
        }

        long2Bytes(min_value, encode_pos, encoded_result);
        encode_pos += 8;

        int bw = bitWidth(max_value - min_value);

        int2Bytes(bw, encode_pos, encoded_result);
        encode_pos += 4;

        VBPIndexLong idx = new VBPIndexLong(bw, block_data);
        indexList.add(idx);

        return encode_pos;

    }

    public static void BlockDecoder(byte[] encoded_result1, byte[] encoded_result2, int block_index, int block_size1, int block_size2,
            int[] encode_pos, ArrayList<VBPIndexLong> indexList1, ArrayList<VBPIndexLong> indexList2, int[] result, int[] result_length,
            int bound_query_range) {

        long min_value1 = bytes2Long(encoded_result1, encode_pos[0], 8);
        encode_pos[0] += 8;

        int bw1 = bytes2Integer(encoded_result1, encode_pos[0], 4);
        encode_pos[0] += 4;

        long min_value2 = bytes2Long(encoded_result2, encode_pos[1], 8);
        encode_pos[1] += 8;

        int bw2 = bytes2Integer(encoded_result2, encode_pos[1], 4);
        encode_pos[1] += 4;


        VBPIndexLong idx1 = indexList1.get(block_index);
        VBPIndexLong idx2 = indexList2.get(block_index);

        BitSet bitset_result1 = idx1.select(HBPIndex.Op.LT, bound_query_range);
        BitSet bitset_result2 = idx2.select(HBPIndex.Op.LT, bound_query_range);

        for (int i = 0; i < bitset_result1.length(); i++) {
            if (bitset_result1.get(i) && bitset_result2.get(i)) {
                result[result_length[0]] = i + (block_index * block_size1);
                result_length[0]++;
            }
        }

    }

    public static int Encoder(long[] data, int block_size, ArrayList<VBPIndexLong> indexList, byte[] encoded_result) {
        int data_length = data.length;
        int encode_pos = 0;

        int2Bytes(data_length, encode_pos, encoded_result);
        encode_pos += 4;

        int2Bytes(block_size, encode_pos, encoded_result);
        encode_pos += 4;

        int num_blocks = data_length / block_size;

        int remainder = data_length % block_size;

        for (int i = 0; i < num_blocks; i++) {
            encode_pos = BlockEncoder(data, i, block_size, block_size, encode_pos, indexList, encoded_result);
        }

        // if (remainder <= 3) {
        // for (int i = 0; i < remainder; i++) {
        // long value = data[num_blocks * block_size + i];
        // long2Bytes(value, encode_pos, encoded_result);
        // encode_pos += 8;
        // }
        // } else {
        encode_pos = BlockEncoder(data, num_blocks, block_size, remainder, encode_pos, indexList,
                encoded_result);
        // }

        // System.out.println("beta: " + beta[0]);

        return encode_pos;
    }

    public static void Decoder(byte[] encoded_result1, byte[] encoded_result2, ArrayList<VBPIndexLong> indexList1, ArrayList<VBPIndexLong> indexList2, int bound_query_range) {
        int[] encode_pos = new int[2];

        int data_length1 = bytes2Integer(encoded_result1, encode_pos[0], 4);
        encode_pos[0] += 4;

        int block_size1 = bytes2Integer(encoded_result1, encode_pos[0], 4);
        encode_pos[0] += 4;

        int num_blocks = data_length1 / block_size1;

        int data_length2 = bytes2Integer(encoded_result2, encode_pos[1], 4);
        encode_pos[1] += 4;

        int block_size2 = bytes2Integer(encoded_result2, encode_pos[1], 4);
        encode_pos[1] += 4;


        int[] result = new int[data_length1];
        int[] result_length = new int[1];

        for (int i = 0; i < num_blocks; i++) {
            BlockDecoder(encoded_result1, encoded_result2, i, block_size1, block_size2, encode_pos, indexList1, indexList2, result,
                    result_length, bound_query_range);
        }

        int remainder = data_length1 % block_size1;

        // if (remainder <= 3) {
        // for (int i = 0; i < remainder; i++) {
        // data[num_blocks * block_size + i] = bytes2Long(encoded_result, encode_pos,
        // 8);
        // encode_pos += 8;
        // }
        // } else {
        BlockDecoder(encoded_result1, encoded_result2, num_blocks, block_size1, block_size2,
                encode_pos, indexList1, indexList2, result, result_length, bound_query_range);
        // }
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
    public void test0() throws IOException {
        String parent_dir = "D:/github/xjz17/subcolumn/";
        // // String parent_dir = "D:/encoding-subcolumn/";
        //
        String input_parent_dir = parent_dir + "dataset/";
        //
        String output_parent_dir = "D:/encoding-subcolumn/result/";
        // // String output_parent_dir = parent_dir + "result/";
        //
        // String outputPath = output_parent_dir + "vbp_query.csv";

        // String parent_dir = "/Users/xiaojinzhao/Documents/GitHub/subcolumn/";
        // //"D:/github/xjz17/subcolumn/";
        // String parent_dir = "D:/encoding-subcolumn/";

        // String input_parent_dir = parent_dir + "dataset/";

        // String output_parent_dir = parent_dir + "result/vbp_query/";

        String outputPath = output_parent_dir + "vbp_query_less_parts.csv";
        // String output_parent_dir = parent_dir + "result/query_vs_beta/";

        HashMap<String, Integer> queryRange = new HashMap<>();

        queryRange.put("Bird-migration", 2500000);
        queryRange.put("Bitcoin-price", 160000000);
        queryRange.put("City-temp", 480);
        queryRange.put("Dewpoint-temp", 9500);
        queryRange.put("IR-bio-temp", -300);
        queryRange.put("PM10-dust", 1000);
        queryRange.put("Stocks-DE", 40000);
        queryRange.put("Stocks-UK", 20000);
        queryRange.put("Stocks-USA", 5000);
        queryRange.put("Wind-Speed", 50);
        queryRange.put("Wine-Tasting", 0);
        queryRange.put("Arade4", 10000000);
        queryRange.put("EPM-Education", 200);
        queryRange.put("POI-lat", 0);
        queryRange.put("Gov10", 100000);

        int block_size = 512;

        int repeatTime = 100;
        // repeatTime = 500;

        // repeatTime = 1;

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
            if (!queryRange.containsKey(datasetName)) {
                continue;
            }

            InputStream inputStream = Files.newInputStream(file.toPath());

            CsvReader loader = new CsvReader(inputStream, StandardCharsets.UTF_8);
            ArrayList<Double> data1 = new ArrayList<>();

            int max_decimal = 0;
            while (loader.readRecord()) {
                String f_str = loader.getValues()[0];
                if (f_str.isEmpty()) {
                    continue;
                }
                int cur_decimal = getDecimalPrecision(f_str);
                if (cur_decimal > max_decimal) {
                    max_decimal = cur_decimal;
                }
                data1.add(Double.valueOf(f_str));
            }
            inputStream.close();

            if (max_decimal > 17) {
                max_decimal = 17;
            }

            // long[] data2_arr = new long[data1.size()];
            int totalSize = data1.size();
            int halfSize = totalSize / 2;

            long[] col1_data = new long[halfSize];
            long[] col2_data = new long[halfSize];

            long max_mul = (long) Math.pow(10, max_decimal);
            for (int i = 0; i < halfSize; i++) {
                col1_data[i] = (long) (data1.get(i) * max_mul);
            }

            for (int i = 0; i < halfSize; i++) {
                col2_data[i] = (long) (data1.get(i + halfSize) * max_mul);
            }

            System.out.println(max_decimal);

            byte[] encoded_result1 = new byte[col1_data.length * 8];
            byte[] encoded_result2 = new byte[col2_data.length * 8];

            long encodeTime = 0;
            long decodeTime = 0;
            double ratio = 0;
            double compressed_size = 0;

            int length = 0;

            ArrayList<VBPIndexLong> indexList1 = new ArrayList<>();
            ArrayList<VBPIndexLong> indexList2 = new ArrayList<>();

            long s = System.nanoTime();
            for (int repeat = 0; repeat < repeatTime; repeat++) {
                // clear indexList
                indexList1.clear();
                indexList2.clear();

                length = Encoder(col1_data, block_size, indexList1, encoded_result1);

                length = Encoder(col2_data, block_size, indexList2, encoded_result2);
            }

            long e = System.nanoTime();
            encodeTime += ((e - s) / repeatTime);
            compressed_size += length;

            for (VBPIndexLong idx : indexList1) {
                compressed_size += idx.k * idx.wordsPerPlane * Long.BYTES;
            }

            for (VBPIndexLong idx : indexList2) {
                compressed_size += idx.k * idx.wordsPerPlane * Long.BYTES;
            }

            double ratioTmp;

            ratioTmp = compressed_size / (double) (data1.size() * Long.BYTES);

            ratio += ratioTmp;

            System.out.println("Decode");

            // long[] data2_arr_decoded = new long[data2_arr.length];

            s = System.nanoTime();

            for (int repeat = 0; repeat < repeatTime; repeat++) {
                Decoder(encoded_result1, encoded_result2, indexList1, indexList2, queryRange.get(datasetName));
            }

            e = System.nanoTime();
            decodeTime += ((e - s) / repeatTime);

            String[] record = {
                    datasetName,
                    "VBP",
                    String.valueOf(encodeTime),
                    String.valueOf(decodeTime),
                    String.valueOf(data1.size()),
                    String.valueOf(compressed_size),
                    String.valueOf(ratio)
            };
            writer.writeRecord(record);
            System.out.println(ratio);
        }

        writer.close();
    }

}
