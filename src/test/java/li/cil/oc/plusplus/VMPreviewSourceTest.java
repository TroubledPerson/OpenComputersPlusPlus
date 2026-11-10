package li.cil.oc.plusplus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.*;

/** Offline safety/compatibility checks; not a running Minecraft GUI test. */
public class VMPreviewSourceTest {
 private File root(){return new File(System.getProperty("ocpp.sourceRoot","."));}
 private String source(String suffix)throws Exception{return new String(Files.readAllBytes(new File(root(),"src/main/"+suffix).toPath()),StandardCharsets.UTF_8).replace("\r\n","\n");}
 @Test public void legacyGuiOrdinalsAndItemMetadataStayAppendOnly()throws Exception{
  String path="scala/li/cil/oc/common/GuiType.scala", now=source(path);
  try(ZipFile old=new ZipFile(new File(root().getParentFile(),"deliverables/onejar-dev.11/OpenComputersPlusPlus-onejar-dev.11-source.zip"))){
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=old.getInputStream(old.getEntry("src/main/"+path))){byte[] b=new byte[4096];int n;while((n=in.read(b))>=0)out.write(b,0,n);}
   String previous=new String(out.toByteArray(),StandardCharsets.UTF_8).replace("\r\n","\n");
   assertEquals(previous,now.replace("  // Append only: preserve every existing GUI protocol ordinal.\n  val VMCasePreview = new EnumVal { def name = \"VMCasePreview\"; def subType = GuiType.Category.Block }\n","")
     .replace("  val VMScreenPreview = new EnumVal { def name = \"VMScreenPreview\"; def subType = GuiType.Category.Block }\n",""));
  }
  String registration=source("scala/li/cil/oc/common/init/PlusPlus.scala");
  assertTrue(registration.indexOf("Items.registerBlock(new li.cil.oc.common.block.VMCasePreview")>registration.indexOf("new item.SoundCard"));
  assertFalse(registration.contains("Recipes.addBlock(new li.cil.oc.common.block.VMCasePreview"));
 }
 @Test public void previewHasNoMachineInventoryOrHostCapabilities()throws Exception{
  String tile=source("scala/li/cil/oc/common/tileentity/VMCasePreview.scala");
  assertTrue(tile.contains("extends TileEntity with Analyzable"));
  assertTrue(tile.contains("!getWorld.isRemote"));assertTrue(tile.contains("!player.isInstanceOf[FakePlayer]"));
  assertTrue(tile.contains("Localization.Analyzer.LastError(li.cil.oc.api.vm.SuperBios.statusKey"));
  String c=source("scala/li/cil/oc/common/container/VMCasePreview.scala");
  assertTrue(c.contains("new Slot(playerInventory"));assertTrue(c.contains("getTileEntity(preview.getPos) == preview"));assertTrue(c.contains("<= 64"));
  for(String p:new String[]{"common/tileentity/","common/block/","common/container/","client/gui/"}){
   String s=source("scala/li/cil/oc/"+p+"VMCasePreview.scala");
   for(String forbidden:new String[]{"Runtime.getRuntime","ProcessBuilder","java.io.File","java.net.","machine.start", "new Machine", "api.Machine"})assertFalse(p+forbidden,s.contains(forbidden));
  }
 }
 @Test public void buttonOnlyChangesVisibleStatusAndChatIsAnalyserOnly()throws Exception{
  String gui=source("scala/li/cil/oc/client/gui/VMCasePreview.scala");
  assertTrue(gui.contains("if(button.id==0)PacketSender.sendPreviewPower(preview)"));assertFalse(gui.contains("sendMessage"));
  assertTrue(gui.contains("guiLeft+79,guiTop+55,18,18"));assertTrue(gui.contains("ySize=224"));
  assertFalse(source("scala/li/cil/oc/common/block/VMCasePreview.scala").contains("sendMessage"));
  String lang=source("resources/assets/opencomputers/lang/en_US.lang");
  assertTrue(lang.contains("PREVIEW - backend not connected"));assertTrue(lang.contains("Tier 7 requires the VirtualBox Integration mod."));
  assertTrue(lang.contains("\u00a7f\u00a7l\u00a7oTier 7 VM Case (Preview)\u00a7r"));
 }
 @Test public void previewUsesSeparateSnowCaseAndDoesNotReplaceCreativeIcon()throws Exception{
  assertTrue(source("resources/assets/opencomputers/models/item/ocpp_case7_preview.json").contains("opencomputers:block/ocpp_case7_preview"));
  assertTrue(source("resources/assets/opencomputers/blockstates/ocpp_case7_preview.json").contains("opencomputers:ocpp_case7_preview"));
  assertFalse(source("scala/li/cil/oc/client/ColorHandler.scala").contains("ocpp_case7_preview"));
  assertFalse(source("scala/li/cil/oc/CreativeTab.scala").contains("ocpp_case7_preview"));
 }
}
