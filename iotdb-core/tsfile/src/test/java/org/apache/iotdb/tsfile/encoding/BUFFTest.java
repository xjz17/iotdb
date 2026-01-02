package org.apache.iotdb.tsfile.encoding;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.Test;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;

import static org.junit.Assert.assertEquals;

public class BUFFTest {

    public static int bitWidth(int value) {
        return 32 - Integer.numberOfLeadingZeros(value);
    }

    public static int bitWidth(long value) {
        return 64 - Long.numberOfLeadingZeros(value);
    }

    public static void intToBytes(int srcNum, byte[] result, int pos, int width) {
        int cnt = pos & 0x07;
        int index = pos >> 3;
        while (width > 0) {
            int m = width + cnt >= 8 ? 8 - cnt : width;
            width -= m;
            int mask = 1 << (8 - cnt);
            cnt += m;
            byte y = (byte) (srcNum >>> width);
            y = (byte) (y << (8 - cnt));
            mask = ~(mask - (1 << (8 - cnt)));
            result[index] = (byte) (result[index] & mask | y);
            srcNum = srcNum & ~(-1 << width);
            if (cnt == 8) {
                index++;
                cnt = 0;
            }
        }
    }

    public static int bytesToInt(byte[] result, int pos, int width) {
        int ret = 0;
        int cnt = pos & 0x07;
        int index = pos >> 3;
        while (width > 0) {
            int m = width + cnt >= 8 ? 8 - cnt : width;
            width -= m;
            ret = ret << m;
            byte y = (byte) (result[index] & (0xff >> cnt));
            y = (byte) ((y & 0xff) >>> (8 - cnt - m));
            ret = ret | (y & 0xff);
            cnt += m;
            if (cnt == 8) {
                cnt = 0;
                index++;
            }
        }
        return ret;
    }

    public static void longToBytes(long srcNum, byte[] result, int pos, int width) {
        int cnt = pos & 0x07;
        int index = pos >> 3;

        while (width > 0) {
            int m = width + cnt >= 8 ? 8 - cnt : width;
            width -= m;
            int mask = 1 << (8 - cnt);
            cnt += m;
            byte y = (byte) (srcNum >>> width);
            y = (byte) (y << (8 - cnt));
            mask = ~(mask - (1 << (8 - cnt)));
            result[index] = (byte) (result[index] & mask | y);
            srcNum = srcNum & ~(-1L << width);
            if (cnt == 8) {
                index++;
                cnt = 0;
            }
        }
    }

    public static long bytesToLong(byte[] result, int pos, int width) {
        long ret = 0;
        int cnt = pos & 0x07;
        int index = pos >> 3;
        while (width > 0) {
            int m = width + cnt >= 8 ? 8 - cnt : width;
            width -= m;
            ret = ret << m;
            byte y = (byte) (result[index] & (0xff >> cnt));
            y = (byte) ((y & 0xff) >>> (8 - cnt - m));
            ret = ret | (y & 0xff);
            cnt += m;
            if (cnt == 8) {
                cnt = 0;
                index++;
            }
        }
        return ret;
    }

    public static void pack8Values(int[] values, int offset, int width, int encode_pos,
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
                buffer |= (values[valueIdx] << (32 - leftBit));
                leftSize -= leftBit;
                leftBit = 0;
                valueIdx++;
            }

