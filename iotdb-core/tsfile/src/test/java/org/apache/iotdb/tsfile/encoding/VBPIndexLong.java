package org.apache.iotdb.tsfile.encoding;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class VBPIndexLong {

    public static final int W = 64; // machine word size

    public final int k; // bits per code
    public final int n; // number of rows
    public final int wordsPerPlane; // how many 64-bit words needed per plane
    // planes[t][w] : bitplane for bit t (0=LSB ... k-1=MSB), word index w
    public final long[][] planes;

    /**
     * Build VBP index from k-bit codes.
     *
     * @param kBits number of bits per code (1..63 recommended)
     * @param codes encoded values (each should fit in kBits)
     */
    public VBPIndexLong(int kBits, long[] codes) {
        // if (kBits <= 0 || kBits >= W) {
        // throw new IllegalArgumentException("k must be in [1, 63]");
        // }

        this.k = kBits;
        this.n = codes.length;
        this.wordsPerPlane = (n + W - 1) / W;
        this.planes = new long[k][wordsPerPlane];
        pack(codes);
    }

    /** pack codes into bit-planes */
    private void pack(long[] codes) {
        for (int row = 0; row < n; row++) {
            int wordIdx = row / W;
            int bitPos = row % W;
            long bitMask = 1L << bitPos;
            long code = codes[row];
            for (int t = 0; t < k; t++) {
                if (((code >>> t) & 1) != 0) {
                    planes[t][wordIdx] |= bitMask;
                }
            }
        }
    }

    public BitSet select(HBPIndex.Op op, long C) {
        // mask C into k bits safely using long shifts
        long codeMask = (k == 64) ? ~0L : ((1L << k) - 1L);
        long Ck = (C & codeMask);
        return selectInternal(op, Ck);
    }

    public int[] selectResult(HBPIndex.Op op, long C) {
        long codeMask = (k == 64) ? ~0L : ((1L << k) - 1L);
        long Ck = (C & codeMask);
        return selectInternalResult(op, Ck);
    }

    private int[] selectInternalResult(HBPIndex.Op op, long Ck) {
        // 创建一个动态数组用于存储匹配的行索引
        List<Integer> out = new ArrayList<>();

        // For each 64-bit word index, compute E/L/G across planes
        for (int w = 0; w < wordsPerPlane; w++) {
            // 计算当前单元的有效位掩码
            int bitsInThisWord = Math.min(W, n - w * W);
            long validMask = (bitsInThisWord == 64) ? ~0L : ((1L << bitsInThisWord) - 1L);

            long E = validMask; // equal-so-far
            long L = 0L; // less-than
            long G = 0L; // greater-than

            // 逐位比较从高到低
            for (int t = k - 1; t >= 0; t--) {
                long B = planes[t][w] & validMask; // 当前位平面单元（掩码处理）
                long cb = (Ck >>> t) & 1;
                if (cb == 1) {
                    L |= (E & (~B));
                    E &= B;
                } else {
                    G |= (E & B);
                    E &= (~B);
                }
            }

            long res;
            switch (op) {
                case EQ:
                    res = E;
                    break;
                case NE:
                    res = (~E) & validMask;
                    break;
                case LT:
                    res = L;
                    break;
                case LE:
                    res = (L | E) & validMask;
                    break;
                case GT:
                    res = G;
                    break;
                case GE:
                    res = (G | E) & validMask;
                    break;
                default:
                    res = 0L;
            }

            // 将符合条件的行索引加入结果数组中
            int base = w * W;
            long tmp = res;
            while (tmp != 0L) {
                int t = Long.numberOfTrailingZeros(tmp);
                out.add(base + t);
                tmp &= (tmp - 1);
            }
        }

        // 将结果转换为 int[] 数组并返回
        return out.stream().mapToInt(i -> i).toArray();
    }

    /** Count matches for op C. */
    public long count(HBPIndex.Op op, long C) {
        BitSet bs = select(op, C);
        return bs.cardinality();
    }

    public int countResult(HBPIndex.Op op, long C) {
        int[] res = selectResult(op, C);
        return res.length;
    }

    public int size() {
        return n;
    }

    /** Reconstruct a code at given row (slow path). */
    public long getCode(int row) {
        if (row < 0 || row >= n)
            throw new IndexOutOfBoundsException();
        int wordIdx = row / W;
        int bitPos = row % W;
        long code = 0;
        long mask = 1L << bitPos;
        for (int t = 0; t < k; t++) {
            long planeWord = planes[t][wordIdx];
            if ((planeWord & mask) != 0) {
                code |= (1L << t);
            }
        }
        return code;
    }

    /* ---------- Core vertical scan ---------- */

    private BitSet selectInternal(HBPIndex.Op op, long Ck) {
        BitSet out = new BitSet(n);
        // For each 64-bit word index, compute E/L/G across planes
        for (int w = 0; w < wordsPerPlane; w++) {
            // compute valid mask for this word (last word may be partial)
            int bitsInThisWord = Math.min(W, n - w * W);
            long validMask = (bitsInThisWord == 64) ? ~0L : ((1L << bitsInThisWord) - 1L);

            long E = validMask; // equal-so-far
            long L = 0L; // less-than
            long G = 0L; // greater-than

            // iterate bits from MSB (k-1) down to 0
            for (int t = k - 1; t >= 0; t--) {
                long B = planes[t][w] & validMask; // current bit plane word (masked)
                long cb = (Ck >>> t) & 1;
                if (cb == 1) {
                    // C has 1: if X has 0 -> X < C
                    L |= (E & (~B));
                    // equality continues only where X has 1
                    E &= B;
                } else {
                    // C has 0: if X has 1 -> X > C
                    G |= (E & B);
                    // equality continues only where X has 0
                    E &= (~B);
                }
            }

            long res;
            switch (op) {
                case EQ:
                    res = E;
                    break;
                case NE:
                    res = (~E) & validMask;
                    break;
                case LT:
                    res = L;
                    break;
                case LE:
                    res = (L | E) & validMask;
                    break;
                case GT:
                    res = G;
                    break;
                case GE:
                    res = (G | E) & validMask;
                    break;
                default:
                    res = 0L;
            }

            // write bits from res into BitSet (base index = w * 64)
            int base = w * W;
            long tmp = res;
            while (tmp != 0L) {
                int t = Long.numberOfTrailingZeros(tmp);
                out.set(base + t);
                tmp &= (tmp - 1);
            }
        }
        return out;
    }

    public int findMaxIndex() {
        if (n == 0)
            return -1;

        // 初始化候选位置，开始时所有有效位置都是候选
        long[] candidates = new long[wordsPerPlane];
        for (int w = 0; w < wordsPerPlane; w++) {
            int bitsInThisWord = Math.min(W, n - w * W);
            // 为最后一个word创建有效位掩码
            candidates[w] = (bitsInThisWord == 64) ? ~0L : ((1L << bitsInThisWord) - 1L);
        }

        // 从最高位(MSB)开始处理到最低位(LSB)
        for (int t = k - 1; t >= 0; t--) {
            long[] nextCandidates = new long[wordsPerPlane];
            boolean hasOnes = false;

            // 检查当前候选位置中是否有在第t位为1的
            for (int w = 0; w < wordsPerPlane; w++) {
                long onesInThisBit = planes[t][w] & candidates[w];
                if (onesInThisBit != 0) {
                    nextCandidates[w] = onesInThisBit;
                    hasOnes = true;
                }
            }

            // 如果找到了第t位为1的位置，只保留这些位置
            // 否则，保留第t位为0的位置
            if (hasOnes) {
                candidates = nextCandidates;
            } else {
                // 保留第t位为0的位置
                for (int w = 0; w < wordsPerPlane; w++) {
                    candidates[w] = candidates[w] & (~planes[t][w]);
                }
            }
        }

        // 在剩余的候选位置中找到第一个设置的位
        for (int w = 0; w < wordsPerPlane; w++) {
            if (candidates[w] != 0) {
                int bitPos = Long.numberOfTrailingZeros(candidates[w]);
                return w * W + bitPos;
            }
        }

        return -1;
    }

    public long sum() {
        if (n == 0)
            return 0L;

        long totalSum = 0L;

        // 对每个bit位置t，计算其对总和的贡献
        for (int t = 0; t < k; t++) {
            long bitContribution = 0L;

            // 统计第t个bit-plane中所有为1的位的个数
            for (int w = 0; w < wordsPerPlane; w++) {
                // 获取当前word中的有效位掩码
                int bitsInThisWord = Math.min(W, n - w * W);
                long validMask = (bitsInThisWord == 64) ? ~0L : ((1L << bitsInThisWord) - 1L);

                // 获取第t个bit-plane在当前word中的值，并应用有效位掩码
                long planeWord = planes[t][w] & validMask;

                // 统计这个word中1的个数
                bitContribution += Long.bitCount(planeWord);
            }

            // 第t位的权重是2^t，将贡献加到总和中
            totalSum += bitContribution << t;
        }

        return totalSum;
    }

    /* ---------- Demo ---------- */
    public static void main(String[] args) {
        int k = 3;
        long[] codes = { 1, 5, 6, 1, 6, 4, 0, 7, 4, 3 };
        VBPIndexLong idx = new VBPIndexLong(k, codes);

        for (int i = 0; i < idx.wordsPerPlane; i++) {
            System.out.println("word " + i + ":");
            for (int t = 0; t < k; t++) {
                System.out.println(String.format("%64s", Long.toBinaryString(idx.planes[t][i])).replace(' ', '0'));
            }
            System.out.println();
        }

        System.out.println("n = " + idx.size());

        BitSet lt4 = idx.select(HBPIndex.Op.LT, 4);
        System.out.println("< 4 -> " + lt4);

        BitSet eq4 = idx.select(HBPIndex.Op.EQ, 4);
        System.out.println("= 4 -> " + eq4);

        BitSet ge6 = idx.select(HBPIndex.Op.GE, 6);
        System.out.println(">= 6 -> " + ge6);

        for (int i = 0; i < idx.size(); i++) {
            System.out.print(idx.getCode(i) + (i + 1 == idx.size() ? "\n" : " "));
        }

        System.out.println("count(<5) = " + idx.count(HBPIndex.Op.LT, 5));
    }
}
