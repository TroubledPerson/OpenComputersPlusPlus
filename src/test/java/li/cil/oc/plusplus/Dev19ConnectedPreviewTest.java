package li.cil.oc.plusplus;

import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pixel invariants; final Forge model/GL integration still needs its separate phase. */
public class Dev19ConnectedPreviewTest {
    private File root(){return new File(System.getProperty("ocpp.sourceRoot","."));}
    @Test public void connectedVentHardwareIsNeverSnowTinted()throws Exception{
        File dir=new File(root(),"src/main/resources/assets/opencomputers/textures/blocks/screen");
        File[] files=dir.listFiles((d,n)->n.endsWith(".png"));assertNotNull(files);assertEquals(48,files.length);
        int changed=0;
        for(File file:files){
            String name=file.getName().replace(".png","");
            BufferedImage base=ImageIO.read(file),out=PreviewSprites.connectedScreenSnow(name,base);
            assertEquals(16,out.getWidth());assertEquals(16,out.getHeight());
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                int p=base.getRGB(x,y),q=out.getRGB(x,y);
                assertEquals(name,p>>>24,q>>>24);
                if(name.equals("bmm"))assertEquals("fully connected vent interior",p,q);
                if(name.equals("btm")&&y>=3)assertEquals("vent continues through bottom edge",p,q);
                if(name.equals("bml")&&x>=3)assertEquals("vent continues through right edge",p,q);
                if(name.equals("bhm")&&y>=3&&y<=12)assertEquals("horizontal vent crosses both side joins",p,q);
                if(name.equals("bvm")&&x>=3&&x<=12)assertEquals("vertical vent crosses both vertical joins",p,q);
                if(name.equals("fmm"))assertEquals("interior screen stays black",p,q);
                if(p!=q)changed++;
            }
        }
        assertTrue("outer panels become snow-white",changed>0);
    }
    @Test public void observingDeferredSoundDoesNotConsumeIt(){
        OpticalSoundSchedule s=new OpticalSoundSchedule();
        assertFalse(s.hasPendingRead());assertEquals(OpticalSoundSchedule.CLOSE,s.tray(0,true));
        s.requestRead();assertTrue(s.hasPendingRead());assertTrue(s.hasPendingRead());
        assertEquals(-1,s.tick(1,true));assertTrue(s.hasPendingRead());
        assertEquals(OpticalSoundSchedule.READ,s.tick(40,true));assertFalse(s.hasPendingRead());
        assertEquals(OpticalSoundSchedule.STOP,s.tick(53,true));
    }
}
