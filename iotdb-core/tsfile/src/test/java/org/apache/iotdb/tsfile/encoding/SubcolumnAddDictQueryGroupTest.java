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

public class SubcolumnAddDictQueryGroupTest {

  private static class BlockMeta {
    int minDelta;
    int m;
    int beta;
    int l;
    int[] bitWidthList;
    int[] encodingType;
    int[] segmentPos;
    int[] runCountList;
    int[] cardinalityList;
    int nextPos;
  }

  public static int[] QueryGroupMaxIndex(byte[] encodedResult, int windowSize) {
    int encodePos = 0;
    int dataLength = SubcolumnAddDictPruneNewTest.bytes2Integer(encodedResult, encodePos, 4);
    encodePos += 4;
    int blockSize = SubcolumnAddDictPruneNewTest.bytes2Integer(encodedResult, encodePos, 4);
    encodePos += 4;

    int numBlocks = dataLength / blockSize;
    int remainder = dataLength % blockSize;
    int totalBlocks = numBlocks + (remainder > 0 ? 1 : 0);

    BlockMeta[] metas = new BlockMeta[totalBlocks];
    int[] blockRowCount = new int[totalBlocks];
    for (int i = 0; i < totalBlocks; i++) {
      int rowCount = (i < numBlocks) ? blockSize : remainder;
      if (rowCount == 0) {
        break;
      }
      blockRowCount[i] = rowCount;
      BlockMeta meta = parseBlockMeta(encodedResult, encodePos, blockSize, rowCount);
      metas[i] = meta;
      encodePos = meta.nextPos;
    }

    int groupCount = (dataLength + windowSize - 1) / windowSize;
    int[] maxIndices = new int[groupCount];

    for (int g = 0; g < groupCount; g++) {
      int globalStart = g * windowSize;
      int globalEnd = Math.min(dataLength, globalStart + windowSize);

      int bestIndex = -1;
      int bestValue = Integer.MIN_VALUE;

      int startBlock = globalStart / blockSize;
      int endBlock = (globalEnd - 1) / blockSize;
      for (int b = startBlock; b <= endBlock; b++) {
        int rowCount = blockRowCount[b];
        int blockStart = b * blockSize;
        int localStart = Math.max(0, globalStart - blockStart);
        int localEnd = Math.min(rowCount, globalEnd - blockStart);
        if (localStart >= localEnd) {
          continue;
        }

        int localIndex = blockWindowMaxIndex(encodedResult, metas[b], blockSize, localStart, localEnd);
        int value = decodeValueAtLocalIndex(encodedResult, metas[b], blockSize, rowCount, localIndex);
        int globalIndex = blockStart + localIndex;
        if (value > bestValue || (value == bestValue && globalIndex < bestIndex)) {
          bestValue = value;
          bestIndex = globalIndex;
        }
      }

      maxIndices[g] = bestIndex;
    }

    return maxIndices;
  }

