package li.cil.oc.plusplus;

import java.io.*;
import java.nio.file.Files;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure state/pixel/source contracts, not in-game appearance or audible output. */
public class PreviewCorrectionTest {
 private File root(){return new File(System.getProperty("ocpp.sourceRoot","."));}
 private String source(String n)throws Exception{return new String(Files.readAllBytes(new File(root(),"src/main/"+n).toPath()),"UTF-8");}
 @Test public void failedStartIsFiniteRateLimitedAndNeverPersistent(){
  PreviewFailureState s=new PreviewFailureState();assertFalse(s.active());assertTrue(s.tryStart());
  for(int i=0;i<39;i++){assertFalse(s.tryStart());assertFalse(s.tick());assertTrue(s.active());}
  assertTrue(s.tick());assertFalse(s.active());assertTrue(s.tryStart());s.clear();assertFalse(s.active());
  s.synchronize(Integer.MAX_VALUE);assertEquals(40,s.remaining());s.synchronize(-1);assertFalse(s.active());
 }
 @Test public void snowPanelsPreserveNativePortsAndTransparentPixels()throws Exception{
  for(String n:new String[]{"case_top","case_side","case_back","case_front","screen/f"}){
   BufferedImage b=ImageIO.read(new File(root(),"src/main/resources/assets/opencomputers/textures/blocks/"+n+".png")),a=PreviewSprites.snow(b);int white=0;
   for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){
    int p=b.getRGB(x,y),q=a.getRGB(x,y),v=(p>>>16)&255;
    assertEquals(p>>>24,q>>>24);
    if((p>>>24)==0||v<32)assertEquals(p,q);
    if(v>=64&&Math.max((p>>>16)&255,Math.max((p>>>8)&255,p&255))-Math.min((p>>>16)&255,Math.min((p>>>8)&255,p&255))<=3){assertTrue((q&255)>=240);white++;}
   }assertTrue(n,white>0);
  }
 }
 @Test public void failedStartRequiresServerPlayerAndSameOpenContainer()throws Exception{
  String s=source("scala/li/cil/oc/common/tileentity/VMCasePreview.scala");
  for(String required:new String[]{"world.isRemote","FakePlayer","player.world != world","world.isBlockLoaded(pos)","world.getTileEntity(pos) != this","gui.preview eq this","gui.canInteractWith(player)","sendSound(world","\"--\"","failure.clear()"})assertTrue(required,s.contains(required));
  assertFalse(s.contains("override def writeToNBT"));assertFalse(s.contains("new Machine"));assertFalse(s.contains("machine.start"));
  assertEquals(1,s.split("sendMessage",-1).length-1); // Existing explicit Analyser diagnostic only.
  // Exercise the real mapped server method, with a silent sink and synthetic world.
  net.minecraft.init.Bootstrap.register();
  class SilentCase extends li.cil.oc.common.tileentity.VMCasePreview {
   int beeps,posts;
   @Override public void emitFailedStartSound(){beeps++;}
   @Override public void emitPostSound(){posts++;}
   @Override public li.cil.oc.api.network.Node node(){return null;}
  }
  SilentCase tile=new SilentCase();
  net.minecraft.world.World world=org.mockito.Mockito.mock(net.minecraft.world.World.class);
  net.minecraft.entity.player.EntityPlayer player=org.mockito.Mockito.mock(net.minecraft.entity.player.EntityPlayer.class);
  tile.setWorld(world);tile.setPos(net.minecraft.util.math.BlockPos.ORIGIN);player.world=world;
  org.mockito.Mockito.when(world.isBlockLoaded(tile.getPos())).thenReturn(true);
  org.mockito.Mockito.when(world.getTileEntity(tile.getPos())).thenReturn(tile);
  org.mockito.Mockito.when(player.isEntityAlive()).thenReturn(true);
  assertEquals(li.cil.oc.api.vm.SuperBios.MISSING,li.cil.oc.api.vm.SuperBios.statusCode());
  assertFalse(tile.trySimulatedStart(player));assertEquals(0,tile.beeps);assertFalse(tile.biosOn());
  player.openContainer=new li.cil.oc.common.container.VMCasePreview(tile,null);
  assertTrue(tile.trySimulatedStart(player));assertEquals(1,tile.beeps);assertTrue(tile.failedStartActive());
  assertEquals(BiosBootState.State.ERROR,tile.bios().state());assertFalse(tile.biosOn());
  assertFalse(tile.trySimulatedStart(player));assertEquals(1,tile.beeps);
  li.cil.oc.api.vm.SuperBios.Provider provider=new li.cil.oc.api.vm.SuperBios.Provider(){
   public int apiVersion(){return li.cil.oc.api.vm.SuperBios.API_VERSION;}
   public String backend(){return "qemu-tcg";}
  };
  li.cil.oc.api.vm.SuperBios.register(provider);
  try{
   tile.update();assertFalse("Dependency recovery must not autoboot",tile.biosOn());
   org.mockito.Mockito.when(world.getTotalWorldTime()).thenReturn(40L);
   assertTrue(tile.trySimulatedStart(player));assertTrue(tile.biosOn());assertFalse(tile.failedStartActive());
   assertEquals(1,tile.beeps);assertEquals(1,tile.posts);assertFalse(li.cil.oc.api.vm.SuperBios.mayExecuteGuest());
  }finally{li.cil.oc.api.vm.SuperBios.unregister(provider);}
  tile.update();assertFalse(tile.biosOn());assertEquals(1,tile.beeps);assertEquals(1,tile.posts);
 }
 @Test public void nativeUiReflectsBiosPowerWithSameCenteredIconAndPlayerSlots()throws Exception{
  net.minecraft.init.Bootstrap.register();
  String s=source("scala/li/cil/oc/client/gui/VMCasePreview.scala");
  for(String required:new String[]{"CustomGuiContainer","ocpp_vm_case.png","Textures.GUI.ButtonPower","guiLeft+79,guiTop+55,18,18","ySize=224","powerButton.toggled=preview.biosOn"})assertTrue(required,s.contains(required));
  assertTrue(source("scala/li/cil/oc/common/container/VMCasePreview.scala").contains("new Slot(playerInventory"));
  li.cil.oc.common.tileentity.VMCasePreview tile=new li.cil.oc.common.tileentity.VMCasePreview();
  assertFalse(tile.biosOn());assertTrue(tile.bios().power(BiosBootState.Dependency.OFFLINE_BIOS,true,0).accepted);
  assertTrue(tile.biosOn());assertTrue(tile.bios().power(BiosBootState.Dependency.OFFLINE_BIOS,true,40).accepted);assertFalse(tile.biosOn());
 }
 @Test public void faintContoursPreserveNativePaleShadingAndPlainLabels()throws Exception{
  BufferedImage b=ImageIO.read(new File(root(),"src/main/resources/assets/opencomputers/textures/items/disk.png")),a=OpticalSprites.disc(b,false);
  int p=b.getRGB(6,8),q=a.getRGB(6,8);assertEquals(p,q);assertEquals(80,OpticalSprites.contour(b,false).getRGB(6,8)>>>24);
  assertEquals(0xff333333,a.getRGB(12,10));assertEquals(b.getRGB(15,10),a.getRGB(15,10));
 }
}
