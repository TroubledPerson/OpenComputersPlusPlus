package li.cil.oc.plusplus;
import java.io.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

/** Tier-specific grey-based contact hues supersede the uniform warm dev14 choice. */
public class RamHardwareSourceTest {
 private File project(){return new File(System.getProperty("ocpp.sourceRoot","."));}
 private File originals(){return new File(project(),"src/main/resources/assets/opencomputers/textures/items");}
 private BufferedImage original(String n)throws Exception{return ImageIO.read(new File(originals(),n+".png"));}
 private ZipFile old()throws Exception{return new ZipFile(new File(project().getParentFile(),"deliverables/onejar-dev.14/OpenComputersPlusPlus-onejar-dev.14.jar"));}
 @Test public void changesOnlySixWholeContactsPreservingGapsAndEveryOtherPixel()throws Exception {
  BufferedImage base=original("ram3"),ref=original("ram5");int[] colours={0x66ff66,0xeb8844,0xb3312c};
  try(ZipFile z=old()){for(int t=4;t<=6;t++){
   BufferedImage before=ImageIO.read(z.getInputStream(z.getEntry("assets/opencomputers/textures/items/ocpp_ram"+t+".png"))),after=TierSpriteLayers.bake("ram",base,ref,colours[t-4]);int changed=0;
   for(int y=0;y<16;y++)for(int x=0;x<16;x++){
    assertEquals(before.getRGB(x,y)>>>24,after.getRGB(x,y)>>>24);
    if(TierSpriteLayers.ramContactPixel(x,y))assertEquals(TierSpriteLayers.ramContactTint(base.getRGB(x,y),colours[t-4]),after.getRGB(x,y));
    else assertEquals("preserved marking/PCB/contact "+t+":"+x+","+y,before.getRGB(x,y),after.getRGB(x,y));
    if(before.getRGB(x,y)!=after.getRGB(x,y))changed++;
   }assertEquals(6,changed);assertNotEquals(before.getRGB(5,6),after.getRGB(5,6));assertEquals(after.getRGB(5,6),after.getRGB(7,9));
   assertEquals(0xff182c18,after.getRGB(6,6));assertEquals(0xff224422,after.getRGB(6,9));assertEquals(before.getRGB(11,2),after.getRGB(11,2));
  }}
 }
 @Test public void halfSticksChangeOnlySixMiddleBevelPixelsOtherFamiliesRemainIdentical()throws Exception {
  int[] colours={0x66ff66,0xeb8844,0xb3312c};
  try(ZipFile z=old()){for(int t=4;t<=6;t++){
   BufferedImage half=StageOneSprites.halfRam(original("ram4"),original("ram6"),t),before=ImageIO.read(z.getInputStream(z.getEntry("assets/opencomputers/textures/items/ocpp_ram"+t+"_5.png")));
      int changed=0;
   for(int y=0;y<16;y++)for(int x=0;x<16;x++){
    boolean bevel=(y==6&&x>=5&&x<=8)||(x==5&&(y==7||y==8));
    if(bevel){assertEquals(TierSpriteLayers.ramContactTint(x==5&&y==6?0xff858472:0xff5c5b4f,colours[t-4]),half.getRGB(x,y));changed++;}
    else assertEquals("half marking/geometry/contact "+t+":"+x+","+y,before.getRGB(x,y),half.getRGB(x,y));
    assertEquals(before.getRGB(x,y)>>>24,half.getRGB(x,y)>>>24);
   }assertEquals(6,changed);
   for(String f:new String[]{"cpu","apu","hdd","graphicscard"}){
    BufferedImage a=TierSpriteLayers.bake(f,original(TierSpriteLayers.baseName(f)),original(TierSpriteLayers.referenceName(f)),colours[t-4]);
    BufferedImage b=ImageIO.read(z.getInputStream(z.getEntry("assets/opencomputers/textures/items/ocpp_"+f+t+".png")));
    assertArrayEquals(f+t,b.getRGB(0,0,16,b.getHeight(),null,0,16),a.getRGB(0,0,16,a.getHeight(),null,0,16));
   }
  }}
 }
 public static void main(String[] args)throws Exception {
  File p=new File(args[0]),out=new File(args[1]);out.mkdirs();BufferedImage ref=ImageIO.read(new File(p,"src/main/resources/assets/opencomputers/textures/items/ram5.png"));
  BufferedImage sheet=new BufferedImage(768,316,BufferedImage.TYPE_INT_ARGB);Graphics2D g=sheet.createGraphics();g.setColor(new Color(0x777777));g.fillRect(0,0,768,316);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
  try(ZipFile z=new ZipFile(new File(p.getParentFile(),"deliverables/onejar-dev.14/OpenComputersPlusPlus-onejar-dev.14.jar"))){for(int t=4;t<=6;t++){
   BufferedImage before=ImageIO.read(z.getInputStream(z.getEntry("assets/opencomputers/textures/items/ocpp_ram"+t+".png"))),after=ImageIO.read(new File(out,"generated/assets/opencomputers/textures/items/ocpp_ram"+t+".png"));int x=(t-4)*256+12;
   g.drawImage(before,x,24,128,128,null);g.drawImage(after,x,176,128,128,null);g.setColor(Color.WHITE);g.drawString("T"+t+" dev14 / tier-grey revised",x+132,88);g.drawString("6 contact pixels",x+132,240);
  }}g.dispose();ImageIO.write(sheet,"PNG",new File(out,"ram-hardware-before-after.png"));
 }
}