  private static BlockMeta parseBlockMeta(byte[] encodedResult, int encodePos, int blockSize, int rowCount) {
    BlockMeta meta = new BlockMeta();
    meta.minDelta = SubcolumnAddDictPruneNewTest.bytes2Integer(encodedResult, encodePos, 4);
    encodePos += 4;
    meta.m = SubcolumnAddDictPruneNewTest.bytes2Integer(encodedResult, encodePos, 1);
    encodePos += 1;

    if (meta.m == 0) {
      meta.nextPos = encodePos;
      return meta;
    }

    meta.beta = SubcolumnAddDictPruneNewTest.bytes2Integer(encodedResult, encodePos, 1);
    encodePos += 1;
    meta.l = (meta.m + meta.beta - 1) / meta.beta;

    meta.bitWidthList = new int[meta.l];
    encodePos =
        SubcolumnAddDictPruneNewTest.decodeBitPacking(
            encodedResult, encodePos, 8, meta.l, meta.bitWidthList);
    meta.encodingType = new int[meta.l];
    encodePos =
        SubcolumnAddDictPruneNewTest.decodeBitPacking(
            encodedResult, encodePos, 2, meta.l, meta.encodingType);

    int bw = SubcolumnAddDictPruneNewTest.bitWidth(blockSize);
    meta.segmentPos = new int[meta.l];
    meta.runCountList = new int[meta.l];
    meta.cardinalityList = new int[meta.l];
    int scanPos = encodePos;

    for (int i = 0; i < meta.l; i++) {
      meta.segmentPos[i] = scanPos;
      int type = meta.encodingType[i];
      int currentBitWidth = meta.bitWidthList[i];
      if (type == 0) {
        long bitPos = ((long) scanPos) * 8L + (long) currentBitWidth * rowCount;
        scanPos = (int) ((bitPos + 7L) / 8L);
      } else if (type == 1) {
        int runCount = ((encodedResult[scanPos] & 0xFF) << 8) | (encodedResult[scanPos + 1] & 0xFF);
        meta.runCountList[i] = runCount;
        scanPos += 2;
        long bitPos = ((long) scanPos) * 8L + (long) runCount * bw;
        scanPos = (int) ((bitPos + 7L) / 8L);
        bitPos = ((long) scanPos) * 8L + (long) runCount * currentBitWidth;
        scanPos = (int) ((bitPos + 7L) / 8L);
      } else {
        int cardinality = ((encodedResult[scanPos] & 0xFF) << 8) | (encodedResult[scanPos + 1] & 0xFF);
        meta.cardinalityList[i] = cardinality;
        scanPos += 2;
        int dictBitWidth = SubcolumnAddDictPruneNewTest.bitWidth(cardinality);
        long bitPos = ((long) scanPos) * 8L + (long) cardinality * currentBitWidth;
        scanPos = (int) ((bitPos + 7L) / 8L);
        bitPos = ((long) scanPos) * 8L + (long) rowCount * dictBitWidth;
        scanPos = (int) ((bitPos + 7L) / 8L);
      }
    }

    meta.nextPos = scanPos;
    return meta;
  }

