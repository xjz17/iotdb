package org.apache.iotdb.tsfile.encoding;

import me.lemire.integercompression.*;

import java.io.*;
import java.util.Arrays;


import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import org.apache.iotdb.tsfile.compress.ICompressor;
import org.apache.iotdb.tsfile.compress.IUnCompressor;
import org.apache.iotdb.tsfile.encoding.decoder.Decoder;
import org.apache.iotdb.tsfile.encoding.encoder.Encoder;
import org.apache.iotdb.tsfile.encoding.encoder.TSEncodingBuilder;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.utils.Binary;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;

import static org.apache.iotdb.tsfile.utils.ReadWriteIOUtils.writeObject;

public class PFORTest {
    public static void main(@NotNull String[] args) throws IOException {
//        final int N = 1310720;
//        int[] data = new int[N];
//
//        // 2-bit data
//        for (int k = 0; k < N; k += 1)
//            data[k] = 3;
//
//        // a few large values
//        for (int k = 0; k < N; k += 5)
//            data[k] = 100;
//        for (int k = 0; k < N; k += 533)
//            data[k] = 10000;

        String parent_dir = "/Users/zihanguo/Downloads/outliier_code/encoding-outlier/";
        String output_parent_dir = parent_dir + "vldb/compression_ratio/btr_ratio/";
        String input_parent_dir = parent_dir + "iotdb_test_small/";
        ArrayList<String> input_path_list = new ArrayList<>();
        ArrayList<String> output_path_list = new ArrayList<>();
        ArrayList<String> dataset_name = new ArrayList<>();
        ArrayList<Integer> dataset_block_size = new ArrayList<>();

        dataset_name.add("CS-Sensors");
        dataset_name.add("Metro-Traffic");
        dataset_name.add("USGS-Earthquakes");
        dataset_name.add("YZ-Electricity");
        dataset_name.add("GW-Magnetic");
        dataset_name.add("TY-Fuel");
        dataset_name.add("Cyber-Vehicle");
        dataset_name.add("Vehicle-Charge");
        dataset_name.add("Nifty-Stocks");
        dataset_name.add("TH-Climate");
        dataset_name.add("TY-Transport");
        dataset_name.add("EPM-Education");

        for (int i = 0; i < dataset_name.size(); i++) {
            input_path_list.add(input_parent_dir + dataset_name.get(i));
        }

        output_path_list.add(output_parent_dir + "CS-Sensors_ratio.csv"); // 0
        dataset_block_size.add(1024);
//    dataset_k.add(5);
        output_path_list.add(output_parent_dir + "Metro-Traffic_ratio.csv");// 1
        dataset_block_size.add(512);
//    dataset_k.add(7);
        output_path_list.add(output_parent_dir + "USGS-Earthquakes_ratio.csv");// 2
        dataset_block_size.add(512);
//    dataset_k.add(7);
        output_path_list.add(output_parent_dir + "YZ-Electricity_ratio.csv"); // 3
        dataset_block_size.add(512);
//    dataset_k.add(1);
        output_path_list.add(output_parent_dir + "GW-Magnetic_ratio.csv"); //4
        dataset_block_size.add(128);
//    dataset_k.add(6);
        output_path_list.add(output_parent_dir + "TY-Fuel_ratio.csv");//5
        dataset_block_size.add(64);
//    dataset_k.add(5);
        output_path_list.add(output_parent_dir + "Cyber-Vehicle_ratio.csv"); //6
        dataset_block_size.add(128);
//    dataset_k.add(4);
        output_path_list.add(output_parent_dir + "Vehicle-Charge_ratio.csv");//7
        dataset_block_size.add(512);
//    dataset_k.add(8);
        output_path_list.add(output_parent_dir + "Nifty-Stocks_ratio.csv");//8
        dataset_block_size.add(256);
//    dataset_k.add(1);
        output_path_list.add(output_parent_dir + "TH-Climate_ratio.csv");//9
        dataset_block_size.add(512);
//    dataset_k.add(2);
        output_path_list.add(output_parent_dir + "TY-Transport_ratio.csv");//10
        dataset_block_size.add(512);
//    dataset_k.add(9);
        output_path_list.add(output_parent_dir + "EPM-Education_ratio.csv");//11
        dataset_block_size.add(512);

//        for(int file_i=3;file_i<4;file_i++){
        for (int file_i = 0; file_i < input_path_list.size(); file_i++) {
            String inputPath = input_path_list.get(file_i);
            String Output = output_path_list.get(file_i);
            int repeatTime = 1; // set repeat time
            String dataTypeName = "int"; // set dataType
            //    if (args.length >= 2) inputPath = args[1];
            //    if (args.length >= 3) Output = args[2];

            File file = new File(inputPath);
            File[] tempList = file.listFiles();

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
            ArrayList<Integer> columnIndexes = new ArrayList<>(); // set the column indexes of compressed
            for (int i = 0; i < 2; i++) {
                columnIndexes.add(i, i);
            }
            int points = 0;
            for (File f : tempList) {
                fileRepeat = 0;
                double final_ratio = 0;
                int final_compressed_size = 0;
                System.out.println(f);
                long encodeTime = 0;

                while(fileRepeat < 10) {
                    fileRepeat += 1;
                    InputStream inputStream = Files.newInputStream(f.toPath());
                    CsvReader loader = new CsvReader(inputStream, StandardCharsets.UTF_8);
                    String fileName = f.getAbsolutePath();
                    ArrayList<String> data = new ArrayList<>();

                    for (int index : columnIndexes) {
                        // add a column to "data"
                        //        System.out.println(index);
                        if (index == 0) {
                            continue;
                        }
                        int max_precision = 0;
                        loader.readHeaders();
                        data.clear();
                        while (loader.readRecord()) {
                            String v = loader.getValues()[index];
                            //          int ind = v.indexOf(".");
                            //          if (ind > -1) {
                            //            int len = v.substring(ind + 1).length();
                            //            if (len > max_precision) {
                            //              max_precision = len;
                            //            }
                            //          }
                            data.add(v);
                        }
                        points = data.size();
                        inputStream.close();

                        //                    System.out.println(data.size());
                        // encode data
                        int[] tmp = new int[data.size()];
                        ArrayList<Integer> tmp_array = new ArrayList<>();
                        int i = 0;
                        for (String value : data) {
                            tmp[i++] = Integer.parseInt(value);
                            tmp_array.add(Integer.parseInt(value));
                        }
                        // sample data
                        long s = System.nanoTime();
                        int sample_num = tmp.length / 100;
                        int segment_num = sample_num / 4;
                        Random r = new Random();
                        int round_length;
                        ArrayList<Integer> sampled_date = new ArrayList<Integer>();
                        round_length = r.nextInt(tmp.length / 4 - segment_num);
                        for (int j = 0; j < segment_num; j++) {
                            sampled_date.add(tmp[j + round_length]);
                        }
                        round_length = r.nextInt(tmp.length / 4 - segment_num);
                        for (int j = 0; j < segment_num; j++) {
                            sampled_date.add(tmp[j + round_length + tmp.length / 4]);
                        }
                        round_length = r.nextInt(tmp.length / 4 - segment_num);
                        for (int j = 0; j < segment_num; j++) {
                            sampled_date.add(tmp[j + round_length + tmp.length / 2]);
                        }
                        round_length = r.nextInt(tmp.length / 4 - segment_num);
                        for (int j = 0; j < segment_num; j++) {
                            sampled_date.add(tmp[j + round_length + 3 * tmp.length / 4]);
                        }
                        double rle_ratio = 9999;
                        double dict_ratio = 9999;
                        double pfor_ratio = 9999;
                        double bp128_ratio = 9999;
                        // pre test rle?
                        ArrayList<Integer> rla = new ArrayList<Integer>();
                        int rl = 1;
                        int last_value = 9999999;
                        for (int j : sampled_date) {
                            if (j == last_value) {
                                rl++;
                            } else {
                                last_value = j;
                                rla.add(rl);
                                rl = 1;
                            }
                        }
                        int sum = 0;
                        for (int j : rla) {
                            sum += j;
                        }
                        if (sum * 1.0 / rla.size() < 2) {
                            //System.out.println("Dont consider rle");
                        } else {
                            Encoder encoder =
                                    TSEncodingBuilder.getEncodingBuilder(TSEncoding.RLE).getEncoder(TSDataType.INT32);
                            Decoder decoder = Decoder.getDecoderByType(TSEncoding.RLE, TSDataType.INT32);
                            long decodeTime = 0;
                            ICompressor compressor = ICompressor.getCompressor(CompressionType.UNCOMPRESSED);
                            IUnCompressor unCompressor = IUnCompressor.getUnCompressor(CompressionType.UNCOMPRESSED);

                            double compressed_size = 0;
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                            // test encode time
                            for (int val : sampled_date) {
                                encoder.encode(val, buffer);
                            }

                            //                    byte[] elems = buffer.toByteArray();
                            encoder.flush(buffer);

                            // test compress time
                            byte[] elems = buffer.toByteArray();
                            byte[] compressed = compressor.compress(elems);

                            // test compression ratio and compressed size
                            compressed_size += compressed.length;
                            rle_ratio = (double) compressed.length / (double) (sampled_date.size() * Integer.BYTES);
                            //System.out.println("RLE ratio:" + rle_ratio);
                        }
                        //System.out.println(sum * 1.0/ rla.size());
                        //pre test dict
                        HashSet<Integer> set = new HashSet<Integer>();
                        HashSet<Integer> unique = new HashSet<Integer>();
                        for (int j : sampled_date) {
                            unique.add(j);
                            if (set.contains(j)) {
                                unique.remove(j);
                            }
                            set.add(j);
                        }
                        if (unique.size() * 1.0 / set.size() > 0.5) {
                            //System.out.println("Dont consider dict");
                        } else {
                            Map<Integer, Integer> dictionary = new HashMap<>();
                            ArrayList<Integer> encodedList = new ArrayList<>();

                            int currentIndex = 0;
                            for (int num : sampled_date) {
                                if (!dictionary.containsKey(num)) {
                                    dictionary.put(num, currentIndex);
                                    currentIndex++;
                                }
                                encodedList.add(dictionary.get(num));
                            }

                            int max = 1;
                            for (int num : encodedList) {
                                int bitWidth = 32 - Integer.numberOfLeadingZeros(num);
                                max = Math.max(bitWidth, max);
                            }

                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                            objectOutputStream.writeObject(dictionary);
                            byte[] dict_byte = byteArrayOutputStream.toByteArray();

                            int space = dict_byte.length * 8 + max * encodedList.size();
                            dict_ratio = (double) space / (double) (sampled_date.size() * 32);
                            //System.out.println("Dict ratio:" + dict_ratio);
                        }
                        //System.out.println(unique.size()*1.0/set.size());
                        // PFOR BP128
                        tmp = new int[sampled_date.size()];
                        i = 0;
                        for (int j : sampled_date) {
                            tmp[i++] = j;
                        }
                        IntegerCODEC codec = new Composition(new FastPFOR(), new VariableByte());
                        int[] compressed = new int[tmp.length + 1024];
                        IntWrapper inputoffset = new IntWrapper(0);
                        IntWrapper outputoffset = new IntWrapper(0);

                        codec.compress(tmp, inputoffset, tmp.length, compressed, outputoffset);

                        //                    System.out.println("compressed unsorted integers from " +
                        //                            tmp.length * 4 / 1024 + "KB to " +
                        //                            outputoffset.intValue() * 4 / 1024 + "KB");
                        pfor_ratio = 1.0 * outputoffset.intValue() / tmp.length;
                        //System.out.println("PFOR ratio:" + pfor_ratio);

                        codec = new Composition(new FastPFOR128(), new VariableByte());
                        compressed = new int[tmp.length + 1024];
                        inputoffset = new IntWrapper(0);
                        outputoffset = new IntWrapper(0);

                        codec.compress(tmp, inputoffset, tmp.length, compressed, outputoffset);

                        //                    System.out.println("compressed unsorted integers from " +
                        //                            tmp.length * 4 / 1024 + "KB to " +
                        //                            outputoffset.intValue() * 4 / 1024 + "KB");
                        bp128_ratio = 1.0 * outputoffset.intValue() / tmp.length;

                        //                    compressed = Arrays.copyOf(compressed, outputoffset.intValue());

                        //                    int[] recovered = new int[tmp.length];
                        //                    IntWrapper recoffset = new IntWrapper(0);
                        //
                        //                    codec.uncompress(compressed, new IntWrapper(0), compressed.length,
                        //                            recovered, recoffset);

                        double minValue = 9999;
                        String opimal = "No Choice";

                        System.out.println("rle ratio:" + rle_ratio);
                        System.out.println("dict ratio:" + dict_ratio);
                        System.out.println("pfor ratio:" + pfor_ratio);
                        System.out.println("bp128 ratio:" + bp128_ratio);
                        if (rle_ratio < minValue) {
                            minValue = rle_ratio;
                            opimal = "rle";
                        }

                        if (dict_ratio < minValue) {
                            minValue = dict_ratio;
                            opimal = "dict";
                        }

                        if (pfor_ratio < minValue) {
                            minValue = pfor_ratio;
                            opimal = "pfor";
                        }

                        if (bp128_ratio < minValue) {
                            minValue = bp128_ratio;
                            opimal = "bp128";
                        }

                        //                    System.out.println("best ratio:" + minValue);
                                            System.out.println("choose " + opimal);
                        //                    System.out.println(data.size());
                        tmp = new int[data.size()];
                        tmp_array = new ArrayList<>();
                        i = 0;
                        for (String value : data) {
                            tmp[i++] = Integer.parseInt(value);
                            tmp_array.add(Integer.parseInt(value));
                        }
                        double ratio = 0;
                        byte[] compressed_byte;
                        int compressed_size = 0;
                        if (opimal.equals("rle")) {
                            Encoder encoder =
                                    TSEncodingBuilder.getEncodingBuilder(TSEncoding.RLE).getEncoder(TSDataType.INT32);
                            Decoder decoder = Decoder.getDecoderByType(TSEncoding.RLE, TSDataType.INT32);
                            long decodeTime = 0;
                            ICompressor compressor = ICompressor.getCompressor(CompressionType.UNCOMPRESSED);
                            IUnCompressor unCompressor = IUnCompressor.getUnCompressor(CompressionType.UNCOMPRESSED);

                            compressed_size = 0;
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                            // test encode time
                            for (int val : tmp) {
                                encoder.encode(val, buffer);
                            }

                            //                    byte[] elems = buffer.toByteArray();
                            encoder.flush(buffer);


                            // test compress time
                            byte[] elems = buffer.toByteArray();
                            compressed_byte = compressor.compress(elems);

                            // test compression ratio and compressed size
                            final_compressed_size += compressed_byte.length;
                            final_ratio += (double) compressed_byte.length / (double) (tmp.length * Integer.BYTES);
                        } else if (opimal.equals("dict")) {
                            Map<Integer, Integer> dictionary = new HashMap<>();
                            ArrayList<Integer> encodedList = new ArrayList<>();

                            int currentIndex = 0;
                            for (int num : tmp) {
                                if (!dictionary.containsKey(num)) {
                                    dictionary.put(num, currentIndex);
                                    currentIndex++;
                                }
                                encodedList.add(dictionary.get(num));
                            }

                            int max = 1;
                            for (int num : encodedList) {
                                int bitWidth = 32 - Integer.numberOfLeadingZeros(num);
                                max = Math.max(bitWidth, max);
                            }

                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                            objectOutputStream.writeObject(dictionary);
                            byte[] dict_byte = byteArrayOutputStream.toByteArray();

                            final_compressed_size += dict_byte.length + max * encodedList.size() / 8;
                            final_ratio += (double) (dict_byte.length + max * encodedList.size() / 8) / (double) (tmp.length * 4);
                            //System.out.println("Dict ratio:" + dict_ratio);
                        } else if (opimal.equals("pfor")) {
                            codec = new Composition(new FastPFOR(), new VariableByte());
                            compressed = new int[tmp.length + 1024];
                            inputoffset = new IntWrapper(0);
                            outputoffset = new IntWrapper(0);

                            codec.compress(tmp, inputoffset, tmp.length, compressed, outputoffset);

                            //                    System.out.println("compressed unsorted integers from " +
                            //                            tmp.length * 4 / 1024 + "KB to " +
                            //                            outputoffset.intValue() * 4 / 1024 + "KB");
                            final_ratio += 1.0 * outputoffset.intValue() / tmp.length;
                            final_compressed_size += outputoffset.intValue() * 4;
                            //System.out.println("PFOR ratio:" + pfor_ratio);
                        } else if (opimal.equals("bp128")) {
                            codec = new Composition(new FastPFOR128(), new VariableByte());
                            compressed = new int[tmp.length + 1024];
                            inputoffset = new IntWrapper(0);
                            outputoffset = new IntWrapper(0);

                            codec.compress(tmp, inputoffset, tmp.length, compressed, outputoffset);

                            //                    System.out.println("compressed unsorted integers from " +
                            //                            tmp.length * 4 / 1024 + "KB to " +
                            //                            outputoffset.intValue() * 4 / 1024 + "KB");
                            final_ratio += 1.0 * outputoffset.intValue() / tmp.length;
                            final_compressed_size += outputoffset.intValue() * 4;
                            //System.out.println("PFOR ratio:" + pfor_ratio);
                        }
                        //                    System.out.println(ratio);
                        encodeTime += System.nanoTime() - s;
                    }
                    inputStream = Files.newInputStream(f.toPath());
                    loader = new CsvReader(inputStream, StandardCharsets.UTF_8);
                }

                String[] record = {
                        f.toString(),
                        "1",
                        "BTRBLOCKS",
                        "NOCOMPRESSION",
                        String.valueOf(1.0*encodeTime/fileRepeat),
                        "0",
                        "0",
                        "0",
                        String.valueOf(points),
                        String.valueOf(final_compressed_size/fileRepeat),
                        String.valueOf(final_ratio/fileRepeat)
                };
                System.out.println(final_ratio/fileRepeat);
                writer.writeRecord(record);
            }
            writer.close();
        }
    }
}
