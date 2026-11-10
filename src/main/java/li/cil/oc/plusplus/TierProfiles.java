package li.cil.oc.plusplus;

/** Stable indexes: upstream index 3 remains creative. New hardware uses 4..6. */
public final class TierProfiles {
    public static final int FIRST = 4;
    public static final int LAST = 6;

    private TierProfiles() {}

    public static int displayTier(int tier) {
        return tier >= FIRST && tier <= LAST ? tier : tier + 1;
    }

    public static int memoryTier(int level) {
        if (level < 0 || level > 11) throw new IllegalArgumentException("invalid RAM level");
        return level < 6 ? level / 2 : level < 9 ? level - 2 : level - 5;
    }

    public static int[] checked(int[] values, int min, int max) {
        if (values.length != 3) throw new IllegalArgumentException("OC++ settings require three entries (T4/T5/T6)");
        int[] result = values.clone();
        for (int value : result) {
            if (value < min || value > max) throw new IllegalArgumentException("OC++ setting out of bounds");
        }
        return result;
    }

    public static double[] checkedBudgets(double[] values) {
        if (values.length != 3) throw new IllegalArgumentException("OC++ call budgets require three entries");
        double[] result = values.clone();
        for (double value : result) {
            if (!Double.isFinite(value) || value <= 0 || value > 4.0)
                throw new IllegalArgumentException("OC++ call budgets must be finite and in (0,4]");
        }
        return result;
    }

    /** Keep legacy RAM levels 0..5 byte-for-byte; append full T4/T5/T6 sticks. */
    public static int[] memorySizes(int[] legacy, int[] extended) {
        if (legacy.length != 6) throw new IllegalArgumentException("six legacy RAM levels required");
        return append(legacy, checked(extended, 1, 65536));
    }

    /** Append half sticks without shifting legacy or existing whole-stick levels. */
    public static int[] memorySizes(int[] legacy, int[] extended, int[] half) {
        return append(memorySizes(legacy, extended), checked(half, 1, 65536));
    }

    /** Keep the creative index 3 reserved; physical disks 4..6 use new values. */
    public static int[] diskSizes(int[] legacy, int[] extended) {
        if (legacy.length != 3) throw new IllegalArgumentException("three legacy disks required");
        return append(append(legacy, new int[]{legacy[2]}), checked(extended, 1, 1048576));
    }

    private static int[] append(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