  private static int blockWindowMaxIndex(
      byte[] encodedResult, BlockMeta meta, int blockSize, int localStart, int localEnd) {
    if (meta.m == 0) {
      return localStart;
    }

    int[] candidate = new int[localEnd - localStart];
    int candidateLength = 0;
    for (int i = localStart; i < localEnd; i++) {
      candidate[candidateLength++] = i;
    }
    int bw = SubcolumnAddDictPruneNewTest.bitWidth(blockSize);

    for (int level = meta.l - 1; level >= 0; level--) {
      if (candidateLength <= 1) {
        break;
      }

      int type = meta.encodingType[level];
      int currentBitWidth = meta.bitWidthList[level];
      int maxPart = Integer.MIN_VALUE;
      int[] next = new int[candidateLength];
      int nextLen = 0;

      if (type == 0) {
        long bitStart = ((long) meta.segmentPos[level]) * 8L;
        for (int j = 0; j < candidateLength; j++) {
          int index = candidate[j];
          int value =
              SubcolumnAddDictPruneNewTest.bytesToInt(
                  encodedResult, (int) (bitStart + (long) index * currentBitWidth), currentBitWidth);
          if (value > maxPart) {
            maxPart = value;
            nextLen = 0;
            next[nextLen++] = index;
          } else if (value == maxPart) {
            next[nextLen++] = index;
          }
        }
      } else if (type == 1) {
        int runCount = meta.runCountList[level];
        int pos = meta.segmentPos[level] + 2;
        int[] runEnd = new int[runCount];
        int[] rleValues = new int[runCount];
        pos = SubcolumnAddDictPruneNewTest.decodeBitPacking(encodedResult, pos, bw, runCount, runEnd);
        SubcolumnAddDictPruneNewTest.decodeBitPacking(
            encodedResult, pos, currentBitWidth, runCount, rleValues);

        int runIdx = 0;
        for (int j = 0; j < candidateLength; j++) {
          int index = candidate[j];
          while (runIdx < runCount && runEnd[runIdx] <= index) {
            runIdx++;
          }
          if (runIdx >= runCount) {
            break;
          }
          int value = rleValues[runIdx];
          if (value > maxPart) {
            maxPart = value;
            nextLen = 0;
            next[nextLen++] = index;
          } else if (value == maxPart) {
            next[nextLen++] = index;
          }
        }
      } else {
        int cardinality = meta.cardinalityList[level];
        int pos = meta.segmentPos[level] + 2;
        int dictBitWidth = SubcolumnAddDictPruneNewTest.bitWidth(cardinality);
        int[] dictKey = new int[cardinality];
        int[] dictIndex = new int[localEnd];
        pos =
            SubcolumnAddDictPruneNewTest.decodeBitPacking(
                encodedResult, pos, currentBitWidth, cardinality, dictKey);
        SubcolumnAddDictPruneNewTest.decodeBitPacking(
            encodedResult, pos, dictBitWidth, localEnd, dictIndex);

        for (int j = 0; j < candidateLength; j++) {
          int index = candidate[j];
          int value = dictKey[dictIndex[index]];
          if (value > maxPart) {
            maxPart = value;
            nextLen = 0;
            next[nextLen++] = index;
          } else if (value == maxPart) {
            next[nextLen++] = index;
          }
        }
      }

      candidate = next;
      candidateLength = nextLen;
    }

    int best = candidate[0];
    for (int i = 1; i < candidateLength; i++) {
      if (candidate[i] < best) {
        best = candidate[i];
      }
    }
    return best;
  }

  private static int decodeValueAtLocalIndex(
      byte[] encodedResult, BlockMeta meta, int blockSize, int rowCount, int localIndex) {
    if (meta.m == 0) {
      return meta.minDelta;
    }
    int bw = SubcolumnAddDictPruneNewTest.bitWidth(blockSize);
    int value = 0;
    for (int level = 0; level < meta.l; level++) {
      int type = meta.encodingType[level];
      int currentBitWidth = meta.bitWidthList[level];
      int part;
      if (type == 0) {
        long bitStart = ((long) meta.segmentPos[level]) * 8L;
        part =
            SubcolumnAddDictPruneNewTest.bytesToInt(
                encodedResult, (int) (bitStart + (long) localIndex * currentBitWidth), currentBitWidth);
      } else if (type == 1) {
        int runCount = meta.runCountList[level];
        int pos = meta.segmentPos[level] + 2;
        int[] runEnd = new int[runCount];
        int[] rleValues = new int[runCount];
        pos = SubcolumnAddDictPruneNewTest.decodeBitPacking(encodedResult, pos, bw, runCount, runEnd);
        SubcolumnAddDictPruneNewTest.decodeBitPacking(
            encodedResult, pos, currentBitWidth, runCount, rleValues);
        int runIdx = 0;
        while (runIdx < runCount && runEnd[runIdx] <= localIndex) {
          runIdx++;
        }
        part = (runIdx < runCount) ? rleValues[runIdx] : 0;
      } else {
        int cardinality = meta.cardinalityList[level];
        int pos = meta.segmentPos[level] + 2;
        int dictBitWidth = SubcolumnAddDictPruneNewTest.bitWidth(cardinality);
        int[] dictKey = new int[cardinality];
        int[] dictIndex = new int[rowCount];
        pos =
            SubcolumnAddDictPruneNewTest.decodeBitPacking(
                encodedResult, pos, currentBitWidth, cardinality, dictKey);
        SubcolumnAddDictPruneNewTest.decodeBitPacking(
            encodedResult, pos, dictBitWidth, rowCount, dictIndex);
        part = dictKey[dictIndex[localIndex]];
      }
      value |= (part << (level * meta.beta));
    }
    return value + meta.minDelta;
  }

