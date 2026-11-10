package li.cil.oc.plusplus;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class StageOneSpritesTest {
    private File originals(){return new File(System.getProperty("ocpp.spriteSourceItems"));}
    private File generated(){return new File(System.getProperty("ocpp.spriteGenerated"));}
    private BufferedImage read(File f)throws Exception{BufferedImage i=ImageIO.read(f);assertNotNull(i);return i;}
    private BufferedImage image(String name)throws Exception{return read(new File(generated(),"assets/opencomputers/textures/items/"+name+".png"));}
    private static float[] hsv(int p){return Color.RGBtoHSB((p>>>16)&255,(p>>>8)&255,p&255,null);}
    @Test public void halfRamKeepsNativeThreeChipLayoutContactsPinsAndBoard()throws Exception{
        BufferedImage base=read(new File(originals(),"ram4.png")),ref=read(new File(originals(),"ram6.png"));
        for(int t=4;t<=6;t++){
            BufferedImage out=image("ocpp_ram"+t+"_5");assertEquals(16,out.getWidth());assertEquals(16,out.getHeight());int changed=0;
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                int a=base.getRGB(x,y),b=ref.getRGB(x,y),v=out.getRGB(x,y);assertEquals(a>>>24,v>>>24);
                boolean bevel=(y==6&&x>=5&&x<=8)||(x==5&&(y==7||y==8));
                if(a==b&&!bevel)assertEquals("shared hardware pixel "+x+","+y,a,v);if(a!=v)changed++;
            }
            assertEquals("six markings plus six approved middle-chip bevel pixels change",12,changed);
            assertEquals(base.getRGB(11,2),out.getRGB(11,2));
            for(int y:new int[]{3,7,11})assertTrue((out.getRGB(6,y)&0xFFFFFF)!=(base.getRGB(6,y)&0xFFFFFF));
        }
    }
    @Test public void chipPackagesGrowWithoutTintingNativeSilverPinsOrBlurring()throws Exception{
        int last=0;
        for(int t=4;t<=6;t++){
            BufferedImage out=image("ocpp_chip"+t);int extra=t-4,top=4-extra,left=3-extra,w=11+2*extra,h=8+2*extra;
            int opaque=0,marks=0;float target=new float[]{1f/3f,0.068f,0.006f}[t-4];
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                int p=out.getRGB(x,y);assertTrue((p>>>24)==0||(p>>>24)==255);if((p>>>24)!=0){opaque++;float[] c=hsv(p);if(c[2]>0.5f&&c[1]>0.1f){marks++;float d=Math.abs(c[0]-target);assertTrue(Math.min(d,1-d)<0.025f);}}
            }
            assertTrue(opaque>last);last=opaque;assertTrue(marks>=8);
            for(int x=left;x<left+w;x+=2){assertTrue(hsv(out.getRGB(x,top-1))[1]<0.03f);assertTrue(hsv(out.getRGB(x,top+h))[1]<0.03f);}
            assertEquals(w*h+2*((w+1)/2),opaque);
        }
    }
    @Test public void everyNewModelUsesItsOwnUntintedGeneratedResource()throws Exception{
        File models=new File(originals().getParentFile().getParentFile(),"models/item");
        for(int t=4;t<=6;t++)for(String stem:new String[]{"ocpp_chip"+t,"ocpp_ram"+t+"_5"}){
            String json=new String(Files.readAllBytes(new File(models,stem+".json").toPath()),StandardCharsets.UTF_8);
            assertTrue(json.contains("\"layer0\":\"opencomputers:items/"+stem+"\""));assertFalse(json.contains("layer1"));assertNotNull(image(stem));
        }
    }
}