            while (leftSize >= width && valueIdx < 8 + offset) {
                // encode one Integer to the 'buffer'
                buffer |= (values[valueIdx] << (leftSize - width));
                leftSize -= width;
                valueIdx++;
            }
            // If the remaining space of the buffer can not save the bits for one Integer,
            if (leftSize > 0 && valueIdx < 8 + offset) {
                // put the first 'leftSize' bits of the Integer into remaining space of the
                // buffer
                buffer |= (values[valueIdx] >>> (width - leftSize));
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

    public static void pack8Values(
            long[] values, int offset, int width, int encode_pos, byte[] encoded_result) {
        int bufIdx = 0;
        int valueIdx = offset;
        // remaining bits for the current unfinished Long
        int leftBit = 0;

        while (valueIdx < 8 + offset) {
            // buffer is used for saving 64 bits as a part of result
            long buffer = 0;
            // remaining size of bits in the 'buffer'
            int leftSize = 64;

            // encode the left bits of current Long to 'buffer'
            if (leftBit > 0) {
                buffer |= (values[valueIdx] << (64 - leftBit));
                leftSize -= leftBit;
                leftBit = 0;
                valueIdx++;
            }

            while (leftSize >= width && valueIdx < 8 + offset) {
                // encode one Long to the 'buffer'
                buffer |= (values[valueIdx] << (leftSize - width));
                leftSize -= width;
                valueIdx++;
            }
            // If the remaining space of the buffer can not save the bits for one Long
            if (leftSize > 0 && valueIdx < 8 + offset) {
                // put the first 'leftSize' bits of the Long into remaining space of the buffer
                buffer |= (values[valueIdx] >>> (width - leftSize));
                leftBit = width - leftSize;
            }

            // put the buffer into the final result
            for (int j = 0; j < 8; j++) {
                encoded_result[encode_pos] = (byte) ((buffer >>> ((7 - j) * 8)) & 0xFF);
                encode_pos++;
                bufIdx++;
                if (bufIdx >= width) {
                    return;
                }
            }
        }
    }

    public static void unpack8Values(byte[] encoded, int offset, int width, int[] result_list, int result_offset) {
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
                // result_list.add((int) (buffer >>> (totalBits - width)));
                result_list[result_offset + valueIdx] = (int) (buffer >>> (totalBits - width));
                valueIdx++;
                totalBits -= width;
                buffer = buffer & ((1L << totalBits) - 1);
            }
        }
    }

    public static void unpack8Values(
            byte[] encoded, int offset, int width, long[] result_list, int result_offset) {
        int byteIdx = offset;
        long buffer = 0;
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
                // result_list.add((int) (buffer >>> (totalBits - width)));
                result_list[result_offset + valueIdx] = buffer >>> (totalBits - width);
                valueIdx++;
                totalBits -= width;
                buffer = buffer & ((1L << totalBits) - 1);
            }
        }
    }

    public static int bitPacking(int[] numbers, int bit_width, int encode_pos,
                                 byte[] encoded_result, int num_values) {
        int block_num = num_values / 8;
        int remainder = num_values % 8;

        for (int i = 0; i < block_num; i++) {
            pack8Values(numbers, i * 8, bit_width, encode_pos, encoded_result);
            encode_pos += bit_width;
        }

        encode_pos *= 8;

        for (int i = 0; i < remainder; i++) {
            intToBytes(numbers[block_num * 8 + i], encoded_result, encode_pos, bit_width);
            encode_pos += bit_width;
        }

        return (encode_pos + 7) / 8;
    }

    public static int bitPacking(long[] numbers, int bit_width, int encode_pos,
                                 byte[] encoded_result, int num_values) {
        int block_num = num_values / 8;
        int remainder = num_values % 8;

        for (int i = 0; i < block_num; i++) {
            pack8Values(numbers, i * 8, bit_width, encode_pos, encoded_result);
            encode_pos += bit_width;
        }

        encode_pos *= 8;

        for (int i = 0; i < remainder; i++) {
            longToBytes(numbers[block_num * 8 + i], encoded_result, encode_pos, bit_width);
            encode_pos += bit_width;
        }

        return (encode_pos + 7) / 8;
    }

    public static int decodeBitPacking(
            byte[] encoded, int decode_pos, int bit_width, int num_values, int[] result_list) {
        // ArrayList<Integer> result_list = new ArrayList<>();
        // int[] result_list = new int[num_values];
        int block_num = num_values / 8;
        int remainder = num_values % 8;

        for (int i = 0; i < block_num; i++) { // bitpacking
            unpack8Values(encoded, decode_pos, bit_width, result_list, i * 8);
            decode_pos += bit_width;
        }

        decode_pos *= 8;

        for (int i = 0; i < remainder; i++) {
            result_list[block_num * 8 + i] = bytesToInt(encoded, decode_pos, bit_width);
            decode_pos += bit_width;
        }

        return (decode_pos + 7) / 8;
    }

    public static int decodeBitPacking(
            byte[] encoded, int decode_pos, int bit_width, int num_values, long[] result_list) {
        int block_num = num_values / 8;
        int remainder = num_values % 8;

        for (int i = 0; i < block_num; i++) {
            unpack8Values(encoded, decode_pos, bit_width, result_list, i * 8);
            decode_pos += bit_width;
        }

        decode_pos *= 8;

        for (int i = 0; i < remainder; i++) {
            result_list[block_num * 8 + i] = bytesToLong(encoded, decode_pos, bit_width);
            decode_pos += bit_width;
        }

        return (decode_pos + 7) / 8;
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

    public static int[] bits_needed = { 0, 5, 8, 11, 15, 18, 21, 25, 28, 31, 35,
            38, 41, 45, 48, 51, 55, 58
    };

    public static int BlockEncoder(double[] data, int block_index, int block_size, int remainder, int max_decimal,
                                   int encode_pos, byte[] encoded_result) {

        long[] sign_bits = new long[remainder];
        long[] integer_parts = new long[remainder];
        long[] decimal_parts = new long[remainder];

        long min_integer_part = Long.MAX_VALUE;
        long max_integer_part = Long.MIN_VALUE;

        // for (int i = 0; i < remainder; i++) {
        // System.out.print(data[block_index * block_size + i] + " ");
        // }
        // System.out.println();

        for (int i = 0; i < remainder; i++) {
            double value = data[block_index * block_size + i];

            if (value < 0) {
                sign_bits[i] = 1;
            }

            long currentInt = (long) Math.abs(value);
            integer_parts[i] = currentInt;

            if (currentInt < min_integer_part) {
                min_integer_part = currentInt;
            }

            if (currentInt > max_integer_part) {
                max_integer_part = currentInt;
            }

            long bits = Double.doubleToLongBits(value);

            // int sign = (bits >> 63) & 1;
            long exponent = (bits >> 52) & 0x7FF;
            long mantissa = bits & (long) ((1L << 52) - 1);

            long actualExponent = exponent - 1023;

            if (actualExponent >= 0) {
                long mask = (1L << (52 - actualExponent)) - 1;
                mantissa &= mask;
            } else {
                mantissa += 1L << 52;
            }

            long shift = 52 - actualExponent - bits_needed[max_decimal];

            if (shift < 0) {
                mantissa <<= -shift;
            } else {
                mantissa >>= shift;
            }

            if (exponent == 0) {
                mantissa = 0;
            }

            decimal_parts[i] = mantissa;
        }

        // encoded_result[encode_pos] = (byte) (min_integer_part >> 24);
        // encoded_result[encode_pos + 1] = (byte) (min_integer_part >> 16);
        // encoded_result[encode_pos + 2] = (byte) (min_integer_part >> 8);
        // encoded_result[encode_pos + 3] = (byte) min_integer_part;
        // encode_pos += 4;

        long2Bytes(min_integer_part, encode_pos, encoded_result);
        encode_pos += 8;

        // System.out.println("min_integer_part: " + min_integer_part);
        // System.out.println("max_integer_part: " + max_integer_part);

        int bw = bitWidth(max_integer_part - min_integer_part);

        encoded_result[encode_pos] = (byte) bw;
        encode_pos += 1;

        for (int i = 0; i < remainder; i++) {
            integer_parts[i] -= min_integer_part;
        }

        // int[] combined = new int[remainder];
        // for (int i = 0; i < remainder; i++) {
        // combined[i] = (sign_bits[i] << (bw + bits_needed[max_decimal])) |
        // (integer_parts[i] << bits_needed[max_decimal]) | decimal_parts[i];
        // }

        // int totalBitWidth = 1 + bw + bits_needed[max_decimal];

        // encode_pos = bitPacking(combined, totalBitWidth, encode_pos, encoded_result,
        // remainder);

        int totalBitWidth = 1 + bw + bits_needed[max_decimal];

        int intArrayCount = (totalBitWidth + 7) / 8;

        long[][] combinedArrays = new long[intArrayCount][remainder];

        for (int i = 0; i < intArrayCount; i++) {
            for (int j = 0; j < remainder; j++) {
                long combined = (sign_bits[j] << (bw + bits_needed[max_decimal]))
                        | (integer_parts[j] << bits_needed[max_decimal]) | decimal_parts[j];
                combinedArrays[i][j] = ((combined >> (i * 8)) & 0xFF);
            }
        }

        for (int i = 0; i < intArrayCount; i++) {
            int currentBitWidth = Math.min(8, totalBitWidth - i * 8);
            encode_pos = bitPacking(combinedArrays[i], currentBitWidth, encode_pos, encoded_result, remainder);
        }

        return encode_pos;
    }

    public static int BlockDecoder(byte[] encoded_result, int block_index, int block_size, int remainder,
                                   int max_decimal, int encode_pos, double[] data) {

        long[] sign_bits = new long[remainder];
        long[] integer_parts = new long[remainder];
        long[] decimal_parts = new long[remainder];

        long min_integer_part = bytes2Long(encoded_result, encode_pos, 8);
        encode_pos += 8;

        // System.out.println("min_integer_part: " + min_integer_part);

        int bw = encoded_result[encode_pos];
        encode_pos += 1;

        // int[] combined = new int[remainder];

        // encode_pos = decodeBitPacking(encoded_result, encode_pos, 1 + bw +
        // bits_needed[max_decimal], remainder, combined);

        // for (int i = 0; i < remainder; i++) {
        // int value = combined[i];
        // sign_bits[i] = (value >> (bw + bits_needed[max_decimal])) & 1;
        // integer_parts[i] = (value >> bits_needed[max_decimal]) & ((1 << bw) - 1);
        // integer_parts[i] += min_integer_part;
        // decimal_parts[i] = value & ((1 << bits_needed[max_decimal]) - 1);
        // }

        int totalBitWidth = 1 + bw + bits_needed[max_decimal];

        int intArrayCount = (totalBitWidth + 7) / 8;

        long[][] combinedArrays = new long[intArrayCount][remainder];

        long[] combined = new long[remainder];

        for (int i = 0; i < intArrayCount; i++) {
            int currentBitWidth = Math.min(8, totalBitWidth - i * 8);
            encode_pos = decodeBitPacking(encoded_result, encode_pos, currentBitWidth, remainder, combinedArrays[i]);
            for (int j = 0; j < remainder; j++) {
                combined[j] |= (combinedArrays[i][j]) << (i * 8);
            }
        }

        for (int i = 0; i < remainder; i++) {
            sign_bits[i] = ((combined[i] >> (bw + bits_needed[max_decimal])) & 1);
            integer_parts[i] = ((combined[i] >> bits_needed[max_decimal]) & ((1 << bw) - 1));
            integer_parts[i] += min_integer_part;
            decimal_parts[i] = (combined[i] & ((1 << bits_needed[max_decimal]) - 1));
        }

        for (int i = 0; i < remainder; i++) {
            double decimal = decimal_parts[i];
            for (int j = 0; j < bits_needed[max_decimal]; j++) {
                decimal /= 2;
            }
            double value = (integer_parts[i] + decimal);
            value = sign_bits[i] == 1 ? -value : value;
            data[block_index * block_size + i] = value;
        }

        // for (int i = 0; i < remainder; i++) {
        // System.out.print(data[block_index * block_size + i] + " ");
        // }
        // System.out.println();

        return encode_pos;
    }

    public static int Encoder(double[] data, int block_size, int max_decimal, byte[] encoded_result) {
        int data_length = data.length;
        int encode_pos = 0;

        encoded_result[0] = (byte) (data_length >> 24);
        encoded_result[1] = (byte) (data_length >> 16);
        encoded_result[2] = (byte) (data_length >> 8);
        encoded_result[3] = (byte) data_length;
        encode_pos += 4;

        encoded_result[4] = (byte) (block_size >> 24);
        encoded_result[5] = (byte) (block_size >> 16);
        encoded_result[6] = (byte) (block_size >> 8);
        encoded_result[7] = (byte) block_size;
        encode_pos += 4;

        encoded_result[8] = (byte) max_decimal;
        encode_pos += 1;

        int num_blocks = data_length / block_size;

        int remainder = data_length % block_size;

        for (int i = 0; i < num_blocks; i++) {
            encode_pos = BlockEncoder(data, i, block_size, block_size, max_decimal, encode_pos, encoded_result);
        }

        if (remainder > 0) {
            encode_pos = BlockEncoder(data, num_blocks, block_size, remainder, max_decimal, encode_pos, encoded_result);
        }

        // if (remainder <= 3) {
        // for (int i = 0; i < remainder; i++) {
        // int value = data[num_blocks * block_size + i];
        // encoded_result[encode_pos] = (byte) (value >> 24);
        // encoded_result[encode_pos + 1] = (byte) (value >> 16);
        // encoded_result[encode_pos + 2] = (byte) (value >> 8);
        // encoded_result[encode_pos + 3] = (byte) value;
        // encode_pos += 4;
        // }
        // } else {
        // encode_pos = BlockEncoder(data, num_blocks, block_size, remainder,
        // max_decimal, encode_pos,
        // encoded_result);
        // }

        return encode_pos;
    }

    public static double[] Decoder(byte[] encoded_result) {
        int encode_pos = 0;

        int data_length = ((encoded_result[encode_pos] & 0xFF) << 24) | ((encoded_result[encode_pos + 1] & 0xFF) << 16)
                |
                ((encoded_result[encode_pos + 2] & 0xFF) << 8) | (encoded_result[encode_pos + 3] & 0xFF);
        encode_pos += 4;

        int block_size = ((encoded_result[encode_pos] & 0xFF) << 24) | ((encoded_result[encode_pos + 1] & 0xFF) << 16) |
                ((encoded_result[encode_pos + 2] & 0xFF) << 8) | (encoded_result[encode_pos + 3] & 0xFF);
        encode_pos += 4;

        int max_decimal = encoded_result[encode_pos];
        encode_pos += 1;

        int num_blocks = data_length / block_size;

        int remainder = data_length % block_size;

        double[] data = new double[data_length];

        for (int i = 0; i < num_blocks; i++) {
            encode_pos = BlockDecoder(encoded_result, i, block_size, block_size, max_decimal, encode_pos, data);
        }

        if (remainder > 0) {
            encode_pos = BlockDecoder(encoded_result, num_blocks, block_size, remainder, max_decimal, encode_pos, data);
        }

        // if (remainder <= 3) {
        // for (int i = 0; i < remainder; i++) {
        // data[num_blocks * block_size + i] = ((encoded_result[encode_pos] & 0xFF) <<
        // 24) |
        // ((encoded_result[encode_pos + 1] & 0xFF) << 16) |
        // ((encoded_result[encode_pos + 2] & 0xFF) << 8) | (encoded_result[encode_pos +
        // 3] & 0xFF);
        // encode_pos += 4;
        // }
        // } else {
        // encode_pos = BlockDecoder(encoded_result, num_blocks, block_size, remainder,
        // encode_pos, data);
        // }

        return data;
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
    private static final List<String> IGNORE_FILES = Arrays.asList(".DS_Store", "full_data","test.csv","POI-lat.csv",
            "POI-lon.csv","Air-sensor.csv","Basel-wind.csv","Basel-temp.csv");
    private static final int CHUNK_SIZE = 1024;
    public static int[] scaleNumbers(List<String> numbers, int decimalMax) {
        int scale = (int) Math.pow(10, decimalMax);
        int size = numbers.size();
        int[] result = new int[size];

        if (size == 0) {
            return result;
        }

        // 1. Parse all numbers and scale them up
        int[] scaledValues = new int[size];
        for (int i = 0; i < size; i++) {
            String numStr = numbers.get(i);
            // Parse the number (handling both "123.456" and "123" cases)
            String[] parts = numStr.split("\\.");
            int whole = Integer.parseInt(parts[0]);

            // Handle fractional part
            int fraction = 0;
            if (parts.length > 1) {
                String fractionStr = parts[1];
                // Pad with zeros if necessary to ensure proper scaling
                if (fractionStr.length() < decimalMax) {
                    while (fractionStr.length() < decimalMax) {
                        fractionStr += "0";
                    }
                } else if (fractionStr.length() > decimalMax) {
                    // Truncate if too many decimal places (alternative could be rounding)
                    fractionStr = fractionStr.substring(0, decimalMax);
                }
                fraction = Integer.parseInt(fractionStr);
            }

            scaledValues[i] = whole * scale + fraction;
        }

//        // 2. Process first element
//        int first = scaledValues[0];
//        result[0] = first;
//
//        // 3. Process subsequent elements with delta + ZigZag encoding
//        int prev = first;
//        for (int i = 1; i < size; i++) {
//            int current = scaledValues[i];
//            int diff = current - prev;
//            result[i] = (diff << 1) ^ (diff >> 31); // ZigZag encoding
//            prev = current;
//        }

        return scaledValues;
    }

    public static void main(String[] args) throws IOException {
        // 示例数据（实际应替换为真实时间序列）
        System.out.println("\nPerformance Testing...");
//        String csvFilePath = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/processed_data.csv";
        String directory = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String outputDirstr = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/output_buff";
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
                    "Decoding Time",
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
            int decimalMax = decimalPlaces.stream().max(Integer::compare).orElse(0);

            int[] scaledInt = scaleNumbers(numbers, decimalMax);
            double[] numbers_list = new double[numbers.size()];
            for (int i = 0; i < numbers.size(); i++) {
                numbers_list[i] = Double.parseDouble(numbers.get(i));
            }
            byte[] encoded_result = new byte[numbers.size() * 8];

            long encodeTime = 0;
            long decodeTime = 0;
            double compressed_size = 0;

            long s = System.nanoTime();
            long length  = 0;
            for (int repeat = 0; repeat < time_of_repeat; repeat++) {
                length = Encoder(numbers_list, CHUNK_SIZE, decimalMax, encoded_result);
            }long e = System.nanoTime();
            encodeTime += ((e - s) / time_of_repeat);
            compressed_size += length;

            System.out.println("Decode");

            double[] data2_arr_decoded = new double[numbers.size()];

            s = System.nanoTime();

            for (int repeat = 0; repeat < time_of_repeat; repeat++) {
                data2_arr_decoded = Decoder(encoded_result);
            }
            e = System.nanoTime();
            decodeTime += ((e - s) / time_of_repeat);

            double model_ratio = (double) compressed_size / (double) (numbers.size()*8);
            double modelTime_throughput = (double)(numbers.size()*8000)/ (double) (encodeTime);
            double modelDecodeTime_throughput = (double)(numbers.size()*8000)/ (double) (decodeTime);
            String[] record = {
                    file.toString(),
                    "BUFF",
                    String.valueOf(modelTime_throughput),
                    String.valueOf(modelDecodeTime_throughput),
                    String.valueOf(numbers.size()),
                    String.valueOf(compressed_size),
                    String.valueOf(model_ratio)
            };
            writer.writeRecord(record);
            writer.close();
//            break;
        }
    }
    @Test
    public void test0() throws IOException {
        String parent_dir = "D:/github/xjz17/subcolumn/";
        // String parent_dir = "D:/encoding-subcolumn/";

//        String input_parent_dir = parent_dir + "dataset/";

//        String output_parent_dir = "D:/encoding-subcolumn/result/";
        // String output_parent_dir = parent_dir + "result/";
        String input_parent_dir = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/ElfTestData_camel";
        String output_parent_dir = "/Users/xiaojinzhao/Documents/GitHub/encoding-block/elf_resources/buff";
//        String outputPath = output_parent_dir + "buff_long0.csv";

        int block_size = 1024;

        // int repeatTime = 100;
        int repeatTime = 500;

        // repeatTime = 1;



        File directory = new File(input_parent_dir);
        // File[] csvFiles = directory.listFiles();
        File[] csvFiles = directory.listFiles((dir, name) -> name.endsWith(".csv"));

        for (File file : csvFiles) {
            if (IGNORE_FILES.contains(file.getName()) || file.isDirectory()) continue;
//            if(!file.getName().equals("Stocks-DE.csv")) continue;
            System.out.println(file.getName());
            String Output = output_parent_dir+"/"+file.getName();
            CsvWriter writer = new CsvWriter(Output, ',', StandardCharsets.UTF_8);
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
                if (cur_decimal > max_decimal)
                    max_decimal = cur_decimal;
                data1.add(Double.valueOf(f_str));
            }
            inputStream.close();
            // int[] data2_arr = new int[data1.size()];
            double[] data2_arr = new double[data1.size()];

            // int max_mul = (int) Math.pow(10, max_decimal);
            for (int i = 0; i < data1.size(); i++) {
                // data2_arr[i] = (int) (data1.get(i) * max_mul);
                data2_arr[i] = data1.get(i);
            }

            System.out.println(max_decimal);

            if (max_decimal > 17) {
                max_decimal = 17;
            }

            byte[] encoded_result = new byte[data2_arr.length * 8];

            long encodeTime = 0;
            long decodeTime = 0;
            double ratio = 0;
            double compressed_size = 0;

            int length = 0;

            long s = System.nanoTime();
            for (int repeat = 0; repeat < repeatTime; repeat++) {
                length = Encoder(data2_arr, block_size, max_decimal, encoded_result);
            }

            long e = System.nanoTime();
            encodeTime += ((e - s) / repeatTime);
            compressed_size += length;

            double ratioTmp;

            ratioTmp = compressed_size / (double) (data1.size() * Long.BYTES);

            ratio += ratioTmp;

            System.out.println("Decode");

            double[] data2_arr_decoded = new double[data1.size()];

            s = System.nanoTime();

            for (int repeat = 0; repeat < repeatTime; repeat++) {
                data2_arr_decoded = Decoder(encoded_result);
            }

            e = System.nanoTime();
            decodeTime += ((e - s) / repeatTime);

            String[] record = {
//                    datasetName,
                    "BUFF",
                    String.valueOf(encodeTime),
                    String.valueOf(decodeTime),
                    String.valueOf(data1.size()),
                    String.valueOf(compressed_size),
                    String.valueOf(ratio)
            };
            writer.writeRecord(record);
            System.out.println(ratio);

            writer.close();
        }

    }

}