package li.cil.oc.plusplus;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import com.typesafe.config.*;
import org.junit.Test;
import static org.junit.Assert.*;
import net.minecraft.util.EnumFacing;

/** Source/resource contracts; no game renderer, display or host backend executed. */
public class VMScreenPreviewSourceTest {
 private File root(){return new File(System.getProperty("ocpp.sourceRoot","."));}
 private String source(String n)throws Exception{return new String(Files.readAllBytes(new File(root(),"src/main/"+n).toPath()),"UTF-8");}
 @Test public void exactNineSlotRecipeRequiresFourEnchantedApplesAndFourBeacons()throws Exception{
  Config c=ConfigFactory.parseFile(new File(root(),"src/main/resources/assets/opencomputers/recipes/tier7-preview.recipes")).getConfig("ocpp_screen7_preview");
  assertEquals(1,c.getInt("output"));assertEquals("shaped",c.getString("type"));List<?> rows=c.getList("input").unwrapped();assertEquals(3,rows.size());
  int apples=0,beacons=0,centres=0;for(int y=0;y<3;y++){List<?> row=(List<?>)rows.get(y);assertEquals(3,row.size());for(int x=0;x<3;x++){
   Object v=row.get(x);if(x==1&&y==1){assertEquals("oc:ocpp_screen6",v);centres++;}
   else if(x!=1&&y!=1){assertEquals("minecraft:beacon",v);beacons++;}
   else{assertTrue(v instanceof Map);Map<?,?> m=(Map<?,?>)v;assertEquals("minecraft:golden_apple",m.get("item"));assertEquals(1,((Number)m.get("subID")).intValue());apples++;}
  }}assertEquals(4,apples);assertEquals(4,beacons);assertEquals(1,centres);
  assertTrue(new StageOneDataTest().recipes().hasPath("ocpp_screen7_preview"));
 }
 @Test public void screenPreviewDoesNotAcquireFunctionalScreenCapabilities()throws Exception{
  String block=source("scala/li/cil/oc/common/block/VMScreenPreview.scala"),tile=source("scala/li/cil/oc/common/tileentity/VMScreenPreview.scala");
  assertTrue(block.contains("extends SimpleBlock with traits.GUI"));assertTrue(tile.contains("extends TileEntity with Analyzable"));
  for(String s:new String[]{block,tile,source("scala/li/cil/oc/client/gui/VMScreenPreview.scala")})for(String forbidden:new String[]{"api.Network","api.internal.TextBuffer","PacketSender","ProcessBuilder","Runtime.getRuntime","screenResolutionsByTier","new tileentity.Screen"})assertFalse(forbidden,s.contains(forbidden));
  String registration=source("scala/li/cil/oc/client/renderer/block/ModelInitialization.scala");
  assertTrue(registration.contains("\"ocpp_screen7_preview\" -> (_ => VMScreenPreviewModel)"));
  for(String ordinary:new String[]{"ScreenTier1","ScreenTier2","ScreenTier3","\"ocpp_screen4\"","\"ocpp_screen5\"","\"ocpp_screen6\""})
   assertTrue("ordinary tiers retain their model",registration.contains(ordinary+" -> (_ => ScreenModel)")||registration.contains("Constants.BlockName."+ordinary+" -> (_ => ScreenModel)"));
  String model=source("scala/li/cil/oc/client/renderer/block/VMScreenPreviewModel.scala");
  assertTrue(model.contains("Textures.Block.vmScreen(native)"));
  assertTrue(model.contains("other.canvasFingerprint==screen.canvasFingerprint"));
  assertFalse("frame geometry cannot depend on BIOS power",model.contains("displayBoot"));
  assertFalse("frame geometry cannot depend on menu state",model.contains("displayPage"));
  for(String forbidden:new String[]{"api.Network","api.internal.TextBuffer","ProcessBuilder","Runtime.getRuntime","new tileentity.Screen"})assertFalse(forbidden,model.contains(forbidden));
  assertFalse(source("scala/li/cil/oc/client/ColorHandler.scala").contains("ocpp_screen7_preview"));
 }
 private void part(int w,int h,int x,int y,EnumFacing side,String expected){
  assertEquals(w+"x"+h+" "+x+","+y+" "+side,"blocks/screen/"+expected,
   li.cil.oc.client.renderer.block.VMScreenPreviewModel$.MODULE$.nativeTexture(w,h,x,y,side).getPath());
 }
 @Test public void connectedNativePartsSpanEveryExteriorFaceWithoutRendering(){
  // Calls the production selection function, without getQuads, texture loading or GL.
  String[] single={"b","b","f","b2","b2","b2"};
  for(EnumFacing side:EnumFacing.values())part(1,1,0,0,side,single[side.getIndex()]);
  String[] hf={"fht2","fhm2","fhb2"},hb={"bhb2","bhm2","bht2"},caps={"bht","bhm","bhb"};
  for(int x=0;x<3;x++){
   part(3,1,x,0,EnumFacing.NORTH,hf[x]);part(3,1,x,0,EnumFacing.SOUTH,hb[x]);
   part(3,1,x,0,EnumFacing.UP,caps[x]);part(3,1,x,0,EnumFacing.DOWN,caps[x]);
  }
  part(3,1,0,0,EnumFacing.EAST,"b2");part(3,1,2,0,EnumFacing.WEST,"b2");
  String[] vf={"fvb2","fvm","fvt"},vb={"bvb2","bvm","bvt"};
  for(int y=0;y<3;y++){
   part(1,3,0,y,EnumFacing.NORTH,vf[y]);part(1,3,0,y,EnumFacing.SOUTH,vb[y]);
   part(1,3,0,y,EnumFacing.EAST,vb[y]);part(1,3,0,y,EnumFacing.WEST,vb[y]);
  }
  part(1,3,0,2,EnumFacing.UP,"b");part(1,3,0,0,EnumFacing.DOWN,"b");
  String[][] front={{"fbl2","fbm2","fbr2"},{"ftl","ftm","ftr"}},back={{"bbr2","bbm2","bbl2"},{"btr","btm","btl"}};
  for(int x=0;x<3;x++)for(int y=0;y<2;y++){
   part(3,2,x,y,EnumFacing.NORTH,front[y][x]);part(3,2,x,y,EnumFacing.SOUTH,back[y][x]);
  }
  for(int x=0;x<3;x++){part(3,2,x,1,EnumFacing.UP,caps[x]);part(3,2,x,0,EnumFacing.DOWN,caps[x]);}
  for(int y=0;y<2;y++){
   part(3,2,0,y,EnumFacing.EAST,y==0?"bvb2":"bvt");part(3,2,2,y,EnumFacing.WEST,y==0?"bvb2":"bvt");
  }
 }
 @Test public void registrationAndUiAreSeparateAppendOnlyAndExplicitlyDisconnected()throws Exception{
  String init=source("scala/li/cil/oc/common/init/PlusPlus.scala");assertTrue(init.indexOf("new li.cil.oc.common.block.VMScreenPreview")>init.indexOf("new li.cil.oc.common.block.VMCasePreview"));
  String lang=source("resources/assets/opencomputers/lang/en_US.lang");assertTrue(lang.contains("\u00a7f\u00a7l\u00a7oTier 7 VM Screen (Preview)\u00a7r"));
  assertTrue(lang.contains("No framebuffer or VM display"));
  String gui=source("scala/li/cil/oc/client/gui/VMScreenPreview.scala");assertFalse(gui.contains("new GuiButton"));assertTrue(gui.contains("BiosScreenView.draw"));assertTrue(gui.contains("sendEnchantPacket"));
  assertTrue(source("scala/li/cil/oc/client/renderer/tileentity/VMScreenPreviewRenderer.scala").contains("BiosScreenView.draw"));
  assertTrue(source("scala/li/cil/oc/client/gui/BiosScreenView.scala").contains("ocpp.superbios.title"));
  String types=source("scala/li/cil/oc/common/GuiType.scala");assertTrue(types.indexOf("val VMScreenPreview")>types.indexOf("val VMCasePreview"));
 }
 @Test public void passiveModelKeepsNativeFrameAndNoRunningVariant()throws Exception{
  Config m=ConfigFactory.parseFile(new File(root(),"src/main/resources/assets/opencomputers/models/block/ocpp_screen7_preview.json"));
  assertEquals("opencomputers:blocks/ocpp_vm_screen_front",m.getString("textures.north"));assertEquals("opencomputers:blocks/ocpp_vm_screen_cap",m.getString("textures.up"));
  String state=source("resources/assets/opencomputers/blockstates/ocpp_screen7_preview.json");assertTrue(state.contains("normal"));assertFalse(state.contains("running"));
 }
 @Test public void actualIngredientParserRejectsRegularGoldenApple(){
  net.minecraft.init.Bootstrap.register();
  Map<Object,Object> m=new HashMap<>();m.put("item","minecraft:golden_apple");m.put("subID",1);
  net.minecraft.item.ItemStack enchanted=(net.minecraft.item.ItemStack)li.cil.oc.common.recipe.Recipes$.MODULE$.parseIngredient(m);
  assertEquals(net.minecraft.init.Items.GOLDEN_APPLE,enchanted.getItem());assertEquals(1,enchanted.getMetadata());
  net.minecraft.item.crafting.Ingredient match=net.minecraft.item.crafting.Ingredient.fromStacks(enchanted);
  assertTrue(match.apply(new net.minecraft.item.ItemStack(net.minecraft.init.Items.GOLDEN_APPLE,1,1)));
  assertFalse(match.apply(new net.minecraft.item.ItemStack(net.minecraft.init.Items.GOLDEN_APPLE,1,0)));
 }
}
