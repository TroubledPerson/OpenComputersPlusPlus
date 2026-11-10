package li.cil.oc.plusplus;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Source/pixel contracts only; actual OpenGL and Minecraft appearance are unrun. */
public class Tier7ControlVisualTest {
 private File root(){return new File(System.getProperty("ocpp.sourceRoot","."));}
 private String source(String p)throws Exception{return new String(Files.readAllBytes(new File(root(),"src/main/"+p).toPath()),"UTF-8");}
 @Test public void caseHasOnlySamePowerAndInventoryWithoutAnyTextOrBoard()throws Exception{
  String s=source("scala/li/cil/oc/client/gui/VMCasePreview.scala");
  assertTrue(s.contains("guiLeft+79,guiTop+55,18,18"));assertTrue(s.contains("xSize=176")&&s.contains("ySize=224"));assertTrue(s.contains("PacketSender.sendPreviewPower(preview)"));
  for(String bad:new String[]{"drawString","drawCenteredString","drawSplitString","Textures.GUI.Computer","managerButton","managerMode","providerObserved","Localization"})assertFalse(bad,s.contains(bad));
 }
 @Test public void completeNativeStripePortAndIndicatorsSurviveContinuousWhitePanels()throws Exception{
  BufferedImage b=ImageIO.read(new File(root(),"src/main/resources/assets/opencomputers/textures/blocks/case_front.png"));BufferedImage snow=PreviewSprites.snow(b),a=PreviewSprites.caseFrontSnow(b);int changed=0;
  for(int y=0;y<16;y++)for(int x=0;x<16;x++){
   int p=b.getRGB(x,y),q=a.getRGB(x,y);assertEquals(p>>>24,q>>>24);
   if(x>=6&&x<=7&&y>=5&&y<=14){assertEquals(p,q);assertTrue((q&255)<=34);if(q!=snow.getRGB(x,y))changed++;}
   else if(y==14&&x>=12&&x<=14)assertEquals("native lower port",p,q);
   else if(Math.max((p>>>16)&255,Math.max((p>>>8)&255,p&255))-Math.min((p>>>16)&255,Math.min((p>>>8)&255,p&255))>3)assertEquals("indicator colour",p,q);
   else assertTrue("white panels retain bounded soft shadows",(q&255)>=216);
  }
  assertEquals(6,changed); // Upper3 rows x2: original33/34 had been whitened.
 }
 @Test public void worldOutputRendererAndScreenControlsAreActuallyRegistered()throws Exception{
  String r=source("scala/li/cil/oc/client/renderer/tileentity/VMScreenPreviewRenderer.scala");assertTrue(r.contains("TileEntitySpecialRenderer[VMScreenPreview]"));assertTrue(r.contains("if(!screen.canvasReady)return"));assertTrue(r.contains("BiosScreenView.draw"));assertTrue(r.contains("z-0.002"));
  assertTrue(source("scala/li/cil/oc/client/Proxy.scala").contains("bindTileEntitySpecialRenderer(classOf[tileentity.VMScreenPreview], VMScreenPreviewRenderer)"));
  String g=source("scala/li/cil/oc/client/gui/VMScreenPreview.scala");assertTrue(g.contains("sendEnchantPacket"));assertTrue(g.contains("BiosScreenView.draw"));assertTrue(g.contains("BiosBootState.actionAt"));assertFalse(g.contains("Textures.GUI.Computer"));
  String t=source("scala/li/cil/oc/common/tileentity/VMScreenPreview.scala");assertTrue(t.contains("notifyBlockUpdate"));assertTrue(t.contains("getUpdatePacket"));assertTrue(t.contains("group.hit"));assertTrue(t.contains("biosAction(player,action,displayEpoch)"));
  // The shared front hit reaches the same menu row independent of which block is used.
  java.util.UUID world=java.util.UUID.randomUUID(),binding=java.util.UUID.randomUUID();
  BiosCanvasLayout.Basis basis=new BiosCanvasLayout.Basis(new BiosCanvasLayout.Axis(0,0,-1),new BiosCanvasLayout.Axis(0,1,0));
  java.util.List<BiosCanvasLayout.Cell> cells=new java.util.ArrayList<>();BiosCanvasLayout.Cell middleTop=null;
  for(int x=0;x<3;x++)for(int y=0;y<2;y++){
   BiosCanvasLayout.Cell cell=new BiosCanvasLayout.Cell(world,x,y,0,basis,7,0xFFFFFF,java.util.UUID.randomUUID(),binding);cells.add(cell);if(x==1&&y==1)middleTop=cell;
  }
  java.util.List<BiosCanvasLayout.Group> groups=BiosCanvasLayout.partition(cells,8,6);assertEquals(1,groups.size());
  BiosCanvasLayout.Group group=groups.get(0);assertEquals(3,group.width);assertEquals(2,group.height);assertEquals(2,group.origin.x);assertEquals(0,group.origin.y);
  int origins=0;for(BiosCanvasLayout.Cell cell:cells)if(group.isOrigin(cell))origins++;assertEquals(1,origins);
  BiosCanvasLayout.Point hit=group.hit(middleTop,basis.normal,.5,.34375,0);assertNotNull(hit);assertEquals(.5,hit.x,1e-9);assertEquals(.3,hit.y,1e-9);
  assertEquals(10,BiosBootState.actionAt(0,hit.x,hit.y));assertNull(group.hit(middleTop,new BiosCanvasLayout.Axis(0,0,1),.5,.34375,1));
  BiosBootState boot=new BiosBootState();boot.power(BiosBootState.Dependency.OFFLINE_BIOS,true,0);long epoch=boot.epoch();
  assertTrue(boot.click(hit.x,hit.y,epoch,true));assertEquals(BiosBootState.Page.LIBRARY,boot.page());
  boot.dependencyChanged(BiosBootState.Dependency.MISSING);assertFalse(boot.mayProject());assertFalse(boot.click(.5,.9,epoch,true));
 }
}
