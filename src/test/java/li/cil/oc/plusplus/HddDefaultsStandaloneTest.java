package li.cil.oc.plusplus;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

/** Focused check of the actual final JAR and explicit configuration precedence.
 * No Minecraft initialization, world/disk data or host mounts are accessed. */
public final class HddDefaultsStandaloneTest {
    private static int checks;
    private static void check(boolean value) { checks++; if(!value)throw new AssertionError("HDD check "+checks); }
    private static int[] ints(Config config,String path) {
        List<Integer> list=config.getIntList(path);int[] result=new int[list.size()];
        for(int i=0;i<result.length;i++)result[i]=list.get(i);return result;
    }
    public static void main(String[] args) throws Exception {
        Config root;
        try(ZipFile jar=new ZipFile(args[0]); InputStreamReader in=new InputStreamReader(jar.getInputStream(jar.getEntry("application.conf")),StandardCharsets.UTF_8)) {
            root=ConfigFactory.parseReader(in);
        }
        Config actual=root.getConfig("opencomputers");
        Config source=ConfigFactory.parseFile(new File(args[1])).getConfig("opencomputers");
        int[] extended={16384,49152,131072};
        check(Arrays.equals(ints(actual,"plusplus.hddSizesKiB"),extended));
        check(Arrays.equals(ints(source,"plusplus.hddSizesKiB"),extended));
        check(Arrays.equals(ints(actual,"filesystem.hddSizes"),new int[]{1024,2048,4096}));
        check(Arrays.equals(ints(actual,"plusplus.ramSizesKiB"),new int[]{2048,6144,16384}));
        check(Arrays.equals(ints(actual,"computer.lua.ramSizes"),new int[]{192,256,384,512,768,1024}));
        int[] tiers=TierProfiles.diskSizes(ints(actual,"filesystem.hddSizes"),ints(actual,"plusplus.hddSizesKiB"));
        check(Arrays.equals(Arrays.copyOf(tiers,4),new int[]{1024,2048,4096,4096}));
        long[] bytes={16777216L,50331648L,134217728L};
        for(int i=0;i<3;i++) {
            check(tiers[4+i]*1024L==bytes[i]);
            check(tiers[4+i]*1024==bytes[i]); // Actual driver multiplication remains within Int range.
            check(2L*tiers[4+i]*1024L==2L*bytes[i]); // Independent disks, not one enlarged filesystem.
        }
        Config old=ConfigFactory.parseString("opencomputers.plusplus.hddSizesKiB=[8192,24576,65536]").withFallback(root).getConfig("opencomputers");
        check(Arrays.equals(ints(old,"plusplus.hddSizesKiB"),new int[]{8192,24576,65536}));
        Config custom=ConfigFactory.parseString("opencomputers.plusplus.hddSizesKiB=[10000,32000,96000]").withFallback(root).getConfig("opencomputers");
        check(Arrays.equals(ints(custom,"plusplus.hddSizesKiB"),new int[]{10000,32000,96000}));
        Config missing=ConfigFactory.parseString("opencomputers.plusplus.ramSizesKiB=[2048,6144,16384]").withFallback(root).getConfig("opencomputers");
        check(Arrays.equals(ints(missing,"plusplus.hddSizesKiB"),extended));
        check(TierProfiles.diskSizes(new int[]{10,20,30},extended)[2]==30);
        try { TierProfiles.diskSizes(new int[]{1024,2048,4096},new int[]{16384,49152}); throw new AssertionError("accepted bad list"); }
        catch(IllegalArgumentException expected){checks++;}
        System.out.println("PASS actual final-JAR HDD defaults, units, limits and config preservation: "+checks+" assertions");
    }
}
