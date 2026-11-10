package li.cil.oc.plusplus;

import com.typesafe.config.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.*;

/** In-memory own-mod fixtures only; no render/audio device, game, host or VM. */
public class DiagnosticsLedContractTest {
 private File root(){return new File(System.getProperty("ocpp.sourceRoot","."));}
 private String source(String p)throws Exception{return new String(Files.readAllBytes(new File(root(),"src/main/"+p).toPath()),StandardCharsets.UTF_8);}
 private Field uniqueField(Class<?> owner,Class<?> type){
  Field found=null;for(Field f:owner.getDeclaredFields())if(f.getType()==type){assertNull("Unique own-mod fixture field",found);found=f;}
  assertNotNull(found);found.setAccessible(true);return found;
 }
 @Test public void diagnosticsDefaultsOffAndExplicitOptInPreservesPower(){
  Config d=ConfigFactory.parseFile(new File(root(),"src/main/resources/application.conf"));String key="opencomputers.plusplus.opticalAudioDiagnostics";
  assertFalse(d.getBoolean(key));assertTrue(ConfigFactory.parseString(key+"=true").withFallback(d).getBoolean(key));
  assertFalse(ConfigFactory.empty().withFallback(d).getBoolean(key));
  assertEquals(Arrays.asList(40,80,160),d.getIntList("opencomputers.plusplus.casePowerRates"));assertEquals(Arrays.asList(5,10,20),d.getIntList("opencomputers.power.rate.case"));
 }
 @Test public void actualDiagnosticBudgetIsFiniteAndCountersSaturate()throws Exception{
  Object settingsModule=li.cil.oc.Settings$.MODULE$,diag=OpticalAudioDiagnostics$.MODULE$;
  Field slot=uniqueField(settingsModule.getClass(),li.cil.oc.Settings.class);
  Object original=slot.get(settingsModule);
  AtomicInteger reports=(AtomicInteger)uniqueField(diag.getClass(),AtomicInteger.class).get(diag);
  AtomicLongArray counters=(AtomicLongArray)uniqueField(diag.getClass(),AtomicLongArray.class).get(diag);
  int oldReports=reports.get();long[] oldCounts=new long[counters.length()];for(int i=0;i<oldCounts.length;i++)oldCounts[i]=counters.get(i);
  li.cil.oc.Settings fixture=Mockito.mock(li.cil.oc.Settings.class);
  try{
   slot.set(settingsModule,fixture);for(int i=0;i<counters.length();i++)counters.set(i,0);reports.set(64);
   Mockito.when(fixture.opticalAudioDiagnostics()).thenReturn(false);
   OpticalAudioDiagnostics$.MODULE$.record(0);assertEquals(0,counters.get(0));assertEquals(64,reports.get());
   Mockito.when(fixture.opticalAudioDiagnostics()).thenReturn(true);assertEquals(16,counters.length());
   for(int round=0;round<32;round++)for(int stage=0;stage<16;stage++){OpticalAudioDiagnostics$.MODULE$.record(stage);assertTrue(reports.get()>=0);}
   assertEquals("At most64 report requests per process budget",0,reports.get());
   for(int i=0;i<4096;i++)OpticalAudioDiagnostics$.MODULE$.record(i%16);
   assertEquals(0,reports.get());counters.set(0,999999);OpticalAudioDiagnostics$.MODULE$.record(0);OpticalAudioDiagnostics$.MODULE$.record(0);assertEquals(1000000,counters.get(0));
   OpticalAudioDiagnostics$.MODULE$.record(-1);OpticalAudioDiagnostics$.MODULE$.record(16);assertEquals(0,reports.get());
   for(int i=0;i<16;i++)assertTrue(counters.get(i)<=1000000);
  }finally{
   slot.set(settingsModule,original);for(int i=0;i<oldCounts.length;i++)counters.set(i,oldCounts[i]);reports.set(oldReports);
  }
  assertSame(original,slot.get(settingsModule));assertEquals(oldReports,reports.get());for(int i=0;i<oldCounts.length;i++)assertEquals(oldCounts[i],counters.get(i));
 }
 @Test public void physicalLightSelectsBiosPowerWithoutGuestOrDiskClaim()throws Exception{
  net.minecraft.init.Bootstrap.register();li.cil.oc.common.tileentity.VMCasePreview tile=new li.cil.oc.common.tileentity.VMCasePreview();
  assertFalse(tile.biosOn());assertTrue(tile.bios().power(BiosBootState.Dependency.OFFLINE_BIOS,true,0).accepted);assertTrue(tile.biosOn());
  assertTrue(tile.bios().power(BiosBootState.Dependency.OFFLINE_BIOS,true,40).accepted);assertFalse(tile.biosOn());
  tile.bios().power(BiosBootState.Dependency.MISSING,true,80);assertFalse(tile.biosOn());assertFalse(li.cil.oc.api.vm.SuperBios.mayExecuteGuest());
  String r=source("scala/li/cil/oc/client/renderer/tileentity/VMCasePreviewRenderer.scala");
  assertTrue(r.contains("val on=preview.biosOn"));assertTrue(r.contains("if(on)Textures.Block.CaseFrontOn else Textures.Block.CaseFrontError"));
  assertTrue(r.contains("!preview.failedStartActive || !RenderUtil.shouldShowErrorLight"));assertFalse(r.contains("CaseFrontActivity"));
 }
 @Test public void missingOrDuplicatedAuthorizedDeltaIsNotSilentlyIgnored()throws Exception{
  String p="scala/li/cil/oc/Settings.scala",s=source(p),line="  val opticalAudioDiagnostics = config.getBoolean(\"plusplus.opticalAudioDiagnostics\")";
  boolean failed=false;try{ApprovedDiagnosticSource.normalize(p,s.replace(line,"").getBytes(StandardCharsets.UTF_8));}catch(AssertionError expected){failed=true;}assertTrue(failed);
  failed=false;try{ApprovedDiagnosticSource.normalize(p,(s+"\n"+line+"\n").getBytes(StandardCharsets.UTF_8));}catch(AssertionError expected){failed=true;}assertTrue(failed);
 }
}