import java.util.PriorityQueue;


public class HuffProcessor {

    private class HuffNode implements Comparable<HuffNode> {
        HuffNode left;
        HuffNode right;
        int value;
        int weight;

        public HuffNode(int val, int count) {
            value = val;
            weight = count;
        }

        public HuffNode(int val, int count, HuffNode ltree, HuffNode rtree) {
            value = val;
            weight = count;
            left = ltree;
            right = rtree;
        }

        public int compareTo(HuffNode o) {
            return weight - o.weight;
        }
    }

    public static final int BITS_PER_WORD = 8;
    public static final int BITS_PER_INT = 32;
    public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
    public static final int PSEUDO_EOF = ALPH_SIZE;
    public static final int HUFF_NUMBER = 0xface8200;
    public static final int HUFF_TREE = HUFF_NUMBER | 1;

    private boolean myDebugging = false;

    public HuffProcessor() {
        this(false);
    }

    public HuffProcessor(boolean debug) {
        myDebugging = debug;
    }

    // ===========================
    // COMPRESS
    // ===========================
    public void compress(BitInputStream in, BitOutputStream out) {
        int[] counts = getCounts(in);
        HuffNode root = makeTree(counts);

        String[] encodings = new String[ALPH_SIZE + 1];
        makeEncodings(root, "", encodings);

        // Write header: magic number + tree
        out.writeBits(BITS_PER_INT, HUFF_TREE);
        writeTree(root, out);

        // Encode the data
        in.reset();
        while (true) {
            int val = in.readBits(BITS_PER_WORD);
            if (val == -1)
                break;
            String code = encodings[val];
            for (char c : code.toCharArray()) {
                out.writeBits(1, c == '1' ? 1 : 0);
            }
        }

        // Write EOF code
        String eofCode = encodings[PSEUDO_EOF];
        for (char c : eofCode.toCharArray()) {
            out.writeBits(1, c == '1' ? 1 : 0);
        }

        out.close();
    }

    public void decompress(BitInputStream in, BitOutputStream out) {
        int magic = in.readBits(BITS_PER_INT);
        if (magic != HUFF_TREE) {
            throw new HuffException("Invalid Huffman file");
        }

        HuffNode root = readTree(in);
        HuffNode current = root;

        while (true) {
            int bit = in.readBits(1);
            if (bit == -1) {
                throw new HuffException("Bad input: no PSEUDO_EOF");
            }

            current = (bit == 0) ? current.left : current.right;

            if (current.left == null && current.right == null) {
                if (current.value == PSEUDO_EOF) {
                    break;
                } else {
                    out.writeBits(BITS_PER_WORD, current.value);
                    current = root;
                }
            }
        }

        out.close();
    }

    // ===========================
    // PRIVATE HELPER METHODS
    // ===========================
    private int[] getCounts(BitInputStream in) {
        int[] counts = new int[ALPH_SIZE + 1];
        while (true) {
            int val = in.readBits(BITS_PER_WORD);
            if (val == -1)
                break;
            counts[val]++;
        }
        counts[PSEUDO_EOF] = 1; // ensure EOF present
        in.reset();
        return counts;
    }

    private HuffNode makeTree(int[] counts) {
        PriorityQueue<HuffNode> pq = new PriorityQueue<>();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                pq.add(new HuffNode(i, counts[i], null, null));
            }
        }

        while (pq.size() > 1) {
            HuffNode left = pq.remove();
            HuffNode right = pq.remove();
            HuffNode parent = new HuffNode(-1, left.weight + right.weight, left, right);
            pq.add(parent);
        }

        return pq.remove();
    }

    private void makeEncodings(HuffNode root, String path, String[] encodings) {
        if (root == null)
            return;
        if (root.left == null && root.right == null) {
            encodings[root.value] = path;
            return;
        }
        makeEncodings(root.left, path + "0", encodings);
        makeEncodings(root.right, path + "1", encodings);
    }

    private void writeTree(HuffNode node, BitOutputStream out) {
        if (node.left == null && node.right == null) {
            out.writeBits(1, 1); // leaf marker
            out.writeBits(BITS_PER_WORD + 1, node.value);
        } else {
            out.writeBits(1, 0); // internal node marker
            writeTree(node.left, out);
            writeTree(node.right, out);
        }
    }

    private HuffNode readTree(BitInputStream in) {
        int bit = in.readBits(1);
        if (bit == -1)
            throw new HuffException("Unexpected EOF while reading tree");

        if (bit == 0) { // internal node
            HuffNode left = readTree(in);
            HuffNode right = readTree(in);
            return new HuffNode(-1, 0, left, right);
        } else { // leaf node
            int val = in.readBits(BITS_PER_WORD + 1);
            if (val == -1)
                throw new HuffException("Unexpected EOF when reading leaf");
            return new HuffNode(val, 0, null, null);
        }
    }
}
