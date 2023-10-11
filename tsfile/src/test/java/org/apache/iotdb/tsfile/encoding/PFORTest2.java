package org.apache.iotdb.tsfile.encoding;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import me.lemire.integercompression.*;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.apache.iotdb.tsfile.encoding.myPFOR.compressOneBlockOpt;
import static org.apache.iotdb.tsfile.encoding.myPFOR.decompressOneBlock;

public class PFORTest2 {
    public static void main(@NotNull String[] args) throws IOException {
        String parent_dir = "/Users/zihanguo/Downloads/outliier_code/encoding-outlier/";
        String output_parent_dir = parent_dir + "vldb/compression_ratio/pfor_ratio/";
        String input_parent_dir = parent_dir + "trans_data/";
        ArrayList<String> input_path_list = new ArrayList<>();
        ArrayList<String> output_path_list = new ArrayList<>();
        ArrayList<String> dataset_name = new ArrayList<>();
        ArrayList<Integer> dataset_block_size = new ArrayList<>();
        ArrayList<String> encoding_list = new ArrayList<>();

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

        encoding_list.add("NEWPFD");
        encoding_list.add("OPTPFD");
        encoding_list.add("FASTPFOR");
//        encoding_list.add("PFOR");

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
            IntegerCODEC codec = new Composition(new JustCopy(), new VariableByte());;
            IntWrapper inputoffset;
            IntWrapper outputoffset;
            int[] compressed;
            for (String encoding : encoding_list) {
                if (Objects.equals(encoding, "FASTPFOR")){
                    codec = new Composition(new FastPFOR(), new VariableByte());
                } else if (Objects.equals(encoding, "NEWPFD")) {
                    codec = new Composition(new NewPFDS16(), new VariableByte());
                } else if (Objects.equals(encoding, "OPTPFD")) {
                    codec = new Composition(new OptPFDS16(), new VariableByte());
                }
                else if (Objects.equals(encoding, "PFOR")) {
                    for (File f : tempList) {
                        fileRepeat = 0;
                        double final_ratio = 0;
                        int final_compressed_size = 0;
                        System.out.println(f);
                        long encodeTime = 0;
                        long decodeTime = 0;
                        while (fileRepeat < 1) {
                            fileRepeat += 1;
                            InputStream inputStream = Files.newInputStream(f.toPath());
                            CsvReader loader = new CsvReader(inputStream, StandardCharsets.UTF_8);
                            ArrayList<String> data = new ArrayList<>();
                            for (int index : columnIndexes) {
                                if (index == 0) {
                                    continue;
                                }
                                loader.readHeaders();
                                data.clear();
                                while (loader.readRecord()) {
                                    String v = loader.getValues()[index];
                                    data.add(v);
                                }
                                points = data.size();
                                inputStream.close();

                                int[] origin = new int[data.size()];
                                int i = 0;
                                for (String value : data) {
                                    origin[i] = Integer.parseInt(value);
                                    i++;
                                }
                                //PFOR
                                int[] outBlock;
                                long s = System.nanoTime();
                                outBlock = compressOneBlockOpt(origin,origin.length);
                                encodeTime += System.nanoTime() - s;
                                int[] uncompressed = new int[origin.length];
                                s = System.nanoTime();
                                int size = decompressOneBlock(uncompressed,outBlock,origin.length);
                                decodeTime += System.nanoTime() - s;
                                if (Arrays.equals(origin, uncompressed)) {
//                                System.out.println("data is recovered without loss");
                                }
                                else {
//                                    System.out.println(tmp.length);
//                                    for (int j = 0; j < origin.length; j++){
//                                        System.out.println(origin[j]);
//                                        System.out.println(uncompressed[j]);
//                                    }
                                    System.out.println("get bug");
                                    //throw new RuntimeException("bug");
                                }
                                final_ratio += 1.0 * outBlock.length / origin.length;
                                final_compressed_size += outBlock.length * 4;
                            }
                        }
                        String[] record = {
                                f.toString(),
                                "1",
                                encoding,
                                "NOCOMPRESSION",
                                String.valueOf(1.0 * encodeTime / fileRepeat),
                                String.valueOf(1.0 * decodeTime / fileRepeat),
                                "0",
                                "0",
                                String.valueOf(points),
                                String.valueOf(final_compressed_size / fileRepeat),
                                String.valueOf(final_ratio / fileRepeat)
                        };
//                    System.out.println(final_ratio / fileRepeat);
                        writer.writeRecord(record);
                    }
                    continue;
                }
                for (File f : tempList) {
                    fileRepeat = 0;
                    double final_ratio = 0;
                    int final_compressed_size = 0;
                    System.out.println(f);
                    long encodeTime = 0;
                    long decodeTime = 0;

                    while (fileRepeat < 10) {
                        fileRepeat += 1;
                        InputStream inputStream = Files.newInputStream(f.toPath());
                        CsvReader loader = new CsvReader(inputStream, StandardCharsets.UTF_8);
                        ArrayList<String> data = new ArrayList<>();

                        for (int index : columnIndexes) {
                            if (index == 0) {
                                continue;
                            }
                            loader.readHeaders();
                            data.clear();
                            while (loader.readRecord()) {
                                String v = loader.getValues()[index];
                                data.add(v);
                            }
                            points = data.size();
                            inputStream.close();

                            int[] tmp = new int[data.size()];
                            int i = 0;
                            for (String value : data) {
                                tmp[i++] = Integer.parseInt(value);
                            }
                            compressed = new int[tmp.length + 1024];
                            inputoffset = new IntWrapper(0);
                            outputoffset = new IntWrapper(0);

                            long s = System.nanoTime();
                            codec.compress(tmp, inputoffset, tmp.length, compressed, outputoffset);
                            encodeTime += System.nanoTime() - s;

                            compressed = Arrays.copyOf(compressed, outputoffset.intValue());

                            int[] recovered = new int[tmp.length];
                            IntWrapper recoffset = new IntWrapper(0);

                            s = System.nanoTime();
                            codec.uncompress(compressed, new IntWrapper(0), compressed.length,
                                    recovered, recoffset);
                            decodeTime += System.nanoTime() - s;

                            if (Arrays.equals(tmp, recovered)) {
//                                System.out.println("data is recovered without loss");
                            }
                            else
                                throw new RuntimeException("bug");

                            final_ratio += 1.0 * outputoffset.intValue() / tmp.length;
                            final_compressed_size += outputoffset.intValue() * 4;
                        }
                    }

                    String[] record = {
                            f.toString(),
                            "1",
                            encoding,
                            "NOCOMPRESSION",
                            String.valueOf(1.0 * encodeTime / fileRepeat),
                            String.valueOf(1.0 * decodeTime / fileRepeat),
                            "0",
                            "0",
                            String.valueOf(points),
                            String.valueOf(final_compressed_size / fileRepeat),
                            String.valueOf(final_ratio / fileRepeat)
                    };
//                    System.out.println(final_ratio / fileRepeat);
                    writer.writeRecord(record);
                }
            }
            writer.close();
        }
    }
}
