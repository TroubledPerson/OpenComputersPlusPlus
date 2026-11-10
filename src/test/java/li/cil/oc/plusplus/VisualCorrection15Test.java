package li.cil.oc.plusplus;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import com.typesafe.config.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Final source/generator contracts; no game or audio device execution. */
public class VisualCorrection15Test {
 private File root(){return new File(System.getProperty("ocpp.sourceRoot","."));}
 private BufferedImage texture(String name)throws Exception{return ImageIO.read(new File(root(),"src/main/resources/assets/opencomputers/textures/"+name+".png"));}
 private String text(String name)throws Exception{return new String(Files.readAllBytes(new File(root(),"src/main/"+name).toPath()),"UTF-8");}
 private int max(int p){return Math.max((p>>>16)&255,Math.max((p>>>8)&255,p&255));}
 @Test public void screenKeepsNativeRecessSlatsAndMapsAllSixFaces()throws Exception{
  for(String n:new String[]{"b","b2"}){
   BufferedImage b=texture("blocks/screen/"+n),a=PreviewSprites.screenSnow(b),snow=PreviewSprites.snow(b);
   for(int y=0;y<16;y++)for(int x=0;x<16;x++)assertEquals(x>=3&&x<=12&&y>=3&&y<=12?b.getRGB(x,y):snow.getRGB(x,y),a.getRGB(x,y));
   for(int y=4;y<=11;y++)for(int x:new int[]{4,6,8,10})assertTrue("coherent native vent columns",max(a.getRGB(x,y))>max(a.getRGB(x+1,y)));
   assertTrue((a.getRGB(1,1)&255)>=240);
  }
  Config c=ConfigFactory.parseString(text("resources/assets/opencomputers/models/block/ocpp_screen7_preview.json"));
  for(String face:new String[]{"east","west","south"})assertEquals("opencomputers:blocks/ocpp_vm_screen_side",c.getString("textures."+face));
  for(String face:new String[]{"up","down"})assertEquals("opencomputers:blocks/ocpp_vm_screen_cap",c.getString("textures."+face));
  assertEquals("opencomputers:blocks/ocpp_vm_screen_front",c.getString("textures.north"));
  assertFalse(c.root().render().contains("ocpp_vm_case"));
 }
 @Test public void genuineAlphaContoursKeepPalePlatterAndPlainForegroundLetters()throws Exception{
  BufferedImage p=texture("items/disk");
  for(boolean rw:new boolean[]{false,true}){
   BufferedImage b=OpticalSprites.disc(p,rw),edge=OpticalSprites.contour(p,rw),composite=OpticalSprites.composite(b,edge);int count=0;
   for(int y=0;y<16;y++)for(int x=0;x<16;x++){
    int v=b.getRGB(x,y),e=edge.getRGB(x,y),a=e>>>24;
    if(v!=p.getRGB(x,y))assertEquals("plain glyph only",0xff333333,v);
    if(a!=0){assertEquals(80,a);assertTrue(OpticalSprites.boundary(p,x,y));assertNotEquals(0xff333333,v);assertEquals(0x484a4c,e&0xffffff);count++;}
    else assertEquals(v,composite.getRGB(x,y));
    assertEquals(v>>>24,composite.getRGB(x,y)>>>24);
   }
   assertTrue(count>20);assertEquals(p.getRGB(6,8),b.getRGB(6,8));
   assertTrue("slightly darker than old20percent edge",(composite.getRGB(6,8)&255)<(OpticalSprites.faintContour(p.getRGB(6,8))&255));
   assertEquals(0xff333333,composite.getRGB(rw?6:12,10));
  }
 }
 @Test public void contactsUseLowSaturationTierHueAndPreserveBrightnessLadder()throws Exception{
  int[] targets={0x66ff66,0xeb8844,0xb3312c};
  for(int t=4;t<=6;t++){
   BufferedImage w=TierSpriteLayers.bake("ram",texture("items/ram3"),texture("items/ram5"),targets[t-4]);
   BufferedImage h=StageOneSprites.halfRam(texture("items/ram4"),texture("items/ram6"),t);
   for(int v:new int[]{w.getRGB(5,6),w.getRGB(7,9),h.getRGB(5,6),h.getRGB(6,6)}){
    float[] hsv=Color.RGBtoHSB((v>>>16)&255,(v>>>8)&255,v&255,null),target=Color.RGBtoHSB((targets[t-4]>>>16)&255,(targets[t-4]>>>8)&255,targets[t-4]&255,null);
    float d=Math.abs(hsv[0]-target[0]);assertTrue(Math.min(d,1-d)<0.02f);assertTrue(hsv[1]>=0.16f&&hsv[1]<=0.21f);assertEquals(255,v>>>24);
   }
   assertEquals(133,max(w.getRGB(5,6)));assertEquals(133,max(h.getRGB(5,6)));assertEquals(92,max(h.getRGB(6,6)));
  }
 }
 @Test public void cdModelsHaveOnlyTheQualifiedUntintedOverlay()throws Exception{
  for(String n:new String[]{"ocpp_cdr","ocpp_cdrw"}){
   Config c=ConfigFactory.parseString(text("resources/assets/opencomputers/models/item/"+n+".json"));
   assertEquals("opencomputers:items/"+n,c.getString("textures.layer0"));assertEquals("opencomputers:items/"+n+"_contour",c.getString("textures.layer1"));assertEquals(2,c.getConfig("textures").root().size());
  }
  String s=text("scala/li/cil/oc/client/ColorHandler.scala");assertTrue(s.contains("Delegator.subItem(stack).exists(_.isInstanceOf[item.OpticalDisc])) 0xFFFFFF"));
  assertTrue(s.contains("TierSpriteLayers.layerTint(tintIndex, Color.rgbValues(Color.byTier(tier)))"));
 }
 @Test public void priorPreviewAudioSpeakersAndOtherHardwareStayExact()throws Exception{
  try(ZipFile z=new ZipFile(new File(root().getParentFile(),"deliverables/onejar-dev.14/OpenComputersPlusPlus-onejar-dev.14-source.zip"))){
   for(String p:new String[]{"scala/li/cil/oc/client/renderer/tileentity/VMCasePreviewRenderer.scala","scala/li/cil/oc/common/tileentity/CDDrive.scala","scala/li/cil/oc/server/component/OpticalFileSystem.scala","java/li/cil/oc/plusplus/SoundSprites.java"}){
    try(InputStream in=z.getInputStream(z.getEntry("src/main/"+p));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))>=0)out.write(b,0,n);byte[] current=Files.readAllBytes(new File(root(),"src/main/"+p).toPath());if(p.equals("scala/li/cil/oc/common/tileentity/CDDrive.scala"))OpticalMotorSourceContract.assertDrive(root(),current);else assertArrayEquals(p,out.toByteArray(),ApprovedDiagnosticSource.normalize(p,current));}
   }
  }
 }
}
