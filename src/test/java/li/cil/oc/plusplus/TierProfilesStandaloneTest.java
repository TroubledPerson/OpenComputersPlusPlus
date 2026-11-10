package li.cil.oc.plusplus;

import java.util.Arrays;

/** Dependency-free checks for schema migration, byte limits and stable legacy IDs. */
public final class TierProfilesStandaloneTest {
    private static int assertions;
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("check " + assertions);
    }
    private static void rejects(Runnable operation) {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected);
    }
    public static void main(String[] args) {
        int[] legacyRam = {192, 256, 384, 512, 768, 1024};
        int[] ram = TierProfiles.memorySizes(legacyRam, new int[]{2048, 6144, 16384});
        check(Arrays.equals(Arrays.copyOf(ram, 6), legacyRam));
        check(ram.length == 9);
        for (int i = 0; i < 6; i++) check(TierProfiles.memoryTier(i) == i / 2);
        for (int i = 6; i < 9; i++) {
            check(TierProfiles.memoryTier(i) == i - 2);
            check(2L * ram[i] * 1024 <= 64L * 1024 * 1024);
        }
        int[] customLegacy = {111, 222, 333};
        int[] disk = TierProfiles.diskSizes(customLegacy, new int[]{8192, 24576, 65536});
        check(Arrays.equals(Arrays.copyOf(disk, 3), customLegacy));
        check(disk[3] == 333);
        check(disk[4] * 1024 == 8 * 1024 * 1024);
        check(disk[5] * 1024 == 24 * 1024 * 1024);
        check(disk[6] * 1024 == 64 * 1024 * 1024);
        check(TierProfiles.displayTier(0) == 1);
        check(TierProfiles.displayTier(2) == 3);
        check(TierProfiles.displayTier(4) == 4);
        check(TierProfiles.displayTier(5) == 5);
        check(TierProfiles.displayTier(6) == 6);
        rejects(() -> TierProfiles.memoryTier(-1));
        rejects(() -> TierProfiles.memoryTier(9));
        rejects(() -> TierProfiles.checked(new int[]{1, 2}, 1, 4));
        rejects(() -> TierProfiles.checked(new int[]{0, 1, 2}, 1, 4));
        rejects(() -> TierProfiles.diskSizes(customLegacy, new int[]{1, 2, Integer.MAX_VALUE}));
        rejects(() -> TierProfiles.checkedBudgets(new double[]{1, Double.NaN, 1}));
        rejects(() -> TierProfiles.checkedBudgets(new double[]{1, Double.POSITIVE_INFINITY, 1}));
        rejects(() -> TierProfiles.checkedBudgets(new double[]{1, 0, 1}));
        rejects(() -> TierProfiles.checkedBudgets(new double[]{1, 4.1, 1}));
        check(TierProfiles.checkedBudgets(new double[]{1.5, 1.5, 1.5})[2] == 1.5);
        int[] input = {1, 2, 3};
        int[] output = TierProfiles.checked(input, 1, 3);
        input[0] = 0;
        check(output[0] == 1);
        System.out.println("PASS TierProfiles: " + assertions + " assertions");
    }
}
