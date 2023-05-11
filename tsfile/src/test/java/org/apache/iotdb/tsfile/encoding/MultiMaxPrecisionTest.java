package org.apache.iotdb.tsfile.encoding;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;

public class MultiMaxPrecisionTest {
    static double log2_10 = Math.log(10) / Math.log(2);
    public static int getBitWith(int num) {
        if (num == 0) return 1;
        else
            return 32 - Integer.numberOfLeadingZeros(num);
    }

    public static byte[] int2Bytes(int integer) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) (integer >> 24);
        bytes[1] = (byte) (integer >> 16);
        bytes[2] = (byte) (integer >> 8);
        bytes[3] = (byte) integer;
        return bytes;
    }
    public static byte[] bitPacking(ArrayList<Integer> numbers, int bit_width) {
        int block_num = numbers.size() / 8;
        byte[] result = new byte[bit_width * block_num];
        for (int i = 0; i < block_num; i++) {
            for (int j = 0; j < bit_width; j++) {
                int tmp_int = 0;
                for (int k = 0; k < 8; k++) {
                    tmp_int += (((numbers.get(i * 8 + k) >> j) % 2) << k);
                }
                result[i * bit_width + j] = (byte) tmp_int;
            }
        }
        return result;
    }
    public static byte[] double2Bytes(double dou){
        long value = Double.doubleToRawLongBits(dou);
        byte[] bytes= new byte[8];
        for(int i=0;i<8;i++){
            bytes[i] = (byte) ((value >>8*i)& 0xff);
        }
        return bytes;
    }
    public static void main(@org.jetbrains.annotations.NotNull String[] args) throws IOException {
        ArrayList<String> input_path_list = new ArrayList<>();
        ArrayList<String> output_path_list = new ArrayList<>();
        input_path_list.add("C:\\Users\\xiaoj\\Desktop\\elfdata\\1");
        output_path_list.add("C:\\Users\\xiaoj\\Desktop\\test_ratio_elf.csv");

        double value = 8.85;
        long longBits = Double.doubleToLongBits(value);
        String binaryString = Long.toBinaryString(longBits);
        System.out.println(binaryString);

        for (int file_i = 0; file_i < input_path_list.size(); file_i++) {
            String inputPath = input_path_list.get(file_i);
            String Output = output_path_list.get(file_i);

            // speed
            int repeatTime = 1; // set repeat time
            String dataTypeName = "double"; // set dataType

            File file = new File(inputPath);
            File[] tempList = file.listFiles();

            // select encoding algorithms
            TSEncoding[] encodingList = {
//            TSEncoding.PLAIN ,
                    TSEncoding.TS_2DIFF,
//                    TSEncoding.CHIMP,
                    TSEncoding.GORILLA,
            };
            // select compression algorithms
            CompressionType[] compressList = {
                    CompressionType.UNCOMPRESSED,
                    //            CompressionType.LZ4,
                    //            CompressionType.GZIP,
                    //            CompressionType.SNAPPY
            };
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            String[] head = {
                    "Input Direction",
                    "Column Index",
                    "Encoding Algorithm",
                    "Compress Algorithm",
                    "Encoding Time",
                    "Decoding Time",
                    "Compress Time",
                    "Uncompress Time",
                    "Points",
                    "Compressed Size",
                    "Compression Ratio"
            };
            writer.writeRecord(head); // write header to output file

            assert tempList != null;
            int fileRepeat = 0;
//            ArrayList<Integer> columnIndexes = new ArrayList<>(); // set the column indexes of compressed
//            for (int i = 0; i < 2; i++) {
//                columnIndexes.add(i, i);
//            }
            for (File f : tempList) {

                InputStream inputStream = Files.newInputStream(f.toPath());
                CsvReader loader = new CsvReader(inputStream, StandardCharsets.UTF_8);
                ArrayList<Double> data = new ArrayList<>();
                ArrayList<String> data_str = new ArrayList<>();
                ArrayList<ArrayList<Float>> data_decoded = new ArrayList<>();
                int max_precision = 0;

                // add a column to "data"
                loader.readHeaders();
                data_str.clear();
                while (loader.readRecord()) {
//                    System.out.println(loader.getValues()[1]);
                    data_str.add(loader.getValues()[0]);

                }
                data_str.removeIf(String::isEmpty);
                for (String f_str:data_str){
                    int cur_pre = 0;
                    if (f_str.split("\\.").length != 1) {
                        cur_pre = f_str.split("\\.")[1].length();
                    }
                    if (cur_pre > max_precision) {
                        max_precision = cur_pre;
                    }
//                    System.out.println(Double.valueOf(f_str).floatValue());
                    data.add(Double.valueOf(f_str));
                }


                inputStream.close();
                long encodeTime = 0;
                long decodeTime = 0;
                double ratio = 0;
                double compressed_size = 0;
                int repeatTime2 = 1;
                System.out.println(max_precision);
                for (int i = 0; i < repeatTime; i++) {
                    long s = System.nanoTime();
                    ArrayList<Byte> buffer = new ArrayList<>();
//                    System.out.println(data.get(0));
                    for (int repeat = 0; repeat < repeatTime2; repeat++)
                        buffer = MultiMaxPrecisionTest(data, 1025, max_precision);
                    long e = System.nanoTime();
                    encodeTime += ((e - s) / repeatTime2);
                    compressed_size += buffer.size();
                    double ratioTmp =
                            (double) buffer.size() / (double) (data.size() * Integer.BYTES * 2);
                    ratio += ratioTmp;
                    s = System.nanoTime();
//          for(int repeat=0;repeat<repeatTime2;repeat++)
//            data_decoded = ReorderingRegressionDecoder(buffer);
                    e = System.nanoTime();
                    decodeTime += ((e - s) / repeatTime2);
                }

                ratio /= repeatTime;
                compressed_size /= repeatTime;
                encodeTime /= repeatTime;
                decodeTime /= repeatTime;

                String[] record = {
                        f.toString(),
                        "SUB-COLUMN",
                        String.valueOf(encodeTime),
                        String.valueOf(decodeTime),
                        String.valueOf(data.size()),
                        String.valueOf(compressed_size),
                        String.valueOf(ratio)
                };
//                System.out.println(ratio);
                writer.writeRecord(record);
                break;
            }
            writer.close();
        }
    }
    private static ArrayList<Byte> MultiMaxPrecisionTest(ArrayList<Double> data, int block_size, int max_precision) {
        ArrayList<Byte> encoded_result = new ArrayList<Byte>();
        int length_all = data.size();
        int encoded_length_all = 0;
        byte[] length_all_bytes = int2Bytes(length_all);
        for (byte b : length_all_bytes) encoded_result.add(b);
        encoded_length_all += 4;

        int block_num = length_all / block_size;

        // encode block size (Integer)
        byte[] block_size_byte = int2Bytes(block_size);
        for (byte b : block_size_byte) encoded_result.add(b);
        encoded_length_all += 4;

        for (int i = 0; i < block_num; i++) {

        }

        double ratio = (double) encoded_length_all / (double) (data.size()*Double.BYTES);
        System.out.println("ratio");
        System.out.println(ratio);
        return encoded_result;
    }


}
