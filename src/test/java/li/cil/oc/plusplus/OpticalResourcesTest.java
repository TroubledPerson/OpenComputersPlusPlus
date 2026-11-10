package li.cil.oc.plusplus;

import com.typesafe.config.*;
import org.junit.Test;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;
import static org.junit.Assert.*;

public class OpticalResourcesTest {
    @Test public void opticalRecipesFitAndDoNotConsumeUsedMedia() {
        Config c=ConfigFactory.parseResources("assets/opencomputers/recipes/optical.recipes");assertEquals(4,c.root().size());
        for(String id:new String[]{"ocpp_cdr","ocpp_cdrw","ocpp_cddrive"})assertEquals(1,c.getInt(id+".output"));
        assertEquals(Arrays.asList("oc:materialDisk","minecraft:glass","minecraft:paper"),c.getStringList("ocpp_cdr.input"));
        assertEquals(4,c.getStringList("ocpp_cdrw.input").size());assertFalse(c.getStringList("ocpp_cdrw.input").contains("oc:ocpp_cdr"));
        assertEquals(3,c.getList("ocpp_cddrive.input").size());
        assertEquals(32768,ConfigFactory.parseResources("application.conf").getInt("opencomputers.plusplus.cdCapacityKiB"));
    }
    @Test public void foregroundBadgesAndPlatterModelAreDeterministic()throws Exception {
        File project=new File(System.getProperty("ocpp.sourceRoot","."));
        File originals=new File(System.getProperty("ocpp.spriteSourceItems",new File(project,"src/main/resources/assets/opencomputers/textures/items").getPath()));
        BufferedImage p=ImageIO.read(new File(originals,"disk.png"));BufferedImage r=OpticalSprites.disc(p,false),rw=OpticalSprites.disc(p,true);
        assertEquals(p.getRGB(7,8),r.getRGB(7,8));assertEquals(p.getRGB(8,8),rw.getRGB(8,8));
        assertEquals(0xff333333,r.getRGB(12,10));assertEquals(0xff333333,r.getRGB(14,13));
        assertEquals(0xff333333,rw.getRGB(6,10));assertEquals(0xff333333,rw.getRGB(14,10));
        assertEquals(p.getRGB(15,10),r.getRGB(15,10)); // no label backing or extra circle
        assertNotEquals(r.getRGB(6,10),rw.getRGB(6,10));
    }
    @Test public void noShapelessOpticalRecipeCollidesWithAnyDefaultRecipe(){
        Config all=new StageOneDataTest().recipes();
        for(String optical:new String[]{"ocpp_cdr","ocpp_cdrw"}){
            List<String> key=flat(all.getConfig(optical));Collections.sort(key);
            for(String other:all.root().keySet())if(!other.equals(optical)&&all.getValue(other).valueType()==ConfigValueType.OBJECT){
                Config recipe=all.getConfig(other);if(!recipe.hasPath("input"))continue;
                if(recipe.getValue("input").valueType()!=ConfigValueType.LIST)continue;
                List<String> candidate=flat(recipe);Collections.sort(candidate);assertFalse("collision "+optical+" / "+other,key.equals(candidate));
            }
        }
    }
    private List<String> flat(Config c){List<String> out=new ArrayList<String>();for(Object row:c.getList("input").unwrapped()){
        if(row instanceof List){for(Object cell:(List<?>)row)if(!cell.toString().isEmpty())out.add(cell.toString());}
        else if(!row.toString().isEmpty())out.add(row.toString());
    }return out;}
    @Test public void screenCorrectionChangesOnlyTheThreeApprovedLightPositions()throws Exception {
        File project=new File(System.getProperty("ocpp.sourceRoot",".")).getAbsoluteFile();
        File archive=new File(project.getParentFile(),"deliverables/onejar-dev.9/OpenComputersPlusPlus-onejar-dev.9-source.zip");
        String old;
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive);java.io.InputStream stream=zip.getInputStream(zip.getEntry("src/main/resources/assets/opencomputers/recipes/plusplus.recipes"))){
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] block=new byte[4096];int n;while((n=stream.read(block))>=0)bytes.write(block,0,n);old=new String(bytes.toByteArray(),"UTF-8");
        }
        Config previous=ConfigFactory.parseString(old),current=new StageOneDataTest().recipes();
        for(int tier=5;tier<=6;tier++){
            List<?> a=previous.getList("ocpp_screen"+tier+".input").unwrapped(),b=current.getList("ocpp_screen"+tier+".input").unwrapped();int changed=0;
            for(int y=0;y<3;y++)for(int x=0;x<3;x++){
                Object before=((List<?>)a.get(y)).get(x),after=((List<?>)b.get(y)).get(x);
                if(before.equals("minecraft:glowstone")){assertEquals(tier==5?"minecraft:golden_apple":"minecraft:sea_lantern",after);changed++;}
                else assertEquals(before,after);
            }assertEquals(3,changed);
        }
        assertEquals(previous.getConfig("ocpp_screen4"),current.getConfig("ocpp_screen4"));
    }
}
