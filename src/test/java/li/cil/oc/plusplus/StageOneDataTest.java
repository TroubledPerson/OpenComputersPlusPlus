package li.cil.oc.plusplus;

import com.typesafe.config.*;
import java.io.File;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Loads real source HOCON and the preserved pre-stage recipe fixture. */
public class StageOneDataTest {
    private File source(String path){return new File(System.getProperty("ocpp.sourceRoot","."),path);}
    private Config defaults(){return ConfigFactory.parseFile(source("src/main/resources/application.conf")).getConfig("opencomputers");}
    Config recipes(){File file=source("src/main/resources/assets/opencomputers/recipes/default.recipes");return ConfigFactory.parseFile(file,ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF).setIncluder(new FileIncluder(file.getParentFile())));}
    private static final class FileIncluder implements ConfigIncluder,ConfigIncluderFile {
        private final File directory;private ConfigIncluder fallback;
        FileIncluder(File directory){this.directory=directory;}
        public ConfigIncluder withFallback(ConfigIncluder value){fallback=value;return this;}
        public ConfigObject include(ConfigIncludeContext context,String name){return fallback.include(context,name);}
        public ConfigObject includeFile(ConfigIncludeContext context,File file){return ConfigFactory.parseFile(file.isAbsolute()?file:new File(directory,file.getPath()),ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF).setIncluder(this)).root();}
    }
    private static int[] ints(Config c,String key){List<Integer> values=c.getIntList(key);int[] a=new int[values.size()];for(int i=0;i<a.length;i++)a[i]=values.get(i);return a;}
    private static List<String> cells(Config c){List<String> out=new ArrayList<>();for(Object row:c.getList("input").unwrapped()){if(row instanceof List){for(Object value:(List<?>)row)if(!value.toString().isEmpty())out.add(value.toString());}else if(!row.toString().isEmpty())out.add(row.toString());}return out;}
    private static int count(List<String> cells,String s){return Collections.frequency(cells,s);}
    @Test public void keepsOldMemoryIndicesAndAppendsExactApprovedHalfSizes(){
        Config c=defaults();int[] old=ints(c,"computer.lua.ramSizes"),whole=ints(c,"plusplus.ramSizesKiB"),half=ints(c,"plusplus.halfRamSizesKiB");
        assertArrayEquals(new int[]{2731,8192,21846},half);
        int[] all=TierProfiles.memorySizes(old,whole,half);assertEquals(12,all.length);
        assertArrayEquals(TierProfiles.memorySizes(old,whole),Arrays.copyOf(all,9));
        for(int i=0;i<3;i++){assertEquals(4+i,TierProfiles.memoryTier(9+i));assertEquals((long)half[i]*1024,(long)all[9+i]*1024);}
        assertTrue(2L*all[11]*1024<64L*1024*1024);
        try{TierProfiles.memoryTier(12);fail();}catch(IllegalArgumentException expected){}
        try{TierProfiles.memorySizes(old,whole,new int[]{1,2});fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void retainsCustomWholeAndLegacySettingsWhenHalfDefaultsAreMissing(){
        Config c=ConfigFactory.parseString("computer.lua.ramSizes=[1,2,3,4,5,6]\nplusplus.ramSizesKiB=[7,8,9]").withFallback(defaults());
        assertArrayEquals(new int[]{1,2,3,4,5,6,7,8,9,2731,8192,21846},TierProfiles.memorySizes(ints(c,"computer.lua.ramSizes"),ints(c,"plusplus.ramSizesKiB"),ints(c,"plusplus.halfRamSizesKiB")));
        Config custom=ConfigFactory.parseString("plusplus.halfRamSizesKiB=[10,20,30]").withFallback(c);
        assertArrayEquals(new int[]{10,20,30},ints(custom,"plusplus.halfRamSizesKiB"));
    }
    @Test public void resetsOnlyExtendedTierLinesAndLeavesLegacyFormattingUntouched(){
        for(int tier=4;tier<=6;tier++){String s=TierText.line(tier,"Tier "+tier);assertTrue(s.startsWith(new String[]{"\u00a7a","\u00a76","\u00a7c"}[tier-4]));assertTrue(s.endsWith("\u00a7r"));assertEquals(2,s.chars().filter(v->v==0xA7).count());}
        for(int tier=-1;tier<=3;tier++)assertEquals("neutral specs",TierText.line(tier,"neutral specs"));
        assertEquals("future",TierText.line(7,"future"));
    }
    @Test public void suppliesExactly27NewRecipesWithOneOutputAndGridFit(){
        Config r=recipes();int total=0;
        for(int tier=4;tier<=6;tier++)for(String family:new String[]{"chip","cpu","ram","hdd","gpu","apu","case","screen","ram_half"}){
            String key="ocpp_"+(family.equals("ram_half")?"ram"+tier+"_5":family+tier);Config c=r.getConfig(key);total++;
            assertEquals(key,1,c.getInt("output"));List<?> rows=c.getList("input").unwrapped();assertTrue(rows.size()<=3);
            int slots=0;for(Object row:rows){assertTrue(row instanceof List);assertTrue(((List<?>)row).size()<=3);for(Object x:(List<?>)row){assertTrue(x instanceof String);if(!x.toString().isEmpty())slots++;}}
            assertTrue(slots>0&&slots<=9);
            if(family.equals("gpu")||family.equals("apu"))assertEquals("ocpp_pristine_shaped",c.getString("type"));
        }assertEquals(27,total);
    }
    @Test public void obeysMaterialCountsChipYieldsAndHalfRamProgression(){
        Config r=recipes();String[] m={"minecraft:obsidian","minecraft:dragon_breath","minecraft:nether_star"};
        for(int t=4;t<=6;t++){
            List<String> chip=cells(r.getConfig("ocpp_chip"+t));assertEquals(6,count(chip,m[t-4]));assertEquals(2,count(chip,"redstone"));assertEquals(1,count(chip,"oc:materialTransistor"));
            for(String f:new String[]{"cpu","apu","case","screen"})assertEquals(4,count(cells(r.getConfig("ocpp_"+f+t)),m[t-4]));
            assertEquals(1,count(cells(r.getConfig("ocpp_ram"+t)),m[t-4]));assertEquals(2,count(cells(r.getConfig("ocpp_hdd"+t)),m[t-4]));
            String light=t==4?"minecraft:glowstone":t==5?"minecraft:golden_apple":"minecraft:sea_lantern";
            assertEquals(3,count(cells(r.getConfig("ocpp_screen"+t)),light));
            if(t>4)assertEquals(0,count(cells(r.getConfig("ocpp_screen"+t)),"minecraft:glowstone"));
        }
        assertEquals(1,count(cells(r.getConfig("ocpp_ram4_5")),"oc:ocpp_chip5"));assertEquals(1,count(cells(r.getConfig("ocpp_ram5_5")),"oc:ocpp_chip6"));
        assertEquals(3,count(cells(r.getConfig("ocpp_ram6_5")),"oc:ocpp_chip6"));assertEquals(2,count(cells(r.getConfig("ocpp_ram6_5")),"oc:ocpp_chip5"));
        assertEquals(4,count(cells(r.getConfig("screen3")),"minecraft:diamond"));
    }
    @Test public void preservesEveryOldRecipeExceptAuthorizedScreen3Change(){
        Config old=ConfigFactory.parseResources("ocpp/dev8-default.recipes"),now=recipes();assertFalse(old.root().isEmpty());
        for(String key:old.root().keySet())if(!key.equals("screen3"))assertEquals("old recipe "+key,old.getValue(key),now.getValue(key));
        assertEquals(8,now.getInt("chip1.output"));assertEquals(4,now.getInt("chip2.output"));assertEquals(2,now.getInt("chip3.output"));
        assertEquals(4,count(cells(old.getConfig("screen3")),"obsidian"));
    }
    private static String shape(Config recipe){
        if(!recipe.hasPath("input"))return null;Object o=recipe.getValue("input").unwrapped();if(!(o instanceof List))return null;
        List<?> rows=(List<?>)o;if(rows.isEmpty()||rows.size()>3||!(rows.get(0)instanceof List))return null;
        List<List<String>> grid=new ArrayList<>();for(Object row:rows){if(!(row instanceof List)||((List<?>)row).size()>3)return null;List<String> line=new ArrayList<>();for(Object v:(List<?>)row)line.add(String.valueOf(v));while(line.size()<3)line.add("");grid.add(line);}
        while(grid.size()<3)grid.add(new ArrayList<>(Arrays.asList("","","")));
        int l=3,r=-1,t=3,b=-1;for(int y=0;y<3;y++)for(int x=0;x<3;x++)if(!grid.get(y).get(x).isEmpty()){l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);}
        if(r<l)return null;StringBuilder a=new StringBuilder(),z=new StringBuilder();
        for(int y=t;y<=b;y++){for(int x=l;x<=r;x++){a.append(grid.get(y).get(x)).append('|');z.append(grid.get(y).get(r-(x-l))).append('|');}a.append('/');z.append('/');}
        return a.toString().compareTo(z.toString())<0?a.toString():z.toString();
    }
    @Test public void hasNoNewGridCollisionsMissingNewIngredientsOrCycles(){
        Config all=recipes();Map<String,String> seen=new HashMap<>();Set<String> roots=new HashSet<>();for(String k:all.root().keySet())if(k.startsWith("ocpp_"))roots.add(k);
        for(String key:all.root().keySet())if(all.getValue(key).valueType()==ConfigValueType.OBJECT){String sig=shape(all.getConfig(key));if(sig!=null){String previous=seen.put(sig,key);if(previous!=null)assertFalse("recipe collision "+previous+" / "+key,previous.startsWith("ocpp_")||key.startsWith("ocpp_"));}}
        assertEquals(35,roots.size());for(String root:roots)visit(root,all,new HashSet<String>(),new HashSet<String>());
    }
    private static void visit(String key,Config all,Set<String> active,Set<String> done){
        if(done.contains(key))return;assertTrue("cyclic ingredient graph "+key,active.add(key));
        for(String input:cells(all.getConfig(key)))if(input.startsWith("oc:ocpp_")){String target=input.substring(3);assertTrue("unregistered recipe ingredient "+target,all.hasPath(target));visit(target,all,active,done);}
        active.remove(key);done.add(key);
    }
}
