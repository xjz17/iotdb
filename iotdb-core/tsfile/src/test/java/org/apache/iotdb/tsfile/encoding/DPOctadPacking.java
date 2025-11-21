package org.apache.iotdb.tsfile.encoding;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DPOctadPacking {
//    private static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data","test.csv","POI-lat.csv",
//            "POI-lon.csv","Air-sensor.csv","Basel-temp.csv");
static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data", "test.csv","POI-lat.csv","POI-lon.csv","Basel-wind.csv","Basel-temp.csv","Air-sensor.csv");
    private static final int CHUNK_SIZE = 1024;

    static String trimStr(String s) {
        if (s == null) return "";
        int a = 0;
        while (a < s.length() && Character.isWhitespace(s.charAt(a))) a++;
        if (a == s.length()) return "";
        int b = s.length() - 1;
        while (b >= 0 && Character.isWhitespace(s.charAt(b))) b--;
        return s.substring(a, b + 1);
    }

    static String stripEnclosingQuotes(String s) {
        if (s == null) return "";
        if (s.length() >= 2) {
            char f = s.charAt(0);
            char l = s.charAt(s.length() - 1);
            if ((f == '"' && l == '"') || (f == '\'' && l == '\'')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    // scaleNumbers: use BigDecimal to parse, scale by 10^decimalMax, shift so min becomes 0, return long[] with clipping
    static long[] scaleNumbers(List<String> numbers, int decimalMax) {
        int n = numbers.size();
        long[] result = new long[n];
        if (n == 0) return result;

        BigDecimal scale = BigDecimal.ONE;
        for (int i = 0; i < decimalMax; ++i) scale = scale.multiply(BigDecimal.TEN);

        BigDecimal[] vals = new BigDecimal[n];
        for (int i = 0; i < n; ++i) {
            String s = trimStr(numbers.get(i));
            s = stripEnclosingQuotes(s);
            if (s.isEmpty()) { vals[i] = BigDecimal.ZERO; continue; }
            s = s.replace(",", ""); // remove thousands sep

            // If scientific notation present, BigDecimal can parse it
            try {
                BigDecimal bd = new BigDecimal(s);
                BigDecimal scaled = bd.multiply(scale);
                // rounding to nearest whole
                BigDecimal rounded = scaled.setScale(0, RoundingMode.HALF_UP);
                vals[i] = rounded;
            } catch (Exception ex) {
                // fallback: parse double
                try {
                    double dv = Double.parseDouble(s);
                    BigDecimal bd = BigDecimal.valueOf(dv).multiply(scale);
                    vals[i] = bd.setScale(0, RoundingMode.HALF_UP);
                } catch (Exception ex2) {
                    System.err.println("Warning: cannot parse token '" + numbers.get(i) + "', set to 0");
                    vals[i] = BigDecimal.ZERO;
                }
            }
        }

        // find min
        BigDecimal minv = vals[0];
        for (int i = 1; i < n; ++i) if (vals[i].compareTo(minv) < 0) minv = vals[i];

        for (int i = 0; i < n; ++i) {
            BigDecimal shifted = vals[i].subtract(minv);
            // clamp to long range
            try {
                BigInteger bi = shifted.toBigIntegerExact();
                if (bi.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) result[i] = Long.MAX_VALUE;
                else if (bi.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) result[i] = Long.MIN_VALUE;
                else result[i] = bi.longValue();
            } catch (ArithmeticException ae) {
                // not an integer exactly: fallback by converting to long with rounding
                BigDecimal rounded = shifted.setScale(0, RoundingMode.HALF_UP);
                try {
                    BigInteger bi = rounded.toBigIntegerExact();
                    if (bi.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) result[i] = Long.MAX_VALUE;
                    else if (bi.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) result[i] = Long.MIN_VALUE;
                    else result[i] = bi.longValue();
                } catch (Exception ex) {
                    result[i] = 0;
                }
            }
        }
        return result;
    }

    public static void main(String[] args) throws IOException {
        // 示例数据（实际应替换为真实时间序列）
        System.out.println("\nPerformance Testing...");
//        String csvFilePath = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/processed_data.csv";
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_BPDP";
        File outputDir = new File(outputDirstr);


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


                    long[] scaledInts = scaleNumbers(chunkNumbers, decimalMax);

                    long startTime = System.nanoTime();
                    int remainder = scaledInts.length % 8;
                    int paddingLength = (remainder == 0) ? 0 : 8 - remainder;

                    // 创建新数组，长度补齐为8的倍数
                    long[] paddedArray = new long[scaledInts.length + paddingLength];
                    System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                    int actual_length = paddedArray.length;
                    int[] bitWidths = new int[actual_length / 8]; // 存储每8个值的位宽结果

                    for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += 8) {
                        // 1. 找出当前8个元素中的最大值
                        long maxInGroup = 0;
                        for (int scaledInts_j = scaledInts_i; scaledInts_j < scaledInts_i + 8; scaledInts_j++) {
                            if (paddedArray[scaledInts_j] > maxInGroup) {
                                maxInGroup = paddedArray[scaledInts_j];
                            }
                        }

                        // 2. 计算该最大值的去头零位宽
                        int bitWidth = 64 - Long.numberOfLeadingZeros(maxInGroup);

                        // 3. 存储结果
                        bitWidths[scaledInts_i / 8] = bitWidth;
                    }
                    int cur_cost = computeMinPackingCost(bitWidths,8);

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
                    "BP-DP",
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
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_BPDP_vary_pack_size";
        File outputDir = new File(outputDirstr);


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
            for(int pack_size_exp = 3; pack_size_exp < 10; pack_size_exp++) {
                int pack_size = (int) Math.pow(2, pack_size_exp);

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


                        long[] scaledInts = scaleNumbers(chunkNumbers, decimalMax);

                        long startTime = System.nanoTime();
                        int remainder = scaledInts.length % pack_size;
                        int paddingLength = (remainder == 0) ? 0 : pack_size - remainder;

                        // 创建新数组，长度补齐为8的倍数
                        long[] paddedArray = new long[scaledInts.length + paddingLength];
                        System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                        int actual_length = paddedArray.length;
                        int[] bitWidths = new int[actual_length / pack_size]; // 存储每8个值的位宽结果

                        for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += pack_size) {
                            // 1. 找出当前8个元素中的最大值
                            long maxInGroup = 0;
                            for (int scaledInts_j = scaledInts_i; scaledInts_j < scaledInts_i + pack_size; scaledInts_j++) {
                                if (paddedArray[scaledInts_j] > maxInGroup) {
                                    maxInGroup = paddedArray[scaledInts_j];
                                }
                            }

                            // 2. 计算该最大值的去头零位宽
                            int bitWidth = 64 - Long.numberOfLeadingZeros(maxInGroup);

                            // 3. 存储结果
                            bitWidths[scaledInts_i / pack_size] = bitWidth;
                        }
                        int[] encodePos = new int[1];
                        byte[] res = compressWithOptimalPacking( paddedArray,  bitWidths, pack_size,encodePos);
                        int cur_cost = encodePos[0];
//                        int cur_cost = computeMinPackingCost(bitWidths,pack_size);

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
                        "BP-DP",
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


    public static int computeMinPackingCost(int[] bitWidths, int pack_size) {
        int N = bitWidths.length;
        if (N == 0) {
            return 0;
        }

        // Precompute the maximum bit width for all intervals [l, r] (0-based)
        int[][] maxB = new int[N][N];
        for (int l = 0; l < N; l++) {
            maxB[l][l] = bitWidths[l];
            for (int r = l + 1; r < N; r++) {
                maxB[l][r] = Math.max(maxB[l][r - 1], bitWidths[r]);
            }
        }

        int minTotalCost = maxB[0][N-1]*pack_size*bitWidths.length;

        // Enumerate all possible C values (ceil(log2(max_pack_size + 1)))
        int maxPossibleC = 32 - Integer.numberOfLeadingZeros(N); // ceil(log2(N + 1))

        for (int C = 1; C <= maxPossibleC; C++) {
            int low_C = (C == 1) ? 1 : (1 << (C - 1));
            int high_C = Math.min((1 << C) - 1, N);

            // DP table: dp[i][a] - min cost for first i octads, a=1 if at least one pack >= low_C
            int[][] dp = new int[N + 1][2];
            int[][] prevState = new int[N + 1][2]; // for backtracking
            int[][] packSize = new int[N + 1][2]; // for backtracking

            // Initialize DP table
            for (int i = 0; i <= N; i++) {
                dp[i][0] = Integer.MAX_VALUE / 2;
                dp[i][1] = Integer.MAX_VALUE / 2;
            }
            dp[0][0] = 0;

            for (int i = 1; i <= N; i++) {
                for (int k = Math.max(1, i - high_C + 1); k <= i; k++) {
                    int packLength = i - k + 1;
                    int currentMaxB = maxB[k - 1][i - 1]; // convert to 0-based indexing

                    // Calculate pack cost: 8 * packLength * currentMaxB + 5 + C
                    int packCost = pack_size * packLength * currentMaxB + 5 + C;

                    // Update DP states based on pack size
                    if (packLength < low_C) {
                        // Cannot transition to state 1 with small packs
                        if (dp[k - 1][0] + packCost < dp[i][0]) {
                            dp[i][0] = dp[k - 1][0] + packCost;
                            prevState[i][0] = k - 1;
                            packSize[i][0] = packLength;
                        }
                        if (dp[k - 1][1] + packCost < dp[i][1]) {
                            dp[i][1] = dp[k - 1][1] + packCost;
                            prevState[i][1] = k - 1;
                            packSize[i][1] = packLength;
                        }
                    } else {
                        // Large pack can transition both states to state 1
                        if (dp[k - 1][0] + packCost < dp[i][1]) {
                            dp[i][1] = dp[k - 1][0] + packCost;
                            prevState[i][1] = k - 1;
                            packSize[i][1] = packLength;
                        }
                        if (dp[k - 1][1] + packCost < dp[i][1]) {
                            dp[i][1] = dp[k - 1][1] + packCost;
                            prevState[i][1] = k - 1;
                            packSize[i][1] = packLength;
                        }
                    }
                }
            }

            // Update minimum total cost for this C value
            if (dp[N][1] < minTotalCost) {
                minTotalCost = dp[N][1];
            }
        }

        return minTotalCost;
    }
    public static byte[] compressWithOptimalPacking(long[] paddedArray, int[] bitWidths, int pack_size, int[] encodePos) {
        // 1. 使用动态规划计算最优分组方案
        PackingPlan optimalPlan = computeOptimalPackingPlan(bitWidths, pack_size);

        // 2. 计算压缩后数据的总大小
        int totalSize = calculateTotalCompressedSize(optimalPlan, pack_size);
        byte[] compressedData = new byte[totalSize];
        int currentPos = 0;

        // 3. 写入元数据头信息
        currentPos = writeMetadataHeader(compressedData, currentPos, optimalPlan, pack_size);

        // 4. 按最优分组方案压缩数据
        currentPos = compressDataBlocks(compressedData, currentPos, paddedArray, optimalPlan, pack_size);

        encodePos[0] = currentPos;

        return compressedData;
    }

    // 动态规划结果类
    static class PackingPlan {
        int optimalC;
        List<Integer> groupSizes;    // 每个分组包含的块数
        List<Integer> groupBitWidths; // 每个分组的位宽
        int totalCost;

        PackingPlan(int optimalC, List<Integer> groupSizes, List<Integer> groupBitWidths, int totalCost) {
            this.optimalC = optimalC;
            this.groupSizes = groupSizes;
            this.groupBitWidths = groupBitWidths;
            this.totalCost = totalCost;
        }
    }

    // 计算最优分组方案的完整实现
    public static PackingPlan computeOptimalPackingPlan(int[] bitWidths, int pack_size) {
        int N = bitWidths.length;
        if (N == 0) {
            return new PackingPlan(0, new ArrayList<>(), new ArrayList<>(), 0);
        }

        // 预计算所有区间的最大位宽
        int[][] maxB = new int[N][N];
        for (int l = 0; l < N; l++) {
            maxB[l][l] = bitWidths[l];
            for (int r = l + 1; r < N; r++) {
                maxB[l][r] = Math.max(maxB[l][r - 1], bitWidths[r]);
            }
        }

        int minTotalCost = Integer.MAX_VALUE;
        int bestC = 1;
        List<Integer> bestGroupSizes = new ArrayList<>();
        List<Integer> bestGroupBitWidths = new ArrayList<>();

        int maxPossibleC = 32 - Integer.numberOfLeadingZeros(N);

        for (int C = 1; C <= maxPossibleC; C++) {
            int low_C = (C == 1) ? 1 : (1 << (C - 1));
            int high_C = Math.min((1 << C) - 1, N);

            // DP表和相关记录数组
            int[][] dp = new int[N + 1][2];
            int[][] prevState = new int[N + 1][2];
            int[][] prevFlag = new int[N + 1][2];
            int[][] packSize = new int[N + 1][2];

            // 初始化
            for (int i = 0; i <= N; i++) {
                dp[i][0] = Integer.MAX_VALUE / 2;
                dp[i][1] = Integer.MAX_VALUE / 2;
            }
            dp[0][0] = 0;

            // 动态规划计算
            for (int i = 1; i <= N; i++) {
                for (int k = Math.max(1, i - high_C + 1); k <= i; k++) {
                    int packLength = i - k + 1;
                    int currentMaxB = maxB[k - 1][i - 1];
                    int packCost = pack_size * packLength * currentMaxB + 5 + C;

                    if (packLength < low_C) {
                        // 小分组，不能改变状态
                        if (dp[k - 1][0] + packCost < dp[i][0]) {
                            dp[i][0] = dp[k - 1][0] + packCost;
                            prevState[i][0] = k - 1;
                            prevFlag[i][0] = 0;
                            packSize[i][0] = packLength;
                        }
                        if (dp[k - 1][1] + packCost < dp[i][1]) {
                            dp[i][1] = dp[k - 1][1] + packCost;
                            prevState[i][1] = k - 1;
                            prevFlag[i][1] = 1;
                            packSize[i][1] = packLength;
                        }
                    } else {
                        // 大分组，可以改变状态到1
                        if (dp[k - 1][0] + packCost < dp[i][1]) {
                            dp[i][1] = dp[k - 1][0] + packCost;
                            prevState[i][1] = k - 1;
                            prevFlag[i][1] = 0;
                            packSize[i][1] = packLength;
                        }
                        if (dp[k - 1][1] + packCost < dp[i][1]) {
                            dp[i][1] = dp[k - 1][1] + packCost;
                            prevState[i][1] = k - 1;
                            prevFlag[i][1] = 1;
                            packSize[i][1] = packLength;
                        }
                    }
                }
            }

            // 回溯构建分组方案
            if (dp[N][1] < minTotalCost) {
                minTotalCost = dp[N][1];
                bestC = C;
                bestGroupSizes = new ArrayList<>();
                bestGroupBitWidths = new ArrayList<>();

                // 回溯路径
                int i = N;
                int flag = 1;
                while (i > 0) {
                    int size = packSize[i][flag];
                    int start = i - size;
                    int bitWidth = maxB[start][i - 1];

                    bestGroupSizes.add(0, size);
                    bestGroupBitWidths.add(0, bitWidth);

                    i = prevState[i][flag];
                    flag = prevFlag[i + size][flag]; // 注意这里要调整索引
                }
            }
        }

        return new PackingPlan(bestC, bestGroupSizes, bestGroupBitWidths, minTotalCost);
    }

    // 计算压缩后数据总大小
    private static int calculateTotalCompressedSize(PackingPlan plan, int pack_size) {
        int totalSize = 0;

        // 元数据头大小: C(5bits) + 分组数量(32bits)
        totalSize += 5; // C占用5bits
        totalSize += 32; // 分组数量占用32bits

        // 每个分组的元数据: packsize(plan.optimalC bits) + 位宽(5bits)
        int groupMetadataBits = plan.groupSizes.size() * (plan.optimalC + 5);
        totalSize += groupMetadataBits;

        // 压缩数据大小
        for (int i = 0; i < plan.groupSizes.size(); i++) {
            int groupBlocks = plan.groupSizes.get(i);
            int bitWidth = plan.groupBitWidths.get(i);
            int dataBits = groupBlocks * pack_size * bitWidth;
            totalSize += dataBits;
        }

        // 转换为字节数（向上取整）
        return (totalSize + 7) / 8;
    }

    // 写入元数据头信息
    private static int writeMetadataHeader(byte[] compressedData, int currentPos,
                                           PackingPlan plan, int pack_size) {
        int bitPos = 0;
        int bytePos = currentPos;

        // 写入C (5 bits)
        writeBits(compressedData, bytePos, bitPos, plan.optimalC, 5);
        bitPos += 5;
        if (bitPos >= 8) {
            bytePos += bitPos / 8;
            bitPos = bitPos % 8;
        }

        // 写入分组数量 (32 bits)
        writeBits(compressedData, bytePos, bitPos, plan.groupSizes.size(), 32);
        bytePos += 4; // 32 bits = 4 bytes

        return bytePos;
    }

    // 压缩数据块
    private static int compressDataBlocks(byte[] compressedData, int currentPos,
                                          long[] paddedArray, PackingPlan plan, int pack_size) {
        int bitPos = 0;
        int bytePos = currentPos;
        int dataIndex = 0;

        for (int groupIdx = 0; groupIdx < plan.groupSizes.size(); groupIdx++) {
            int groupBlocks = plan.groupSizes.get(groupIdx);
            int bitWidth = plan.groupBitWidths.get(groupIdx);

            // 写入当前分组的packsize (optimalC bits)
            writeBits(compressedData, bytePos, bitPos, groupBlocks, plan.optimalC);
            bitPos += plan.optimalC;
            if (bitPos >= 8) {
                bytePos += bitPos / 8;
                bitPos = bitPos % 8;
            }

            // 写入当前分组的位宽 (5 bits)
            writeBits(compressedData, bytePos, bitPos, bitWidth, 5);
            bitPos += 5;
            if (bitPos >= 8) {
                bytePos += bitPos / 8;
                bitPos = bitPos % 8;
            }

            // 压缩当前分组的数据
            int groupDataCount = groupBlocks * pack_size;
            ArrayList<Integer> dataToPack = new ArrayList<>();
            for (int i = 0; i < groupDataCount; i++) {
                dataToPack.add((int) paddedArray[dataIndex++]); // 注意：这里假设数据在int范围内
            }

            // 使用bitpacking压缩
            bytePos = bitPacking(dataToPack, 0, bitWidth, bytePos, compressedData, bitPos);
            bitPos = 0; // bitPacking会处理位对齐
        }

        return bytePos;
    }

    // 写入指定位数的辅助函数
    private static void writeBits(byte[] data, int bytePos, int bitPos, int value, int numBits) {
        for (int i = numBits - 1; i >= 0; i--) {
            int bit = (value >> i) & 1;
            data[bytePos] |= (bit << (7 - bitPos));
            bitPos++;
            if (bitPos == 8) {
                bytePos++;
                bitPos = 0;
            }
        }
    }

    // 修改后的bitPacking方法，支持指定的起始位位置
    public static int bitPacking(ArrayList<Integer> numbers, int start, int bit_width,
                                 int encode_pos, byte[] encoded_result, int startBitPos) {
        int block_num = (numbers.size() - start) / 8;
        int currentBytePos = encode_pos;
        int currentBitPos = startBitPos;

        for (int i = 0; i < block_num; i++) {
            // 对每个8值块进行压缩
            for (int j = 0; j < 8; j++) {
                int value = numbers.get(start + i * 8 + j);

                // 按位写入
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
        }

        return currentBytePos;
    }

    // 解压缩方法
    public static long[] decompressWithOptimalPacking(byte[] compressedData, int originalLength, int pack_size) {
        int bytePos = 0;
        int bitPos = 0;

        // 读取元数据头
        int C = readBits(compressedData, bytePos, bitPos, 5);
        bitPos += 5;
        if (bitPos >= 8) {
            bytePos += bitPos / 8;
            bitPos = bitPos % 8;
        }

        int groupCount = readBits(compressedData, bytePos, bitPos, 32);
        bytePos += 4;

        long[] result = new long[originalLength];
        int resultIndex = 0;

        for (int groupIdx = 0; groupIdx < groupCount; groupIdx++) {
            // 读取分组元数据
            int groupBlocks = readBits(compressedData, bytePos, bitPos, C);
            bitPos += C;
            if (bitPos >= 8) {
                bytePos += bitPos / 8;
                bitPos = bitPos % 8;
            }

            int bitWidth = readBits(compressedData, bytePos, bitPos, 5);
            bitPos += 5;
            if (bitPos >= 8) {
                bytePos += bitPos / 8;
                bitPos = bitPos % 8;
            }

            // 解压缩当前分组数据
            int groupDataCount = groupBlocks * pack_size;
            ArrayList<Integer> decompressed = decompressBitPacking(compressedData, bytePos, bitPos,
                    bitWidth, groupDataCount);

            for (int value : decompressed) {
                if (resultIndex < originalLength) {
                    result[resultIndex++] = value;
                }
            }

            // 更新位置
            int totalBits = groupDataCount * bitWidth;
            bytePos += totalBits / 8;
            bitPos = totalBits % 8;
        }

        return result;
    }

    // 读取指定位数的辅助函数
    private static int readBits(byte[] data, int bytePos, int bitPos, int numBits) {
        int result = 0;
        for (int i = 0; i < numBits; i++) {
            int bit = (data[bytePos] >> (7 - bitPos)) & 1;
            result = (result << 1) | bit;
            bitPos++;
            if (bitPos == 8) {
                bytePos++;
                bitPos = 0;
            }
        }
        return result;
    }

    // 修改后的解压缩方法
    public static ArrayList<Integer> decompressBitPacking(byte[] encoded, int decode_byte_pos, int decode_bit_pos,
                                                          int bit_width, int block_size) {
        ArrayList<Integer> result_list = new ArrayList<>();
        int currentBytePos = decode_byte_pos;
        int currentBitPos = decode_bit_pos;

        for (int i = 0; i < block_size; i++) {
            int value = 0;

            for (int bit = 0; bit < bit_width; bit++) {
                int currentBit = (encoded[currentBytePos] >> (7 - currentBitPos)) & 1;
                value = (value << 1) | currentBit;
                currentBitPos++;

                if (currentBitPos == 8) {
                    currentBytePos++;
                    currentBitPos = 0;
                }
            }

            result_list.add(value);
        }

        return result_list;
    }
}