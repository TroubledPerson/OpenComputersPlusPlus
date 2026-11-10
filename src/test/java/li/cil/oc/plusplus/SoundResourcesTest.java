package li.cil.oc.plusplus;

import com.typesafe.config.*;
import java.io.File;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class SoundResourcesTest {
    private File project(){return new File(System.getProperty("ocpp.sourceRoot","."));}
    @Test public void suppliesThreeCraftableCardsWithOneOutputAndOriginalChipProgression(){
        Config c=ConfigFactory.parseFile(new File(project(),"src/main/resources/assets/opencomputers/recipes/sound.recipes"));assertEquals(3,c.root().size());
        for(int tier=1;tier<=3;tier++){
            Config r=c.getConfig("ocpp_sound"+tier);assertEquals(1,r.getInt("output"));List<String> cells=new ArrayList<String>();
            for(Object row:r.getList("input").unwrapped()){assertTrue(((List<?>)row).size()<=3);for(Object cell:(List<?>)row)if(!cell.toString().isEmpty())cells.add(cell.toString());}
            assertTrue(cells.size()<=9);assertEquals(1,Collections.frequency(cells,"oc:materialCard"));
            assertEquals(tier==1?1:2,Collections.frequency(cells,"oc:circuitChip"+tier));
            assertEquals(tier==1?0:1,Collections.frequency(cells,"minecraft:noteblock"));
        }
        Config defaults=ConfigFactory.parseFile(new File(project(),"src/main/resources/application.conf")).getConfig("opencomputers");
        assertTrue(defaults.getBoolean("plusplus.soundCardsEnabled"));assertEquals(1024,defaults.getInt("plusplus.soundMaxEncodedKiB"));assertEquals(30,defaults.getInt("plusplus.soundMaxDurationSeconds"));
        assertEquals(Arrays.asList(40,80,160),defaults.getIntList("plusplus.casePowerRates")); // source follow-up; immutable dev10 still ships20/20/20
    }
    @Test public void preservesReferenceBoardBracketContactsAndRemovesAllTierNumerals()throws Exception {
        File originals=new File(project(),"src/main/resources/assets/opencomputers/textures/items");BufferedImage base=ImageIO.read(new File(originals,"internetcard.png"));
        for(int tier=1;tier<=3;tier++){BufferedImage image=SoundSprites.card(base,tier);assertEquals(16,image.getWidth());
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                boolean mask=x>=9&&x<=15&&y<=6;
                if(!mask)assertEquals("native detail "+tier+":"+x+","+y,base.getRGB(x,y),image.getRGB(x,y));
            }
            assertEquals(new int[]{0xffc9c9c9,0xffdfd6a3,0xffb5e9e4}[tier-1],image.getRGB(12,1));
            assertEquals(0xff17191b,image.getRGB(12,0));
            assertEquals(base.getRGB(2,5),image.getRGB(2,5)); // metal bracket, not a numeral
            assertNotEquals(image.getRGB(12,0),image.getRGB(12,1));
        }
        String preview=System.getProperty("ocpp.audioPreviewRoot");if(preview!=null){File p=new File(preview);p.mkdirs();SoundSprites.generate(originals,new File(p,"generated"),p);}
    }
}
