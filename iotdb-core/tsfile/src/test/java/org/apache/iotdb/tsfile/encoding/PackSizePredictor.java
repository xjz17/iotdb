package org.apache.iotdb.tsfile.encoding;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.attribute.Remove;
import weka.classifiers.Evaluation;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class PackSizePredictor {
    static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data", "test.csv","POI-lat.csv",
            "POI-lon.csv","Basel-wind.csv","Basel-temp.csv","Air-sensor.csv");
    private static final int CHUNK_SIZE = 1024;

    // 需要的10个特征的索引（从0开始）
    private static final int[] SELECTED_FEATURE_INDICES = {0, 1, 3, 4, 7, 8, 9, 10, 11, 12};
    private static final String[] SELECTED_FEATURE_NAMES = {
            "mean_bitwidth",
            "median_bitwidth",
            "std_bitwidth",
            "mean_diff_bitwidth",
            "std_diff_bitwidth",
            "avg_run_length",
            "local_max_density",
            "local_max_amplitude",
            "entropy",
            "monotonic_segments"
    };

    // 归一化参数
    private static final double NORMALIZATION_FACTOR = 256.0;

    public static int getCount(long long1, int mask) {
        return ((int) (long1 & mask));
    }

    public static void pack8Values(ArrayList<Integer> values, int offset, int width, int encode_pos,
                                   byte[] encoded_result) {
        int bufIdx = 0;
        int valueIdx = offset;
        int leftBit = 0;

        while (valueIdx < 8 + offset) {
            int buffer = 0;
            int leftSize = 32;

            if (leftBit > 0) {
                buffer |= (values.get(valueIdx) << (32 - leftBit));
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
        int totalBits = 0;
        int valueIdx = 0;

        while (valueIdx < 8) {
            while (totalBits < width) {
                buffer = (buffer << 8) | (encoded[byteIdx] & 0xFF);
                byteIdx++;
                totalBits += 8;
            }

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
        int block_num = Math.min((numbers.size() - start) / 8, 1);
        for (int i = 0; i < block_num; i++) {
            pack8Values(numbers, start + i * 8, bit_width, encode_pos, encoded_result);
            encode_pos += bit_width;
        }
        return encode_pos;
    }

    /**
     * 解码函数 - 支持可变大小的数据块
     */
    public static int[] decodeBitPacking(byte[] compressedData, int[] bitWidths, int pack_size, int originalLength) {
        int[] result = new int[originalLength];
        int resultIndex = 0;
        int decodePos = 0;

        for (int group = 0; group < bitWidths.length && resultIndex < originalLength; group++) {
            int bitWidth = compressedData[decodePos++] & 0xFF;

            // 计算这个数据块中需要解码的值数量
            int valuesInGroup = Math.min(pack_size, originalLength - resultIndex);

            // 对于每个数据块，按照8个值一组进行解码
            for (int blockStart = 0; blockStart < valuesInGroup; blockStart += 8) {
                // 当前块中实际需要解码的值数量（最多8个）
                int valuesInBlock = Math.min(8, valuesInGroup - blockStart);

                // 解码8个值
                ArrayList<Integer> blockData = new ArrayList<>(8);
                unpack8Values(compressedData, decodePos, bitWidth, blockData);

                // 将解码的值复制到结果数组中
                for (int i = 0; i < valuesInBlock && resultIndex < originalLength; i++) {
                    result[resultIndex++] = blockData.get(i);
                }

                // 移动到下一个数据块
                decodePos += bitWidth;
            }
        }

        return result;
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
     * 优化的编码函数 - 支持可变大小的数据块
     */
    public static byte[] encodeBitPacking(int[] paddedArray, int[] bitWidths, int pack_size) {
        // 计算总字节数
        int totalGroups = bitWidths.length;

        // 计算编码后数据的预估大小
        int totalBytes = totalGroups; // 每个组的位宽占用1个字节

        for (int group = 0; group < totalGroups; group++) {
            int bitWidth = bitWidths[group];
            // 每个组包含多个8个值的块
            int blocksInGroup = (pack_size + 7) / 8; // 向上取整
            totalBytes += bitWidth * blocksInGroup;
        }

        byte[] encodedResult = new byte[totalBytes];
        int encodePos = 0;

        for (int group = 0; group < totalGroups; group++) {
            int startIndex = group * pack_size;

            // 写入位宽
            encodedResult[encodePos++] = (byte) bitWidths[group];

            // 将这个数据块分成多个8个值的小块进行编码
            for (int blockStart = 0; blockStart < pack_size; blockStart += 8) {
                int actualBlockSize = Math.min(8, paddedArray.length - (startIndex + blockStart));

                // 收集当前块的值
                ArrayList<Integer> blockData = new ArrayList<>(8);
                for (int i = 0; i < 8; i++) {
                    int idx = startIndex + blockStart + i;
                    if (idx < paddedArray.length) {
                        blockData.add(paddedArray[idx]);
                    } else {
                        blockData.add(0); // 用0填充不足的部分
                    }
                }

                // 编码这个8个值的块
                encodePos = bitPacking(blockData, 0, bitWidths[group], encodePos, encodedResult);
            }
        }

        // 裁剪到实际大小
        byte[] finalResult = new byte[encodePos];
        System.arraycopy(encodedResult, 0, finalResult, 0, encodePos);

        return finalResult;
    }

    /**
     * 训练随机森林模型来预测optimal pack size（只使用10个特征）
     */
    public static void trainRandomForestModel10Features(String datasetPath, String modelPath, double testSplitRatio) throws Exception {
        System.out.println("Loading dataset from: " + datasetPath);

        // 1. 加载CSV数据
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(datasetPath));
        Instances data = loader.getDataSet();

        // 对pack_size列进行归一化（除以256）
        int classIndex = data.numAttributes() - 1;
        for (int i = 0; i < data.numInstances(); i++) {
            double originalValue = data.instance(i).value(classIndex);
            double normalizedValue = originalValue / NORMALIZATION_FACTOR;
            data.instance(i).setValue(classIndex, normalizedValue);
        }

        // 2. 只保留选定的10个特征和类别属性
        int totalAttributes = data.numAttributes();
        List<Integer> indicesToKeep = new ArrayList<>();
        for (int idx : SELECTED_FEATURE_INDICES) {
            indicesToKeep.add(idx);
        }
        indicesToKeep.add(totalAttributes - 1); // 添加类别列

        // 找出需要移除的列
        List<Integer> indicesToRemove = new ArrayList<>();
        for (int i = 0; i < totalAttributes; i++) {
            if (!indicesToKeep.contains(i)) {
                indicesToRemove.add(i + 1); // Weka索引从1开始
            }
        }

        // 如果没有需要移除的列，直接使用原始数据
        if (!indicesToRemove.isEmpty()) {
            Remove removeFilter = new Remove();
            removeFilter.setAttributeIndicesArray(indicesToRemove.stream().mapToInt(i->i).toArray());
            removeFilter.setInputFormat(data);
            data = Filter.useFilter(data, removeFilter);
        }

        System.out.println("Selected features: " + Arrays.toString(SELECTED_FEATURE_NAMES));
        System.out.println("Number of features after selection: " + (data.numAttributes() - 1));

        // 3. 设置类别属性（最后一列为类别）
        classIndex = data.numAttributes() - 1;
        data.setClassIndex(classIndex);

        System.out.println("Dataset loaded successfully!");
        System.out.println("Number of instances: " + data.numInstances());
        System.out.println("Number of attributes: " + data.numAttributes());
        System.out.println("Class attribute: " + data.classAttribute().name());

        // 4. 将类别属性转换为名义属性（分类问题）
        NumericToNominal filter = new NumericToNominal();
        filter.setAttributeIndices("last");
        filter.setInputFormat(data);
        Instances filteredData = Filter.useFilter(data, filter);

        // 显示类别分布
        System.out.println("\nClass distribution:");
        int[] classCounts = new int[filteredData.classAttribute().numValues()];
        for (int i = 0; i < filteredData.numInstances(); i++) {
            int classValue = (int) filteredData.instance(i).classValue();
            classCounts[classValue]++;
        }
        for (int i = 0; i < classCounts.length; i++) {
            String className = filteredData.classAttribute().value(i);
            double originalValue = Double.parseDouble(className) * NORMALIZATION_FACTOR;
            System.out.println("  " + className + " (original: " + (int)originalValue + "): " + classCounts[i] + " instances");
        }

        // 5. 分割数据集为训练集和测试集
        filteredData.randomize(new Random(42));
        int trainSize = (int) Math.round(filteredData.numInstances() * (1 - testSplitRatio));
        int testSize = filteredData.numInstances() - trainSize;

        Instances trainData = new Instances(filteredData, 0, trainSize);
        Instances testData = new Instances(filteredData, trainSize, testSize);

        System.out.println("\nTraining set size: " + trainData.numInstances());
        System.out.println("Test set size: " + testData.numInstances());

        // 6. 创建并配置随机森林分类器（小型模型设置）
        RandomForest randomForest = new RandomForest();
        randomForest.setNumIterations(10);
        randomForest.setNumFeatures(4);
        randomForest.setMaxDepth(10);
        randomForest.setBagSizePercent(50);
        randomForest.setSeed(42);
        randomForest.setNumExecutionSlots(1);

        System.out.println("\nTraining small Random Forest model...");
        System.out.println("Parameters: ");
        System.out.println("  Number of trees (iterations): " + randomForest.getNumIterations());
        System.out.println("  Number of features: " + randomForest.getNumFeatures());
        System.out.println("  Max depth: " + randomForest.getMaxDepth());
        System.out.println("  Bag size percent: " + randomForest.getBagSizePercent());

        // 7. 训练模型
        long startTime = System.currentTimeMillis();
        randomForest.buildClassifier(trainData);
        long trainingTime = System.currentTimeMillis() - startTime;
        System.out.println("Training completed in " + trainingTime + " ms");

        // 8. 在训练集上进行评估
        System.out.println("\nEvaluating model on training set...");
        Evaluation trainEval = new Evaluation(trainData);
        trainEval.evaluateModel(randomForest, trainData);
        System.out.println("Training set accuracy: " +
                String.format("%.2f", trainEval.pctCorrect()) + "%");

        // 9. 在测试集上评估
        System.out.println("\nEvaluating model on test set...");
        Evaluation testEval = new Evaluation(trainData);
        testEval.evaluateModel(randomForest, testData);

        System.out.println("Test set accuracy: " +
                String.format("%.2f", testEval.pctCorrect()) + "%");

        // 10. 显示详细的分类报告
        System.out.println("\nDetailed classification report:");
        System.out.println(testEval.toClassDetailsString());

        // 11. 显示混淆矩阵
        System.out.println("Confusion matrix:");
        double[][] confusionMatrix = testEval.confusionMatrix();
        for (int i = 0; i < confusionMatrix.length; i++) {
            System.out.print("Class " + filteredData.classAttribute().value(i) + ": ");
            for (int j = 0; j < confusionMatrix[i].length; j++) {
                System.out.print(String.format("%.0f ", confusionMatrix[i][j]));
            }
            System.out.println();
        }

        // 12. 显示其他评估指标
        System.out.println("\nOther evaluation metrics:");
        System.out.println("Kappa statistic: " + String.format("%.4f", testEval.kappa()));
        System.out.println("Mean absolute error: " + String.format("%.4f", testEval.meanAbsoluteError()));
        System.out.println("Root mean squared error: " + String.format("%.4f", testEval.rootMeanSquaredError()));

        // 13. 交叉验证
        System.out.println("\nPerforming 10-fold cross-validation on training set...");
        Evaluation crossValEval = new Evaluation(trainData);
        crossValEval.crossValidateModel(randomForest, trainData, 10, new Random(42));
        System.out.println("Cross-validation accuracy: " +
                String.format("%.2f", crossValEval.pctCorrect()) + "%");

        // 14. 保存模型和训练数据的结构
        System.out.println("\nSaving model and data structure...");
        weka.core.SerializationHelper.write(modelPath, randomForest);

        // 保存训练数据的结构（用于预测时创建一致的实例）
        String headerPath = modelPath.replace(".model", "_header.arff");
        weka.core.SerializationHelper.write(headerPath, trainData);

        // 保存类别信息（归一化后的值）
        String classInfoPath = modelPath.replace(".model", "_classinfo.txt");
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(classInfoPath))) {
            writer.println("Number of classes: " + filteredData.classAttribute().numValues());
            writer.println("Normalization factor: " + NORMALIZATION_FACTOR);
            for (int i = 0; i < filteredData.classAttribute().numValues(); i++) {
                writer.println(filteredData.classAttribute().value(i));
            }
        }
        System.out.println("Model saved successfully!");
        System.out.println("Header saved to: " + headerPath);
        System.out.println("Class info saved to: " + classInfoPath);
    }

    /**
     * 使用训练好的模型进行预测（10个特征版本）
     */
    public static int predictPackSize10Features(String modelPath, double[] features) throws Exception {
        if (features.length != 10) {
            throw new IllegalArgumentException("Expected 10 features, got " + features.length);
        }

        // 1. 加载模型
        Classifier classifier = (Classifier) weka.core.SerializationHelper.read(modelPath);

        // 2. 加载训练数据的结构（header）
        String headerPath = modelPath.replace(".model", "_header.arff");
        Instances header;
        try {
            header = (Instances) weka.core.SerializationHelper.read(headerPath);
        } catch (Exception e) {
            throw new Exception("Failed to load header file: " + headerPath + ", " + e.getMessage());
        }

        // 3. 使用header的结构创建新的数据集（只包含一个实例）
        Instances dataset = new Instances(header);
        dataset.delete();

        // 4. 创建实例
        DenseInstance instance = new DenseInstance(11);
        instance.setDataset(dataset);

        // 设置特征值（顺序必须与训练时一致）
        for (int i = 0; i < 10; i++) {
            instance.setValue(i, features[i]);
        }

        // 添加到数据集
        dataset.add(instance);

        // 5. 进行预测
        double prediction = classifier.classifyInstance(dataset.instance(0));
        String predictedClass = dataset.classAttribute().value((int) prediction);

        // 6. 加载归一化因子
        String classInfoPath = modelPath.replace(".model", "_classinfo.txt");
        double normalizationFactor = NORMALIZATION_FACTOR;

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(classInfoPath))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (lineNum == 1) {
                    continue;
                } else if (lineNum == 2) {
                    if (line.startsWith("Normalization factor: ")) {
                        normalizationFactor = Double.parseDouble(line.substring("Normalization factor: ".length()));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load class info file, using default normalization factor");
        }

        // 7. 将预测的归一化值反归一化并转换为8的倍数
        double normalizedValue = Double.parseDouble(predictedClass);
        double originalValue = normalizedValue * normalizationFactor;

        int intValue = (int) Math.round(originalValue);
        intValue = Math.max(8, intValue);
        intValue = Math.min(256, intValue);
        intValue = ((intValue + 4) / 8) * 8;

        return intValue;
    }

    /**
     * 计算10个选定的特征（从完整的14个特征中提取）
     */
    public static double[] computeSelectedFeatures(int[] bitWidths) {
        // 先计算所有14个特征
        double[] allFeatures = computeBitWidthFeatures(bitWidths);

        // 提取选定的10个特征
        double[] selectedFeatures = new double[10];
        for (int i = 0; i < SELECTED_FEATURE_INDICES.length; i++) {
            selectedFeatures[i] = allFeatures[SELECTED_FEATURE_INDICES[i]];
        }

        return selectedFeatures;
    }

    public static double[] computeBitWidthFeatures(int[] bitWidths) {
        int n = bitWidths.length;
        if (n == 0) {
            return new double[14];
        }

        double[] features = new double[14];

        // 1. 位宽的平均值/64
        double sum = 0;
        for (int bw : bitWidths) {
            sum += bw;
        }
        features[0] = sum / (n * 64.0);

        // 2. 位宽的中位数/64
        int[] sortedBitWidths = bitWidths.clone();
        Arrays.sort(sortedBitWidths);
        double median;
        if (n % 2 == 0) {
            median = (sortedBitWidths[n/2 - 1] + sortedBitWidths[n/2]) / 2.0;
        } else {
            median = sortedBitWidths[n/2];
        }
        features[1] = median / 64.0;

        // 3. 位宽的极差/64
        int minBw = sortedBitWidths[0];
        int maxBw = sortedBitWidths[n-1];
        features[2] = (maxBw - minBw) / 64.0;

        // 4. 位宽的标准差/64
        double mean = sum / n;
        double variance = 0;
        for (int bw : bitWidths) {
            variance += Math.pow(bw - mean, 2);
        }
        variance /= n;
        double stdDev = Math.sqrt(variance);
        features[3] = stdDev / 64.0;

        // 5-8. 位宽差分特征
        if (n > 1) {
            int[] diffs = new int[n-1];
            for (int i = 0; i < n-1; i++) {
                diffs[i] = Math.abs(bitWidths[i+1] - bitWidths[i]);
            }

            // 5. 位宽差分绝对均值/64
            double diffSum = 0;
            for (int diff : diffs) {
                diffSum += diff;
            }
            features[4] = diffSum / ((n-1) * 64.0);

            // 6. 位宽差分中位数/64
            int[] sortedDiffs = diffs.clone();
            Arrays.sort(sortedDiffs);
            double diffMedian;
            if ((n-1) % 2 == 0) {
                diffMedian = (sortedDiffs[(n-1)/2 - 1] + sortedDiffs[(n-1)/2]) / 2.0;
            } else {
                diffMedian = sortedDiffs[(n-1)/2];
            }
            features[5] = diffMedian / 64.0;

            // 7. 位宽差分极差/64
            int minDiff = sortedDiffs[0];
            int maxDiff = sortedDiffs[n-2];
            features[6] = (maxDiff - minDiff) / 64.0;

            // 8. 位宽差分标准差/64
            double diffMean = diffSum / (n-1);
            double diffVariance = 0;
            for (int diff : diffs) {
                diffVariance += Math.pow(diff - diffMean, 2);
            }
            diffVariance /= (n-1);
            double diffStdDev = Math.sqrt(diffVariance);
            features[7] = diffStdDev / 64.0;
        } else {
            features[4] = 0;
            features[5] = 0;
            features[6] = 0;
            features[7] = 0;
        }

        // 9. 位宽游程平均长度/(n/8)
        double avgRunLength = 0;
        int runCount = 0;
        int currentRunLength = 1;
        for (int i = 1; i < n; i++) {
            if (bitWidths[i] == bitWidths[i-1]) {
                currentRunLength++;
            } else {
                avgRunLength += currentRunLength;
                runCount++;
                currentRunLength = 1;
            }
        }
        avgRunLength += currentRunLength;
        runCount++;
        avgRunLength /= runCount;
        features[8] = avgRunLength / (n / 8.0);

        // 10. 位宽局部最大值密度（中间值大于左右值的个数）
        int localMaxCount = 0;
        for (int i = 1; i < n-1; i++) {
            if (bitWidths[i] > bitWidths[i-1] && bitWidths[i] > bitWidths[i+1]) {
                localMaxCount++;
            }
        }
        features[9] = localMaxCount / (double)Math.max(1, n-2);

        // 11. 位宽局部最大值的平均相对幅度（每个局部最大值减相邻值的平均值）
        double totalRelativeAmplitude = 0;
        int localMaxWithAmplitude = 0;
        for (int i = 1; i < n-1; i++) {
            if (bitWidths[i] > bitWidths[i-1] && bitWidths[i] > bitWidths[i+1]) {
                double leftDiff = bitWidths[i] - bitWidths[i-1];
                double rightDiff = bitWidths[i] - bitWidths[i+1];
                totalRelativeAmplitude += (leftDiff + rightDiff) / 2.0;
                localMaxWithAmplitude++;
            }
        }
        features[10] = (localMaxWithAmplitude > 0) ? (totalRelativeAmplitude / localMaxWithAmplitude) / 64.0 : 0;

        // 12. 位宽熵
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        for (int bw : bitWidths) {
            frequencyMap.put(bw, frequencyMap.getOrDefault(bw, 0) + 1);
        }
        double entropy = 0;
        for (int count : frequencyMap.values()) {
            double probability = count / (double)n;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        features[11] = entropy / 6.0;

        // 13. 位宽单调段数量比例
        int monotonicSegments = 1;
        Boolean isIncreasing = null;
        for (int i = 1; i < n; i++) {
            if (bitWidths[i] > bitWidths[i-1]) {
                if (isIncreasing == null || !isIncreasing) {
                    monotonicSegments++;
                    isIncreasing = true;
                }
            } else if (bitWidths[i] < bitWidths[i-1]) {
                if (isIncreasing == null || isIncreasing) {
                    monotonicSegments++;
                    isIncreasing = false;
                }
            }
        }
        features[12] = monotonicSegments / (double)n;

        // 14. 高位宽比例（大于32的比例）
        int highBitWidthCount = 0;
        for (int bw : bitWidths) {
            if (bw > 32) {
                highBitWidthCount++;
            }
        }
        features[13] = highBitWidthCount / (double)n;

        return features;
    }

    public static int predictPackSizeOptimized(int[] scaledInts, String modelPath) {
        try {
            // 1. 缓存模型（只加载一次）
            ModelCache.loadModel(modelPath);

            // 2. 快速计算位宽（每8个值一组）
            int groupSize = 8;
            int numGroups = (scaledInts.length + groupSize - 1) / groupSize;
            int[] bitWidthsPerGroup = new int[numGroups];

            for (int i = 0; i < numGroups; i++) {
                int start = i * groupSize;
                int end = Math.min(start + groupSize, scaledInts.length);
                int maxInGroup = 0;

                for (int j = start; j < end; j++) {
                    int val = scaledInts[j];
                    if (val > maxInGroup) maxInGroup = val;
                }

                bitWidthsPerGroup[i] = 64 - Long.numberOfLeadingZeros(Math.max(1, maxInGroup));
            }

            // 3. 计算简化特征（10个）
            double[] features = computeSelectedFeatures(bitWidthsPerGroup);

            // 4. 使用缓存的模型预测
            int predictedPackSize = ModelCache.predict(features);

            return Math.max(8, predictedPackSize);

        } catch (Exception e) {
            System.err.println("ML prediction failed, using default: " + e.getMessage());
            return 8;
        }
    }

    /**
     * 使用机器学习模型预测最优pack size（10个特征版本）
     */
    public static int predictOptimalPackSizeML10Features(int[] scaledInts, String modelPath) {
        try {
            // 1. 计算每8个值的位宽序列
            int groupSize = 8;
            int numGroups = (scaledInts.length + groupSize - 1) / groupSize;
            int[] bitWidthsPerGroup = new int[numGroups];

            for (int i = 0; i < numGroups; i++) {
                int start = i * groupSize;
                int end = Math.min(start + groupSize, scaledInts.length);

                int maxInGroup = 0;
                for (int j = start; j < end; j++) {
                    if (scaledInts[j] > maxInGroup) {
                        maxInGroup = scaledInts[j];
                    }
                }

                int bitWidth = 64 - Long.numberOfLeadingZeros(Math.max(1, maxInGroup));
                bitWidthsPerGroup[i] = bitWidth;
            }

            // 2. 计算10个选定的特征
            double[] selectedFeatures = computeSelectedFeatures(bitWidthsPerGroup);

            // 3. 使用模型预测
            int predictedPackSize = predictPackSizeSimple10Features(modelPath, selectedFeatures);

            // 确保是8的倍数
            predictedPackSize = Math.max(8, predictedPackSize);

            return predictedPackSize;

        } catch (Exception e) {
            System.err.println("ML prediction failed, using default: " + e.getMessage());
            e.printStackTrace();
            return 8;
        }
    }

    public static class ModelCache {
        private static Classifier cachedClassifier = null;
        private static Instances cachedHeader = null;
        private static double cachedNormalizationFactor = 256.0;

        public static synchronized void loadModel(String modelPath) throws Exception {
            if (cachedHeader == null) {
                String headerPath = modelPath.replace(".model", "_header.arff");
                File hf = new File(headerPath);
                if (hf.exists()) {
                    cachedHeader = (Instances) weka.core.SerializationHelper.read(headerPath);
                }
                String classInfoPath = modelPath.replace(".model", "_classinfo.txt");
                File cf = new File(classInfoPath);
                if (cf.exists()) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(classInfoPath))) {
                        String line;
                        int lineNum = 0;
                        while ((line = reader.readLine()) != null) {
                            lineNum++;
                            if (lineNum == 2 && line.startsWith("Normalization factor: ")) {
                                cachedNormalizationFactor = Double.parseDouble(line.substring("Normalization factor: ".length()));
                                break;
                            }
                        }
                    }
                }
            }
        }

        public static int predict(double[] features) throws Exception {
            if (cachedClassifier == null) {
                throw new IllegalStateException("Model not loaded");
            }
            Instances dataset = new Instances(cachedHeader);
            DenseInstance instance = new DenseInstance(features.length + 1);
            instance.setDataset(dataset);
            for (int i = 0; i < features.length; i++) {
                instance.setValue(i, features[i]);
            }
            dataset.add(instance);
            double prediction = cachedClassifier.classifyInstance(dataset.instance(0));
            String predictedClass = dataset.classAttribute().value((int) prediction);
            double normalizedValue = Double.parseDouble(predictedClass);
            double originalValue = normalizedValue * cachedNormalizationFactor;
            int intValue = (int) Math.round(originalValue);
            intValue = Math.max(8, Math.min(256, intValue));
            intValue = ((intValue + 4) / 8) * 8;
            return intValue;
        }
    }

    /**
     * 将 Weka 数据集写成 libsvm (sparse) 格式
     */
    private static void writeInstancesAsLibSVM(Instances data, File outFile, Map<String, Integer> classMap) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
            int numAttrs = data.numAttributes();
            int numFeatures = numAttrs - 1;
            for (int i = 0; i < data.numInstances(); i++) {
                weka.core.Instance inst = data.instance(i);
                String classValStr = inst.stringValue(numAttrs - 1);
                Integer label = classMap.get(classValStr);
                if (label == null) {
                    throw new IOException("Unknown class value: " + classValStr);
                }
                StringBuilder sb = new StringBuilder();
                sb.append(label);
                for (int f = 0; f < numFeatures; f++) {
                    double v = inst.value(f);
                    if (Double.isNaN(v)) continue;
                    sb.append(' ').append(f + 1).append(':').append(v);
                }
                bw.write(sb.toString());
                bw.newLine();
            }
        }
    }

    /**
     * 运行外部命令
     */
    private static String runCommand(List<String> cmd, File workingDir, boolean printOutput) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workingDir != null) pb.directory(workingDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
                if (printOutput) {
                    System.out.println(line);
                }
            }
        }
        int rc = p.waitFor();
        if (rc != 0) {
            throw new IOException("Command failed (rc=" + rc + "): " + String.join(" ", cmd) + "\nOutput:\n" + out.toString());
        }
        return out.toString();
    }

    /**
     * 使用 LightGBM CLI 训练模型（10 特征）
     */
    public static void trainLightGBMModel10Features(String datasetPath, String modelPath, double testSplitRatio) throws Exception {
        System.out.println("Loading dataset from: " + datasetPath);

        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(datasetPath));
        Instances data = loader.getDataSet();

        int classIndex = data.numAttributes() - 1;
        for (int i = 0; i < data.numInstances(); i++) {
            double originalValue = data.instance(i).value(classIndex);
            double normalizedValue = originalValue / NORMALIZATION_FACTOR;
            data.instance(i).setValue(classIndex, normalizedValue);
        }

        int totalAttributes = data.numAttributes();
        List<Integer> indicesToKeep = new ArrayList<>();
        for (int idx : SELECTED_FEATURE_INDICES) indicesToKeep.add(idx);
        indicesToKeep.add(totalAttributes - 1);

        List<Integer> indicesToRemove = new ArrayList<>();
        for (int i = 0; i < totalAttributes; i++) {
            if (!indicesToKeep.contains(i)) {
                indicesToRemove.add(i + 1);
            }
        }
        if (!indicesToRemove.isEmpty()) {
            Remove removeFilter = new Remove();
            removeFilter.setAttributeIndicesArray(indicesToRemove.stream().mapToInt(i -> i).toArray());
            removeFilter.setInputFormat(data);
            data = Filter.useFilter(data, removeFilter);
        }

        classIndex = data.numAttributes() - 1;
        data.setClassIndex(classIndex);

        NumericToNominal nt = new NumericToNominal();
        nt.setAttributeIndices("last");
        nt.setInputFormat(data);
        Instances filteredData = Filter.useFilter(data, nt);

        filteredData.randomize(new Random(42));
        int trainSize = (int) Math.round(filteredData.numInstances() * (1 - testSplitRatio));
        int testSize = filteredData.numInstances() - trainSize;
        Instances trainData = new Instances(filteredData, 0, trainSize);
        Instances testData = new Instances(filteredData, trainSize, testSize);

        System.out.println("Train size: " + trainData.numInstances() + ", Test size: " + testData.numInstances());

        Attribute classAttr = trainData.classAttribute();
        int numClasses = classAttr.numValues();
        Map<String, Integer> classMap = new LinkedHashMap<>();
        for (int i = 0; i < numClasses; i++) {
            classMap.put(classAttr.value(i), i);
        }

        File modelFile = new File(modelPath);
        File parent = modelFile.getParentFile();
        if (parent == null) parent = new File(".");
        if (!parent.exists()) parent.mkdirs();

        File trainFile = new File(parent, "lightgbm_train.libsvm");
        File validFile = new File(parent, "lightgbm_valid.libsvm");
        writeInstancesAsLibSVM(trainData, trainFile, classMap);
        writeInstancesAsLibSVM(testData, validFile, classMap);

        File trainConf = new File(parent, "lightgbm_train.conf");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(trainConf), StandardCharsets.UTF_8))) {
            w.write("task = train\n");
            w.write("data = " + trainFile.getAbsolutePath() + "\n");
            w.write("valid = " + validFile.getAbsolutePath() + "\n");
            w.write("objective = multiclass\n");
            w.write("num_class = " + numClasses + "\n");
            w.write("metric = multi_logloss,multi_error\n");
            w.write("num_iterations = 100\n");
            w.write("learning_rate = 0.1\n");
            w.write("num_leaves = 31\n");
            w.write("max_depth = -1\n");
            w.write("verbosity = -1\n");  // 设置verbosity为-1，关闭所有输出
            w.write("output_model = " + modelFile.getAbsolutePath() + "\n");
        }

        System.out.println("Training LightGBM model via CLI...");
        List<String> cmd = Arrays.asList("lightgbm", "config=" + trainConf.getAbsolutePath());
        String out;
        try {
            out = runCommand(cmd, parent, true);  // 训练时打印输出
        } catch (Exception e) {
            System.err.println("LightGBM training failed: " + e.getMessage());
            throw e;
        }
        System.out.println("LightGBM training completed");

        String headerPath = modelPath.replace(".model", "_header.arff");
        weka.core.SerializationHelper.write(headerPath, trainData);

        String classInfoPath = modelPath.replace(".model", "_classinfo.txt");
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(classInfoPath))) {
            writer.println("Number of classes: " + numClasses);
            writer.println("Normalization factor: " + NORMALIZATION_FACTOR);
            for (int i = 0; i < classAttr.numValues(); i++) {
                writer.println(classAttr.value(i));
            }
        }
        System.out.println("Model saved to: " + modelFile.getAbsolutePath());
        System.out.println("Header saved to: " + headerPath);
        System.out.println("Class info saved to: " + classInfoPath);
    }

    /**
     * 使用 LightGBM CLI 进行批量预测（不使用deleteOnExit，手动清理临时文件）
     */
    public static int[] predictWithLightGBMCLI(String modelPath, List<double[]> featuresBatch) throws Exception {
        File modelFile = new File(modelPath);
        File parent = modelFile.getParentFile();
        if (parent == null) parent = new File(".");

        // 读取 classinfo
        String classInfoPath = modelPath.replace(".model", "_classinfo.txt");
        List<String> classValues = new ArrayList<>();
        double normalizationFactor = NORMALIZATION_FACTOR;
        File classInfoFile = new File(classInfoPath);
        if (!classInfoFile.exists()) {
            throw new FileNotFoundException("classinfo file not found: " + classInfoPath);
        }
        try (BufferedReader r = new BufferedReader(new FileReader(classInfoFile))) {
            String line;
            int lineNum = 0;
            while ((line = r.readLine()) != null) {
                lineNum++;
                if (lineNum <= 2) continue;
                classValues.add(line.trim());
            }
            // 解析归一化因子
            try (BufferedReader r2 = new BufferedReader(new FileReader(classInfoFile))) {
                String l2;
                int ln = 0;
                while ((l2 = r2.readLine()) != null) {
                    ln++;
                    if (ln == 2 && l2.startsWith("Normalization factor: ")) {
                        normalizationFactor = Double.parseDouble(l2.substring("Normalization factor: ".length()));
                        break;
                    }
                }
            } catch (Exception e) {
                // 使用默认值
            }
        }

        // 创建临时文件（不使用deleteOnExit）
        File tmpData = null;
        File predOut = null;
        File predictConf = null;

        try {
            // 创建临时文件
            tmpData = File.createTempFile("lgbm_predict_data_", ".libsvm", parent);
            predOut = File.createTempFile("lgbm_pred_out_", ".txt", parent);

            // 写入特征数据
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpData), StandardCharsets.UTF_8))) {
                for (double[] feats : featuresBatch) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("0"); // 临时 label
                    for (int i = 0; i < feats.length; i++) {
                        double v = feats[i];
                        if (Double.isNaN(v)) continue;
                        sb.append(' ').append(i + 1).append(':').append(v);
                    }
                    bw.write(sb.toString());
                    bw.newLine();
                }
            }

            // 生成 predict config
            predictConf = File.createTempFile("lightgbm_predict_", ".conf", parent);
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(predictConf), StandardCharsets.UTF_8))) {
                w.write("task = predict\n");
                w.write("data = " + tmpData.getAbsolutePath() + "\n");
                w.write("model_in = " + modelFile.getAbsolutePath() + "\n");
                w.write("output_result = " + predOut.getAbsolutePath() + "\n");
                w.write("verbosity = -1\n");  // 设置verbosity为-1，关闭所有输出
            }

            // 调用 lightgbm predict
            List<String> cmd = Arrays.asList("lightgbm", "config=" + predictConf.getAbsolutePath());
            String runOut = runCommand(cmd, parent, false);  // 预测时不打印输出

            // 解析 predOut
            int[] ret = new int[featuresBatch.size()];
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(predOut), StandardCharsets.UTF_8))) {
                String line;
                int idx = 0;
                while ((line = r.readLine()) != null && idx < featuresBatch.size()) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        idx++;
                        continue;
                    }
                    String[] tokens = line.split("\\s+|,");
                    int best = 0;
                    double bestVal = Double.NEGATIVE_INFINITY;
                    for (int k = 0; k < tokens.length; k++) {
                        double v = Double.parseDouble(tokens[k]);
                        if (v > bestVal) {
                            bestVal = v;
                            best = k;
                        }
                    }
                    if (best < 0 || best >= classValues.size()) {
                        throw new IOException("Prediction index out of range: " + best + ", classValuesSize=" + classValues.size());
                    }
                    double normalizedValue = Double.parseDouble(classValues.get(best));
                    double originalValue = normalizedValue * normalizationFactor;
                    int intValue = (int) Math.round(originalValue);
                    intValue = Math.max(8, Math.min(256, intValue));
                    intValue = ((intValue + 4) / 8) * 8;
                    ret[idx] = intValue;
                    idx++;
                }
            }

            return ret;

        } finally {
            // 手动清理临时文件
            try {
                if (tmpData != null && tmpData.exists()) tmpData.delete();
            } catch (Exception e) {
                // 忽略删除失败
            }
            try {
                if (predOut != null && predOut.exists()) predOut.delete();
            } catch (Exception e) {
                // 忽略删除失败
            }
            try {
                if (predictConf != null && predictConf.exists()) predictConf.delete();
            } catch (Exception e) {
                // 忽略删除失败
            }
        }
    }

    /**
     * 训练模型（替代：trainRandomForestModel10Features）
     */
    public static void trainModel10Features(String datasetPath, String modelPath, double testSplitRatio) throws Exception {
        trainLightGBMModel10Features(datasetPath, modelPath, testSplitRatio);
    }

    /**
     * 简化版本的预测函数（10 个特征版本）
     */
    public static int predictPackSizeSimple10Features(String modelPath, double[] features) {
        try {
            List<double[]> batch = new ArrayList<>();
            batch.add(features);
            int[] res = predictWithLightGBMCLI(modelPath, batch);
            return res[0];
        } catch (Exception e) {
            System.err.println("LightGBM prediction failed: " + e.getMessage());
            e.printStackTrace();
            return 8;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("\nPerformance Testing...");
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/output_BP_RF_10F";
        String modelPath = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/random_forest_10features.model";
        File outputDir = new File(outputDirstr);
        String datasetPath = "/Users/xiaojinzhao/Documents/GitHub/encoding-pack-size/dataset.csv";
        double testSplitRatio = 0.2;

        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            System.out.println("Training ML model with 10 features...");
            System.out.println("TRAINING RANDOM FOREST MODEL WITH 10 FEATURES");
            trainLightGBMModel10Features(datasetPath, modelPath, testSplitRatio);
        }

        if (!outputDir.exists()) outputDir.mkdir();
        File dir = new File(directory);
        for (File file : Objects.requireNonNull(dir.listFiles())) {

            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;
            System.out.println(file.getName());
            String Output = outputDirstr + "/" + file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);

            // 更新表头，增加解压吞吐率列
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

            int batchSize = 1024;
            List<int[]> batches = new ArrayList<>();

            for (int i = 0; i < numbers.size(); i += batchSize) {
                int end = Math.min(numbers.size(), i + batchSize);
                List<String> batch = numbers.subList(i, end);
                int[] scaledBatch = scaleNumbers(batch, decimalMax);
                batches.add(scaledBatch);
            }

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
                    int[] scaledInts = new int[end - i];
                    if (end - i >= 0) System.arraycopy(scaledInts_all, i, scaledInts, 0, end - i);

                    long startTime = System.nanoTime();

                    // 使用机器学习模型预测最优pack_size（10个特征版本）
                    int pack_size = predictOptimalPackSizeML10Features(scaledInts, modelPath);

                    pack_size = Math.max(8, pack_size);

                    int remainder = scaledInts.length % 8;
                    int paddingLength = (remainder == 0) ? 0 : 8 - remainder;

                    int[] paddedArray = new int[scaledInts.length + paddingLength];
                    System.arraycopy(scaledInts, 0, paddedArray, 0, scaledInts.length);
                    int actual_length = paddedArray.length;
                    int num_of_pack_size = (actual_length + pack_size - 1) / pack_size;
                    int[] bitWidths = new int[num_of_pack_size];

                    for (int scaledInts_i = 0; scaledInts_i < actual_length; scaledInts_i += pack_size) {
                        int maxInGroup = 0;
                        int end_index = Math.min(scaledInts_i + pack_size, actual_length);
                        for (int scaledInts_j = scaledInts_i; scaledInts_j < end_index; scaledInts_j++) {
                            if (paddedArray[scaledInts_j] > maxInGroup) {
                                maxInGroup = paddedArray[scaledInts_j];
                            }
                        }

                        int bitWidth = 64 - Long.numberOfLeadingZeros(Math.max(1, maxInGroup));
                        bitWidths[scaledInts_i / pack_size] = bitWidth;
                    }

                    byte[] compressedData = encodeBitPacking(paddedArray, bitWidths, pack_size);
                    long cur_cost = compressedData.length * 8L;
                    long duration = System.nanoTime() - startTime;
                    modelTime += (duration);
                    modelCost += cur_cost;

                    long startDecodeTime = System.nanoTime();
                    int[] decodedData = decodeBitPacking(compressedData, bitWidths, pack_size, scaledInts.length);
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
                    "BP+RF-10F",
                    String.valueOf(modelTime_throughput),
                    String.valueOf(modelDecodeTime_throughput),
                    String.valueOf(numbers.size()),
                    String.valueOf(modelCost),
                    String.valueOf(model_ratio)
            };
            writer.writeRecord(record);
            writer.close();

            System.out.println("Encoding throughput: " + modelTime_throughput + " MB/s");
            System.out.println("Decoding throughput: " + modelDecodeTime_throughput + " MB/s");
            System.out.println("Compression ratio: " + model_ratio);
        }
    }
}