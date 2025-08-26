package org.apache.iotdb.tsfile.encoding;

// 简单的 BitWeaving (bit-sliced) 列存储 + 向量化比较（<, =, >）实现
// 支持任意行数（通过 long words 分块，每个 long 存 64 行）
import java.util.BitSet;
import java.util.Random;
import java.util.Arrays;

public class BitWeaving {
    // bitPlanes[bitIndex][wordIndex] : 每个 bit plane 是若干 long words（每个 long 保存 64 行）
    private final long[][] bitPlanes;
    private final int bitWidth;     // number of bit planes
    private final int rowCount;
    private final int wordsPerPlane;

    /** 构造：从 int[] 列值生成 bit-sliced 存储 */
    public BitWeaving(int[] values) {
        if (values == null) throw new IllegalArgumentException("values == null");
        this.rowCount = values.length;
        if (rowCount == 0) {
            this.bitWidth = 1;
            this.wordsPerPlane = 0;
            this.bitPlanes = new long[bitWidth][0];
            return;
        }
        int maxV = 0;
        for (int v : values) {
            if (v < 0) throw new IllegalArgumentException("This simple implementation expects non-negative integers. v=" + v);
            if (v > maxV) maxV = v;
        }
        this.bitWidth = Math.max(1, 32 - Integer.numberOfLeadingZeros(maxV)); // minimal bits needed
        this.wordsPerPlane = (rowCount + 63) / 64;
        this.bitPlanes = new long[bitWidth][wordsPerPlane];

        // encode: for each bit plane and each row set bit if value has that bit
        for (int bit = 0; bit < bitWidth; ++bit) {
            long[] plane = bitPlanes[bit];
            for (int row = 0; row < rowCount; ++row) {
                if (((values[row] >> bit) & 1) != 0) {
                    int wordIdx = row >>> 6;      // /64
                    int bitInWord = row & 63;    // %64
                    plane[wordIdx] |= (1L << bitInWord);
                }
            }
        }
    }

    /** 返回行数 */
    public int rowCount() { return rowCount; }

    /** 小工具：把 long mask 的第 base..base+63 位映射到 BitSet 中（只设置在 rowCount 范围内的位） */
    private static void maskToBitSet(long mask, int baseIndex, int rowCount, BitSet out) {
        int limit = Math.min(64, Math.max(0, rowCount - baseIndex));
        for (int b = 0; b < limit; ++b) {
            if ((mask & (1L << b)) != 0L) out.set(baseIndex + b);
        }
    }

    /**
     * 对列中每一行并行判断 value < K，返回 Java BitSet（位 = true 表示该行满足条件）。
     * 算法：逐 64 行为单元计算 bitwise comparator（从 MSB -> LSB 维护 eq, lt, gt）。
     */
    public BitSet lessThanToConst(int K) {
        BitSet result = new BitSet(rowCount);
        if (rowCount == 0) return result;
        for (int w = 0; w < wordsPerPlane; ++w) {
            long eq = ~0L;
            long lt = 0L;
            long gt = 0L; // unused if only need lt, but compute along the way (optional)
            for (int bit = bitWidth - 1; bit >= 0; --bit) {
                long planeWord = bitPlanes[bit][w];
                if (((K >> bit) & 1) == 1) {
                    // K_i == 1: rows with (eq==1 && planeBit==0) => value bit 0 < 1 => value < K
                    lt |= (eq & ~planeWord);
                    // keep equality only where planeBit == 1
                    eq &= planeWord;
                } else {
                    // K_i == 0: rows with (eq==1 && planeBit==1) => value bit 1 > 0 => value > K
                    gt |= (eq & planeWord);
                    // equality continues only where planeBit == 0
                    eq &= ~planeWord;
                }
            }
            // mask out bits beyond rowCount for last word
            int baseIndex = w << 6;
            int remain = Math.max(0, rowCount - baseIndex);
            long validMask = (remain >= 64) ? ~0L : ((1L << remain) - 1L);
            lt &= validMask;
            // write lt into BitSet
            maskToBitSet(lt, baseIndex, rowCount, result);
        }
        return result;
    }

    /** value == K */
    public BitSet equalToConst(int K) {
        BitSet result = new BitSet(rowCount);
        if (rowCount == 0) return result;
        for (int w = 0; w < wordsPerPlane; ++w) {
            long eq = ~0L;
            for (int bit = bitWidth - 1; bit >= 0; --bit) {
                long planeWord = bitPlanes[bit][w];
                if (((K >> bit) & 1) == 1) {
                    eq &= planeWord;
                } else {
                    eq &= ~planeWord;
                }
            }
            int baseIndex = w << 6;
            int remain = Math.max(0, rowCount - baseIndex);
            long validMask = (remain >= 64) ? ~0L : ((1L << remain) - 1L);
            eq &= validMask;
            maskToBitSet(eq, baseIndex, rowCount, result);
        }
        return result;
    }