  public static int getDecimalPrecision(String str) {
    int decimalIndex = str.indexOf('.');
    if (decimalIndex == -1) {
      return 0;
    }
    return str.length() - decimalIndex - 1;
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
    String parentDir = "D://github/xjz17/subcolumn/";
    String inputParentDir = parentDir + "dataset/";
    String outputParentDir = parentDir + "result/";
    String outputPath = outputParentDir + "subcolumn_adddict_prunenew_query_group_max.csv";

    int blockSize = 512;
    int repeatTime = 100;
    int windowSize = 200;
    System.out.println("Output: " + outputPath);
    System.out.println("Block size: " + blockSize);
    System.out.println("Repeat time: " + repeatTime);
    System.out.println("Window size: " + windowSize);

    CsvWriter writer = new CsvWriter(outputPath, ',', StandardCharsets.UTF_8);
    writer.setRecordDelimiter('\n');
    writer.writeRecord(
        new String[] {
          "Dataset",
          "Encoding Algorithm",
          "Encoding Time",
          "Decoding Time",
          "Points",
          "Compressed Size",
          "Compression Ratio"
        });

    File directory = new File(inputParentDir);
    File[] csvFiles = directory.listFiles((dir, name) -> name.endsWith(".csv"));
    if (csvFiles == null) {
      writer.close();
      return;
    }

    for (File file : csvFiles) {
      String datasetName = extractFileName(file.toString());
      System.out.println(datasetName);

      InputStream inputStream = Files.newInputStream(file.toPath());
      CsvReader loader = new CsvReader(inputStream, StandardCharsets.UTF_8);
      ArrayList<Float> data = new ArrayList<>();

      int maxDecimal = 0;
      while (loader.readRecord()) {
        String fStr = loader.getValues()[0];
        if (fStr.isEmpty()) {
          continue;
        }
        int currentDecimal = getDecimalPrecision(fStr);
        if (currentDecimal > maxDecimal) {
          maxDecimal = currentDecimal;
        }
        data.add(Float.valueOf(fStr));
      }
      inputStream.close();

      if (maxDecimal > 8) {
        maxDecimal = 8;
      }
      System.out.println("maxDecimal: " + maxDecimal);

      int[] dataArr = new int[data.size()];
      int maxMul = (int) Math.pow(10, maxDecimal);
      for (int i = 0; i < data.size(); i++) {
        dataArr[i] = (int) (data.get(i) * maxMul);
      }

      byte[] encodedResult = new byte[dataArr.length * 8];
      int length = 0;

      long start = System.nanoTime();
      for (int repeat = 0; repeat < repeatTime; repeat++) {
        length = SubcolumnAddDictPruneNewTest.Encoder(dataArr, blockSize, encodedResult);
      }
      long end = System.nanoTime();
      long encodeTime = (end - start) / repeatTime;

      System.out.println("GroupMaxQuery");
      int[] groupMaxIndices = null;
      start = System.nanoTime();
      for (int repeat = 0; repeat < repeatTime; repeat++) {
        groupMaxIndices = QueryGroupMaxIndex(encodedResult, windowSize);
      }
      end = System.nanoTime();
      long queryTime = (end - start) / repeatTime;

      System.out.println("groupCount: " + (groupMaxIndices == null ? 0 : groupMaxIndices.length));
      double compressionRatio = length / (double) (data.size() * Long.BYTES);
      writer.writeRecord(
          new String[] {
            datasetName,
            "SubcolumnAddDictPruneNew",
            String.valueOf(encodeTime),
            String.valueOf(queryTime),
            String.valueOf(data.size()),
            String.valueOf(length),
            String.valueOf(compressionRatio)
          });
      System.out.println("compressionRatio: " + compressionRatio);
    }

    writer.close();
  }
}
