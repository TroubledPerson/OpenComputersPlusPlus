package li.cil.oc.plusplus;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.zip.*;
import java.security.MessageDigest;

/** Original rasters remain immutable; candidate generation uses a separate root. */
public class VisualSourceFollowupTest {
 private File project(){return new File(System.getProperty("ocpp.sourceRoot","."));}
 private BufferedImage item(String n)throws Exception{return ImageIO.read(new File(project(),"src/main/resources/assets/opencomputers/textures/items/"+n+".png"));}
 @Test public void platterKeepsActualOuterAndInnerAlphaInsteadOfCircularSurround()throws Exception {
  BufferedImage p=item("disk"),r=OpticalSprites.disc(p,false);
  for(int y=0;y<10;y++)for(int x=0;x<16;x++)assertEquals("platter silhouette",p.getRGB(x,y)>>>24,r.getRGB(x,y)>>>24);
  assertEquals(p.getRGB(0,8),r.getRGB(0,8));assertEquals(p.getRGB(7,8),r.getRGB(7,8));assertEquals(p.getRGB(8,8),r.getRGB(8,8));
  assertEquals(p.getRGB(6,8),r.getRGB(6,8));assertEquals(p.getRGB(5,8),r.getRGB(5,8));
  assertEquals(0xff333333,r.getRGB(12,10));assertEquals(p.getRGB(15,10),r.getRGB(15,10));
 }
 @Test public void trayReplacesTheUpperBayAndLeavesChassisLedAndLowerPanelAlone()throws Exception {
  BufferedImage p=ImageIO.read(new File(project(),"src/main/resources/assets/opencomputers/textures/blocks/diskdrive_front.png")),n=OpticalSprites.driveFront(p);
  for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(x<2||x>13||y<2||y>6)assertEquals("unchanged chassis",p.getRGB(x,y),n.getRGB(x,y));
  for(int x=3;x<=12;x++)assertEquals("one optical slit",0xff202020,n.getRGB(x,4));
  assertEquals(0xff777777,n.getRGB(6,3));assertNotEquals(p.getRGB(6,3),n.getRGB(6,3));
 }
 @Test public void networkRecoloursBothBlueShadesOnlyAndKeepsTier3Identical()throws Exception {
  BufferedImage p=item("lancard"),a=NetworkSprites.card(p,1),b=NetworkSprites.card(p,2),c=NetworkSprites.card(p,3);int changed=0;
  for(int y=0;y<16;y++)for(int x=0;x<16;x++){int old=p.getRGB(x,y);assertEquals(old,c.getRGB(x,y));assertEquals(old>>>24,a.getRGB(x,y)>>>24);
   if(!NetworkSprites.isBlue(old)){assertEquals(old,a.getRGB(x,y));assertEquals(old,b.getRGB(x,y));}
   else{changed++;int v=a.getRGB(x,y);assertEquals((v>>>16)&255,(v>>>8)&255);assertEquals((v>>>8)&255,v&255);assertNotEquals(old,v);v=b.getRGB(x,y);assertTrue(((v>>>16)&255)>=((v>>>8)&255)&&((v>>>8)&255)>(v&255));}
  }assertTrue(changed>=25);assertEquals(p.getRGB(6,7),a.getRGB(6,7));assertEquals(p.getRGB(6,12),b.getRGB(6,12));
  assertNotEquals(p.getRGB(3,6),a.getRGB(3,6));assertNotEquals(p.getRGB(8,8),b.getRGB(8,8));
 }
 @Test public void everyOriginalTextureAndAnimationNoticeStillMatchesDev11()throws Exception {
  File zip=new File(project().getParentFile(),"deliverables/onejar-dev.11/OpenComputersPlusPlus-onejar-dev.11-source.zip");int count=0;
  try(ZipFile z=new ZipFile(zip)){for(java.util.Enumeration<? extends ZipEntry> en=z.entries();en.hasMoreElements();){ZipEntry e=en.nextElement();String n=e.getName();if(n.startsWith("src/main/resources/assets/opencomputers/textures/")&&(n.endsWith(".png")||n.endsWith(".mcmeta"))){
    try(InputStream a=z.getInputStream(e);InputStream b=new FileInputStream(new File(project(),n))){assertArrayEquals(n,hash(a),hash(b));}count++;
  }}}assertEquals(368,count);
 }
 @Test public void networkModelsKeepTier3OnTheOriginalTextureAndFlatGeometry(){
  File dir=new File(project(),"src/main/resources/assets/opencomputers/models/item");
  com.typesafe.config.Config one=com.typesafe.config.ConfigFactory.parseFile(new File(dir,"lancard.json")),two=com.typesafe.config.ConfigFactory.parseFile(new File(dir,"ocpp_network2.json")),three=com.typesafe.config.ConfigFactory.parseFile(new File(dir,"ocpp_network3.json"));
  assertEquals("opencomputers:items/ocpp_network1",one.getString("textures.layer0"));assertEquals("opencomputers:items/ocpp_network2",two.getString("textures.layer0"));
  assertEquals("opencomputers:items/lancard",three.getString("textures.layer0"));assertEquals(one.getString("parent"),three.getString("parent"));
 }
 private byte[] hash(InputStream in)throws Exception{MessageDigest h=MessageDigest.getInstance("SHA-256");byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)h.update(b,0,n);return h.digest();}
 @Test public void pairedGuiHandlersValidationSynchronizationAndMediaBackendStayUnchanged()throws Exception {
  String[] paths={"common/GuiHandler.scala","client/GuiHandler.scala","common/container/Player.scala","common/container/ComponentSlot.scala","common/container/StaticComponentSlot.scala","common/tileentity/CDDrive.scala"};
  try(ZipFile z=new ZipFile(new File(project().getParentFile(),"deliverables/onejar-dev.11/OpenComputersPlusPlus-onejar-dev.11-source.zip"))){
   for(String suffix:paths){String path="src/main/scala/li/cil/oc/"+suffix;try(InputStream a=z.getInputStream(z.getEntry(path));InputStream b=new FileInputStream(new File(project(),path))){
    if(suffix.endsWith("GuiHandler.scala")){
     // Only the appended preview dispatch may differ; legacy and CD routes stay exact.
     String current=new String(read(b),"UTF-8").replace("\r\n","\n").replace("          case t: tileentity.VMCasePreview if id == GuiType.VMCasePreview.id =>\n            new "+(suffix.startsWith("client/")?"gui":"container")+".VMCasePreview(t,player.inventory)\n","")
       .replace("          case t: tileentity.VMScreenPreview if id == GuiType.VMScreenPreview.id =>\n            new "+(suffix.startsWith("client/")?"gui.VMScreenPreview":"container.VMScreenPreview")+"(t,player)\n","");
     assertEquals(path,new String(read(a),"UTF-8").replace("\r\n","\n"),current.replace("\r\n","\n"));
    }else if(suffix.equals("common/tileentity/CDDrive.scala")) OpticalMotorSourceContract.assertDrive(project(),read(b)); else assertArrayEquals(path,hash(a),hash(b));
   }}
  }
 }
 private byte[] read(InputStream in)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)o.write(b,0,n);return o.toByteArray();}
 public static void main(String[] args)throws Exception {
  File p=new File(args[0]),out=new File(args[1]);out.mkdirs();File items=new File(p,"src/main/resources/assets/opencomputers/textures/items");File generated=new File(out,"generated");
  SoundSprites.generate(items,generated,out);OpticalSprites.generate(items,generated,out);NetworkSprites.generate(items,generated);
  BufferedImage sheet=new BufferedImage(960,340,BufferedImage.TYPE_INT_ARGB);Graphics2D g=sheet.createGraphics();g.setColor(new Color(0x777777));g.fillRect(0,0,960,340);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
  BufferedImage[] a={ImageIO.read(new File(System.getProperty("ocpp.soundcardReference", "test-fixtures/soundcard-reference.png"))),ImageIO.read(new File(p,"build/generated/tier-style-v2/assets/opencomputers/textures/items/ocpp_sound1.png")),SoundSprites.card(ImageIO.read(new File(items,"internetcard.png")),1),NetworkSprites.card(ImageIO.read(new File(items,"lancard.png")),1),NetworkSprites.card(ImageIO.read(new File(items,"lancard.png")),2),NetworkSprites.card(ImageIO.read(new File(items,"lancard.png")),3)};
  String[] names={"Owner reference","dev11 sound","Revised sound1-3","Network1 white/gray","Network2 gold","Network3 unchanged"};
  for(int i=0;i<a.length;i++){g.drawImage(a[i],i*160+16,24,128,128,null);g.setColor(Color.WHITE);g.drawString(names[i],i*160+4,16);}
  BufferedImage disk=ImageIO.read(new File(items,"disk.png")),front=ImageIO.read(new File(p,"src/main/resources/assets/opencomputers/textures/blocks/diskdrive_front.png"));
  BufferedImage[] b={disk,OpticalSprites.disc(disk,false),OpticalSprites.disc(disk,true),front,ImageIO.read(new File(p,"build/generated/tier-style-v2/assets/opencomputers/textures/blocks/ocpp_cddrive_front.png")),OpticalSprites.driveFront(front)};
  String[] labels={"Original platter","Revised CD-R","Revised CD-RW","Original floppy","dev11 CD front","Revised CD front"};
  for(int i=0;i<b.length;i++){g.drawImage(b[i],i*160+16,192,128,128,null);g.setColor(Color.WHITE);g.drawString(labels[i],i*160+4,180);}g.dispose();ImageIO.write(sheet,"PNG",new File(out,"visual-source-comparison.png"));
 }
}