    /** value > K */
    public BitSet greaterThanConst(int K) {
        BitSet result = new BitSet(rowCount);
        if (rowCount == 0) return result;
        for (int w = 0; w < wordsPerPlane; ++w) {
            long eq = ~0L;
            long gt = 0L;
            for (int bit = bitWidth - 1; bit >= 0; --bit) {
                long planeWord = bitPlanes[bit][w];
                if (((K >> bit) & 1) == 1) {
                    // K_i == 1
                    // lt |= (eq & ~planeWord);
                    eq &= planeWord;
                } else {
                    // K_i == 0
                    gt |= (eq & planeWord);
                    eq &= ~planeWord;
                }
            }
            int baseIndex = w << 6;
            int remain = Math.max(0, rowCount - baseIndex);
            long validMask = (remain >= 64) ? ~0L : ((1L << remain) - 1L);
            gt &= validMask;
            maskToBitSet(gt, baseIndex, rowCount, result);
        }
        return result;
    }

    /** 仅用于调试：把某一列编码的每个 bit plane 打印为二进制（每行一列） —— 只能在少量行数时使用 */
    public void dumpPlanes() {
        System.out.println("bitWidth=" + bitWidth + ", rowCount=" + rowCount + ", wordsPerPlane=" + wordsPerPlane);
        for (int bit = bitWidth - 1; bit >= 0; --bit) {
            System.out.print("bit " + bit + " : ");
            for (int r = 0; r < rowCount; ++r) {
                int wi = r >>> 6;
                int bi = r & 63;
                long word = bitPlanes[bit][wi];
                System.out.print(((word >>> bi) & 1L));
            }
            System.out.println();
        }
    }

    // -------------------- 测试 / 示例 --------------------
    public static void main(String[] args) {
        final int N = 130; // 测试行数（>64 检查多 word 支持）
        final int MAXV = 31; // 值范围 [0, MAXV]
        int[] values = new int[N];
        Random rnd = new Random(12345);
        for (int i = 0; i < N; ++i) values[i] = rnd.nextInt(MAXV + 1);

        BitWeaving bw = new BitWeaving(values);
        System.out.println("Generated " + N + " random values (max " + MAXV + "). bitWidth used = " + bw.bitWidth);
        //bw.dumpPlanes();

        // 测试若干常数查询，并与标量检查比较
        for (int K = 0; K <= MAXV; ++K) {
            BitSet lt = bw.lessThanToConst(K);
            BitSet eq = bw.equalToConst(K);
            BitSet gt = bw.greaterThanConst(K);

            // 标量检查
            BitSet expLt = new BitSet(N);
            BitSet expEq = new BitSet(N);
            BitSet expGt = new BitSet(N);
            for (int i = 0; i < N; ++i) {
                if (values[i] < K) expLt.set(i);
                else if (values[i] == K) expEq.set(i);
                else expGt.set(i);
            }

            if (!lt.equals(expLt) || !eq.equals(expEq) || !gt.equals(expGt)) {
                System.err.println("Mismatch for K=" + K);
                System.err.println("values: " + Arrays.toString(values));
                System.err.println("lt result: " + lt);
                System.err.println("expected lt: " + expLt);
                System.err.println("eq result: " + eq);
                System.err.println("expected eq: " + expEq);
                System.err.println("gt result: " + gt);
                System.err.println("expected gt: " + expGt);
                System.exit(2);
            }
        }
        System.out.println("All tests passed for K in [0.." + MAXV + "]");
        // 演示：打印前 32 行值与 K 比较结果示例
        int Kdemo = 10;
        System.out.println("--- Demo: first 32 rows, values and (v<" + Kdemo + ", v=" + Kdemo + ", v>" + Kdemo + ") ---");
        BitSet ltd = bw.lessThanToConst(Kdemo);
        BitSet eqd = bw.equalToConst(Kdemo);
        BitSet gtd = bw.greaterThanConst(Kdemo);
        for (int i = 0; i < Math.min(32, N); ++i) {
            System.out.printf("%3d: %2d  <%b  =%b  >%b\n", i, values[i], ltd.get(i), eqd.get(i), gtd.get(i));
        }
    }
}

