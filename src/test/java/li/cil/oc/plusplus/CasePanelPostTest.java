package li.cil.oc.plusplus;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real native pixels and production transforms; no game, GL or sound device. */
public class CasePanelPostTest {
    private File root(){return new File(System.getProperty("ocpp.sourceRoot","."));}
    private BufferedImage nativeFace(String face)throws Exception{return ImageIO.read(new File(root(),"src/main/resources/assets/opencomputers/textures/blocks/case_"+face+".png"));}
    @Test public void sideFanAndRecessKeepEveryNativePixelAndOpaqueGeometry()throws Exception {
        BufferedImage before=nativeFace("side"),after=PreviewSprites.caseSideSnow(before);int brightPanels=0;
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            assertEquals(before.getRGB(x,y)>>>24,after.getRGB(x,y)>>>24);
            if(x>=2&&x<=13&&y>=2&&y<=13)assertEquals("fan/recess "+x+","+y,before.getRGB(x,y),after.getRGB(x,y));
            else {assertTrue((after.getRGB(x,y)&255)>=216);if((after.getRGB(x,y)&255)>=240)brightPanels++;}
        }
        assertTrue("snow-white highlights retained",brightPanels>0);
    }
    @Test public void frontShadowsAreContinuousButStripeAndLowerPortRemainHardware()throws Exception {
        BufferedImage before=nativeFace("front"),after=PreviewSprites.caseFrontSnow(before);
        for(int y=5;y<=14;y++)for(int x=6;x<=7;x++)assertEquals(before.getRGB(x,y),after.getRGB(x,y));
        for(int x=12;x<=14;x++)assertEquals(before.getRGB(x,14),after.getRGB(x,14));
        // These are native panel/vent bevel shadows, not connector apertures.
        for(int[] point:new int[][]{{2,13},{2,14},{13,12}})assertTrue((after.getRGB(point[0],point[1])&255)>=216);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int p=before.getRGB(x,y),q=after.getRGB(x,y);assertEquals(p>>>24,q>>>24);
            int max=Math.max((p>>>16)&255,Math.max((p>>>8)&255,p&255)),min=Math.min((p>>>16)&255,Math.min((p>>>8)&255,p&255));
            if(max-min>3)assertEquals("native indicator",p,q);
        }
    }
    @Test public void panelRampHasNoBrightnessCliffAt31Or32() {
        BufferedImage probe=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);int last=-1;
        for(int value=0;value<=255;value++){
            probe.setRGB(0,0,0xff000000|value<<16|value<<8|value);
            int output=PreviewSprites.caseFrontSnow(probe).getRGB(0,0)&255;
            assertTrue(output>=216&&output<=255);if(last>=0){assertTrue(output>=last);assertTrue("at most one shade step",output-last<=1);}last=output;
        }
        probe.setRGB(0,0,0xff1f1f1f);assertEquals(228,PreviewSprites.caseFrontSnow(probe).getRGB(0,0)&255);
        probe.setRGB(0,0,0xff202020);assertEquals(228,PreviewSprites.caseFrontSnow(probe).getRGB(0,0)&255);
    }
    @Test public void transformsPreserveTransparentAndColouredPixelsAndRejectWrongGeometry() {
        BufferedImage probe=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);probe.setRGB(0,0,0x001f1f1f);probe.setRGB(1,0,0xff618760);
        for(BufferedImage after:new BufferedImage[]{PreviewSprites.caseFrontSnow(probe),PreviewSprites.caseSideSnow(probe)}){assertEquals(0x001f1f1f,after.getRGB(0,0));assertEquals(0xff618760,after.getRGB(1,0));}
        try{PreviewSprites.caseSideSnow(new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB));fail("native geometry required");}catch(IllegalArgumentException expected){}
    }
    @Test public void postUsesExistingShortPatternAndStartupStatusKeepsGuestUnavailable()throws Exception {
        String tile=new String(Files.readAllBytes(new File(root(),"src/main/scala/li/cil/oc/common/tileentity/VMCasePreview.scala").toPath()),"UTF-8");
        assertTrue(tile.contains("if(transition.state==BiosBootState.State.BIOS)emitPostSound()"));
        assertTrue(tile.contains("pos.getZ+0.5,\".\")"));assertTrue(tile.contains("pos.getZ+0.5,\"--\")"));
        assertEquals("one transition call, one definition",2,tile.split("emitPostSound",-1).length-1);
        String view=new String(Files.readAllBytes(new File(root(),"src/main/scala/li/cil/oc/client/gui/BiosScreenView.scala").toPath()),"UTF-8");
        assertTrue(view.indexOf("if(!on)return")<view.indexOf("ocpp.superbios.postPassed"));
        for(String key:new String[]{"ocpp.superbios.biosOnly","ocpp.superbios.statusOffline","ocpp.superbios.noRuntime","ocpp.superbios.noGuest"})assertTrue(view.contains(key));
        assertFalse(SuperBiosGuestExecutionProbe.allowed());
    }
    private static class SuperBiosGuestExecutionProbe {static boolean allowed(){return li.cil.oc.api.vm.SuperBios.mayExecuteGuest();}}
}
