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

public class SubcolumnQueryEqualNewTest {

    public static void Query(byte[] encoded_result, int target) {
        int encodePos = 0;

        // 解析数据长度
        int data_length = ((encoded_result[encodePos] & 0xFF) << 24)
                | ((encoded_result[encodePos + 1] & 0xFF) << 16)
                | ((encoded_result[encodePos + 2] & 0xFF) << 8)
                | (encoded_result[encodePos + 3] & 0xFF);
        encodePos += 4;

        // 解析块大小
        int block_size = ((encoded_result[encodePos] & 0xFF) << 24)
                | ((encoded_result[encodePos + 1] & 0xFF) << 16)
                | ((encoded_result[encodePos + 2] & 0xFF) << 8)
                | (encoded_result[encodePos + 3] & 0xFF);
        encodePos += 4;

        int num_blocks = data_length / block_size;

        // 查询结果
        int[] result = new int[data_length];
        int[] result_length = new int[1];

        // 处理每个块
        for (int i = 0; i < num_blocks; i++) {
            encodePos = BlockQueryIndex(encoded_result, i, block_size,
                    block_size, encodePos, target,
                    result, result_length);
        }

        // 处理剩余部分
        int remainder = data_length % block_size;

        if (remainder <= 3) {
            for (int i = 0; i < remainder; i++) {
                int value = SubcolumnTest.bytes2Integer(encoded_result, encodePos, 4);
                encodePos += 4;
                if (value == target) {
                    result[result_length[0]] = value;
                    result_length[0]++;
                }
            }
        } else {
            encodePos = BlockQueryIndex(encoded_result, num_blocks, block_size,
                    remainder, encodePos, target, result, result_length);
        }
    }

    public static int BlockQueryIndex(byte[] encoded_result, int block_index, int block_size, int remainder,
            int encodePos, int target, int[] result, int[] result_length) {

        // 读取最小增量
        int minDelta0 = SubcolumnTest.bytes2Integer(encoded_result, encodePos, 4);
        encodePos += 4;

        int m = encoded_result[encodePos] & 0xFF;
        encodePos += 1;

        target -= minDelta0; // 调整目标值

        // 初始化候选索引
        int[] candidate_indices = new int[remainder];
        int candidate_length = remainder;
        for (int i = 0; i < remainder; i++) {
            candidate_indices[i] = i;
        }

        if (m == 0) {
            if (target == 0) {
                for (int i = 0; i < remainder; i++) {
                    result[result_length[0]] = block_size * block_index + i;
                    result_length[0]++;
                }
            }
            return encodePos;
        }

        int bw = SubcolumnTest.bitWidth(block_size);
        int beta = encoded_result[encodePos] & 0xFF;
        encodePos += 1;

        int l = (m + beta - 1) / beta;
        int[] bitWidthList = new int[l];
        encodePos = SubcolumnTest.decodeBitPacking(encoded_result, encodePos, 8, l, bitWidthList);

        int[] encodingType = new int[l];
        encodePos = SubcolumnTest.decodeBitPacking(encoded_result, encodePos, 1, l, encodingType);

        // 处理每个子列
        for (int i = l - 1; i >= 0; i--) {
            int type = encodingType[i];
            if (type == 0) { // 类型 0：plain bit-packed
                if (target < 0) {
                    long bitPos = ((long) encodePos) * 8L + (long) bitWidthList[i] * (long) remainder;
                    encodePos = (int) ((bitPos + 7) / 8);
                    continue;
                }

                long bitPos = ((long) encodePos) * 8L;
                int new_length = 0;

                for (int j = 0; j < candidate_length; j++) {
                    int index = candidate_indices[j];
                    int subValue = SubcolumnTest.bytesToInt(encoded_result, (int) (bitPos + index * bitWidthList[i]),
                            bitWidthList[i]);
                    int expectedValue = (target >> (i * beta)) & ((1 << beta) - 1);
                    if (subValue == expectedValue) {
                        candidate_indices[new_length++] = index;
                    }
                }

                candidate_length = new_length;
                bitPos += (long) remainder * bitWidthList[i];
                encodePos = (int) ((bitPos + 7) / 8);

            } else { // 类型 1：RLE + bitpacked values
                int index = ((encoded_result[encodePos] & 0xFF) << 8) | (encoded_result[encodePos + 1] & 0xFF);
                encodePos += 2;

                if (target < 0) {
                    long bitPos = ((long) encodePos) * 8L + (long) bw * index;
                    encodePos = (int) ((bitPos + 7) / 8);
                    bitPos = ((long) encodePos) * 8L + (long) bitWidthList[i] * index;
                    encodePos = (int) ((bitPos + 7) / 8);
                    continue;
                }

                int[] run_length = new int[index];
                int[] rle_values = new int[index];

                encodePos = SubcolumnTest.decodeBitPacking(encoded_result, encodePos, bw, index, run_length);
                encodePos = SubcolumnTest.decodeBitPacking(encoded_result, encodePos, bitWidthList[i], index,
                        rle_values);

                int new_length = 0;
                int rleIndex = 0;
                int currentPos = 0;
                int expectedValue = (target >> (i * beta)) & ((1 << beta) - 1);

                for (int j = 0; j < candidate_length; j++) {
                    int index_candidate = candidate_indices[j];

                    while (rleIndex < index && currentPos + run_length[rleIndex] <= index_candidate) {
                        currentPos += run_length[rleIndex];
                        rleIndex++;
                    }

                    if (rleIndex < index && rle_values[rleIndex] == expectedValue) {
                        candidate_indices[new_length++] = index_candidate;
                    }
                }

                candidate_length = new_length;
            }
        }

        // 处理匹配的候选索引
        for (int i = 0; i < candidate_length; i++) {
            result[result_length[0]] = block_size * block_index + candidate_indices[i];
            result_length[0]++;
        }

        return encodePos;
    }

}
