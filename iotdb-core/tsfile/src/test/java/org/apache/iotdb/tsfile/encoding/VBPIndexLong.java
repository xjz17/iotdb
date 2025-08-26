package org.apache.iotdb.tsfile.encoding;

import java.util.BitSet;

public class VBPIndexLong {

    public static final int W = 64; // machine word size

    public final int k;              // bits per code
    public final int n;              // number of rows
    public final int wordsPerPlane;  // how many 64-bit words needed per plane
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
        //     throw new IllegalArgumentException("k must be in [1, 63]");
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

    /* ---------- Public API (compatible with HBPIndex) ---------- */

    /** Return a BitSet with one bit per row; bit=1 means row selected by op C. */
    public BitSet select(HBPIndex.Op op, long C) {
        // mask C into k bits safely using long shifts
        long codeMask = (k == 64) ? ~0L : ((1L << k) - 1L);
        long Ck = (C & codeMask);
        return selectInternal(op, Ck);
    }

    /** Count matches for op C. */
    public long count(HBPIndex.Op op, long C) {
        BitSet bs = select(op, C);
        return bs.cardinality();
    }

    public int size() { return n; }

    /** Reconstruct a code at given row (slow path). */
    public long getCode(int row) {
        if (row < 0 || row >= n) throw new IndexOutOfBoundsException();
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
            long L = 0L;        // less-than
            long G = 0L;        // greater-than

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

    /* ---------- Demo ---------- */
    public static void main(String[] args) {
        int k = 3;
        long[] codes = {1, 5, 6, 1, 6, 4, 0, 7, 4, 3};
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

