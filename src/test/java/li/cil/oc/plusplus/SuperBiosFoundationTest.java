package li.cil.oc.plusplus;

import li.cil.oc.api.vm.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Pure metadata/mock tests; no executable, VM, host disk, guest or network. */
public class SuperBiosFoundationTest {
 static final UUID WORLD=UUID.randomUUID(),CASE=UUID.randomUUID(),OWNER=UUID.randomUUID();
 static String manifest(){return "{\"schema\":1,\"hostOS\":\"windows\",\"hostArchitecture\":\"x86_64\",\"machineVersion\":\"unqualified\",\"cpuModel\":\"unselected\",\"firmwareLicense\":\"unreviewed\",\"diskFormat\":\"qcow2\",\"nvramId\":\""+UUID.randomUUID()+"\",\"ownerId\":\""+OWNER+"\",\"vcpus\":1,\"ramMiB\":128,\"externalDependencies\":[],\"backend\":\"qemu-tcg\",\"packageId\":\""+UUID.randomUUID()+"\",\"worldId\":\""+WORLD+"\",\"caseId\":\""+CASE+"\",\"raidId\":\""+UUID.randomUUID()+"\",\"volumeId\":\""+UUID.randomUUID()+"\",\"architecture\":\"x86_64\",\"machine\":\"pc\",\"firmware\":\"unselected\",\"firmwareSha256\":\""+String.join("",Collections.nCopies(64,"0"))+"\",\"diskPath\":\"volumes/aggregate.qcow2\",\"capacityPolicy\":\"unapproved\",\"capacityBytes\":6,\"memberIds\":[\""+UUID.randomUUID()+"\",\""+UUID.randomUUID()+"\",\""+UUID.randomUUID()+"\"],\"memberBytes\":[1,2,3]}";}
 interface Action {void run()throws Exception;}
 static void denied(Action a)throws Exception{try{a.run();fail("must reject");}catch(IllegalArgumentException|IllegalStateException|SecurityException expected){}}
 @Test public void aggregateManifestKeepsBytesAndCannotExecute(){VmManifest.Plan p=VmManifest.validate(manifest());assertEquals(6,p.capacityBytes);assertEquals(3,p.members.size());assertFalse(p.executable());assertEquals("unapproved",p.capacityPolicy);}
 @Test public void strictManifestRejectsDuplicatesTypesScalingAndUnknownOperations()throws Exception{
  String m=manifest();for(String bad:new String[]{m.replace("\"schema\":1","\"schema\":1,\"schema\":1"),m.replace("\"schema\":1","\"schema\":\"1\""),m.replace("capacityBytes\":6","capacityBytes\":7"),m.replace("unapproved","mib-to-gib"),m.replace("\"architecture\":\"x86_64\"","\"architecture\":\"anything\""),m.replace("\"schema\":1","\"command\":\"run\",\"schema\":1"),m+"{}",m.replace("[1,2,3]","[1,2,-3]")})denied(()->VmManifest.validate(bad));
 }
 @Test public void pathsRejectTraversalHostRootsLinksByNameAndPlatformAmbiguity()throws Exception{
  for(String p:new String[]{"../disk","/disk","C:/disk","a\\b","a//b","a/./b","CON.qcow2","aux","x.","a;b","a b"})denied(()->VmManifest.relative(p));
  assertEquals("volumes/one.qcow2",VmManifest.relative("volumes/one.qcow2"));
 }
 @Test public void providerDiscoveryIsExplicitCompatibleAndAlwaysOffline()throws Exception{
  assertEquals(0,SuperBios.statusCode());SuperBios.Provider p=new li.cil.oc.vm.companion.OCVMIntegration.OfflineProvider();
  try{SuperBios.register(p);assertEquals(1,SuperBios.statusCode());assertFalse(SuperBios.mayExecuteGuest());denied(()->SuperBios.register(new li.cil.oc.vm.companion.OCVMIntegration.OfflineProvider()));}
  finally{SuperBios.unregister(p);}assertEquals(0,SuperBios.statusCode());
  denied(()->SuperBios.register(new SuperBios.Provider(){public int apiVersion(){return 2;}public String backend(){return "qemu-tcg";}}));
 }
 @Test public void aggregateClaimRejectsDuplicateMountAndRetainsDataOnRunningRemoval()throws Exception{
  UUID raid=UUID.randomUUID(),v=UUID.randomUUID();AggregateLease a=new AggregateLease(WORLD,raid,v,OWNER),b=new AggregateLease(WORLD,raid,v,OWNER);
  denied(()->a.acquire(WORLD,CASE,UUID.randomUUID()));a.acquire(WORLD,CASE,OWNER);denied(()->b.acquire(WORLD,UUID.randomUUID(),OWNER));a.observe(AggregateLease.State.RUNNING);a.removed();assertEquals(AggregateLease.State.PENDING_DETACH,a.state());assertTrue(a.imageRetained());denied(a::detachAfterConfirmedOff);a.observe(AggregateLease.State.OFF);a.detachAfterConfirmedOff();assertTrue(a.imageRetained());b.acquire(WORLD,CASE,OWNER);b.removed();assertEquals(AggregateLease.State.DETACHED,b.state());
 }
 @Test public void mockTransportHasIdentityReplayRevocationAndNoQmpPassthrough()throws Exception{
  MockVmTransport t=new MockVmTransport(WORLD,CASE,OWNER);UUID n=UUID.randomUUID();assertTrue(t.request(WORLD,CASE,OWNER,n,"status","").contains("offline"));denied(()->t.request(WORLD,CASE,OWNER,n,"status",""));denied(()->t.request(WORLD,CASE,UUID.randomUUID(),UUID.randomUUID(),"status",""));assertTrue(t.request(WORLD,CASE,OWNER,UUID.randomUUID(),"validate-manifest",manifest()).contains("dry-run"));denied(()->t.request(WORLD,CASE,OWNER,UUID.randomUUID(),"execute","{}"));denied(()->t.request(WORLD,CASE,OWNER,UUID.randomUUID(),"status",new String(new char[65537])));t.revoke();denied(()->t.request(WORLD,CASE,OWNER,UUID.randomUUID(),"status",""));
 }
 @Test public void nativePanelMovesInventoryBelowBoardWithoutChangingOriginalPixels()throws Exception{
  File p=new File(System.getProperty("ocpp.sourceRoot","."));BufferedImage nativePanel=ImageIO.read(new File(p,"src/main/resources/assets/opencomputers/textures/gui/background.png"));
  File generated=new File(System.getProperty("ocpp.spriteGenerated",new File(p,"build/generated/tier-style-v2").getPath()));BufferedImage panel=ImageIO.read(new File(generated,"assets/opencomputers/textures/gui/ocpp_vm_case.png"));
  assertEquals(256,panel.getHeight());for(int y=140;y<224;y++)for(int x=0;x<176;x++)assertEquals(nativePanel.getRGB(x,y-58),panel.getRGB(x,y));
  assertTrue(96<100&&109<111&&125<130&&139<142);assertEquals(0,panel.getRGB(200,230));
 }
 @Test public void offlineCatalogueRejectsOtherOwnersDuplicatePackagesAndExternalDependencies()throws Exception{
  OfflineVmManager m=new OfflineVmManager(WORLD,CASE,OWNER);String plan=manifest();m.stage(OWNER,plan);assertEquals(1,m.list(OWNER).size());assertFalse(m.mayLaunch());denied(()->m.stage(OWNER,plan));denied(()->m.list(UUID.randomUUID()));
  denied(()->VmManifest.validate(plan.replace("\"externalDependencies\":[]","\"externalDependencies\":[\"../parent.vdi\"]")));
  denied(()->VmManifest.validate(plan.replace("\"vcpus\":1","\"vcpus\":4294967297")));
  for(String os:new String[]{"windows","linux","macos"}){VmManifest.Plan p=VmManifest.validate(plan.replace("\"hostOS\":\"windows\"","\"hostOS\":\""+os+"\""));assertEquals(os,p.hostOS);assertFalse(p.executable());}
  m.revoke();denied(()->m.list(OWNER));assertTrue(m.supportStatus().contains("untested"));
 }
}
