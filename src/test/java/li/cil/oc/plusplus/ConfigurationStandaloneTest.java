package li.cil.oc.plusplus;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.util.List;

/** Loads the actual shipped HOCON with the existing Typesafe library. */
public final class ConfigurationStandaloneTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }
    private static int[] values(Config config, String path) {
        List<Integer> values = config.getIntList(path);
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }
    public static void main(String[] args) {
        Config config = ConfigFactory.parseFile(new File(args[0])).getConfig("opencomputers");
        int[] oldRam = values(config, "computer.lua.ramSizes");
        int[] oldDisk = values(config, "filesystem.hddSizes");
        check(oldRam.length == 6 && oldRam[5] == 1024);
        check(oldDisk.length == 3 && oldDisk[2] == 4096);
        int[] ram = TierProfiles.memorySizes(oldRam, values(config, "plusplus.ramSizesKiB"));
        int[] disk = TierProfiles.diskSizes(oldDisk, values(config, "plusplus.hddSizesKiB"));
        long[] ramBytes = {2L << 20, 6L << 20, 16L << 20};
        long[] diskBytes = {16L << 20, 48L << 20, 128L << 20};
        for (int i = 0; i < 3; i++) {
            check(ram[6 + i] * 1024L == ramBytes[i]);
            check(disk[4 + i] * 1024L == diskBytes[i]);
            check(2 * ramBytes[i] <= config.getLong("computer.lua.maxTotalRam"));
            check(config.getDoubleList("plusplus.callBudgets").get(i) == 1.5);
        }
        check(config.getInt("computer.executionDelay") == 12);
        check(config.getIntList("computer.cpuComponentCount").get(3) == 1024);
        check(config.getBoolean("filesystem.bufferChanges"));
        Config custom = ConfigFactory.parseString("opencomputers.computer.lua.ramSizes=[10,20,30,40,50,60]\nopencomputers.filesystem.hddSizes=[10,20,30]")
            .withFallback(ConfigFactory.parseFile(new File(args[0]))).getConfig("opencomputers");
        check(TierProfiles.memorySizes(values(custom, "computer.lua.ramSizes"), values(custom, "plusplus.ramSizesKiB"))[5] == 60);
        check(TierProfiles.diskSizes(values(custom, "filesystem.hddSizes"), values(custom, "plusplus.hddSizesKiB"))[2] == 30);
        System.out.println("PASS shipped configuration: " + checks + " assertions");
    }
}
