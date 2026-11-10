package li.cil.oc.plusplus;
import org.junit.Test;
import static org.junit.Assert.*;

/** Wire bounds and actual scratch checks, not guest RAM or physical input tests. */
public class BiosReportTest {
 @Test public void reportHasFixedSizeNoCallerArrayAliasAndExplicitUnknownStatus(){
  assertEquals(38,BiosReport.SIZE);assertEquals(152,BiosReport.SIZE*4);
  assertEquals(2,BiosReport.normalize(new int[10000])[BiosReport.STATUS]);
  int[] input=BiosReport.empty();input[BiosReport.NODES]=Integer.MAX_VALUE;input[BiosReport.CAPACITY_KIB]=Integer.MAX_VALUE;input[BiosReport.MEMORY_TEST]=999;input[BiosReport.OPTICAL_ENABLED]=-1;input[BiosReport.OPTICAL_BASE]=Integer.MAX_VALUE;
  int[] output=BiosReport.normalize(input);assertEquals(256,output[BiosReport.NODES]);assertEquals(Integer.MAX_VALUE,output[BiosReport.CAPACITY_KIB]);assertEquals(2,output[BiosReport.MEMORY_TEST]);assertEquals(0,output[BiosReport.OPTICAL_ENABLED]);assertEquals(1000000,output[BiosReport.OPTICAL_BASE]);
  input[BiosReport.NODES]=1;assertEquals(256,output[BiosReport.NODES]);
 }
 @Test public void windowShortsRequireACompleteAtomicSnapshotAndRetainLargeQuotas(){
  BiosReport.Words words=new BiosReport.Words();assertNull(words.accept(BiosReport.REPORT_COMMIT,0));
  int[] source=BiosReport.empty();source[BiosReport.CAPACITY_KIB]=Integer.MAX_VALUE;source[BiosReport.OPTICAL_BASE]=1000000;source[BiosReport.STATUS]=1;source[BiosReport.COLUMNS]=3;
  for(int i=0;i<BiosReport.SIZE;i++)for(int half=0;half<2;half++)assertNull(words.accept(BiosReport.REPORT_FIRST+i*2+half,(short)(source[i]>>>(16*half))));
  assertArrayEquals(source,words.accept(BiosReport.REPORT_COMMIT,1));assertNull(words.accept(BiosReport.REPORT_COMMIT,1));
  words.accept(BiosReport.REPORT_FIRST,42);assertNull(words.accept(BiosReport.REPORT_COMMIT,2));assertNull(words.accept(Integer.MAX_VALUE,65535));
 }
 @Test public void corruptedScratchDataCannotPassAndWorkingSetIsOnly64KiB(){
  assertEquals(65536,BiosReport.SCRATCH_BYTES);byte[] scratch=new byte[BiosReport.SCRATCH_BYTES];for(int i=0;i<scratch.length;i++)scratch[i]=(byte)(85^i);
  assertTrue(BiosReport.matches(scratch,85));scratch[65535]^=1;assertFalse(BiosReport.matches(scratch,85));assertFalse(BiosReport.matches(new byte[1],0));assertEquals(1,BiosReport.memorySelfTest());
 }
 @Test public void originalPageOrdinalsAndMockHitRemainCompatibleWhileAudioBackReturnsToDiagnostics(){
  assertEquals(0,BiosBootState.Page.MAIN.ordinal());assertEquals(3,BiosBootState.Page.DIAGNOSTICS.ordinal());assertEquals(4,BiosBootState.Page.AUDIO.ordinal());assertEquals(0,BiosBootState.actionAt(3,.5,.55));assertEquals(1,BiosReport.inputSelfTest());
  BiosBootState state=new BiosBootState();assertFalse(state.openAudio(0,true));state.power(BiosBootState.Dependency.OFFLINE_BIOS,true,0);long epoch=state.epoch();state.click(.5,.62,epoch,true);
  assertFalse(state.openAudio(epoch-1,true));assertFalse(state.openAudio(epoch,false));assertTrue(state.openAudio(epoch,true));assertEquals(BiosBootState.Page.AUDIO,state.page());assertEquals(4,BiosBootState.actionAt(4,.5,.9));assertTrue(state.key(BiosBootState.Key.BACK,epoch,true));assertEquals(BiosBootState.Page.DIAGNOSTICS,state.page());assertFalse(state.mayExecuteGuest());
 }
 @Test public void readingEventAndFiniteIdlePolicyAreUnchanged(){
  assertEquals("ocpp_cd_read",OpticalSoundSchedule.EVENTS[OpticalSoundSchedule.READ]);OpticalSoundSchedule s=new OpticalSoundSchedule();s.requestRead();assertEquals(OpticalSoundSchedule.READ,s.tick(0,true));for(int tick=1;tick<=12;tick++)assertEquals(-1,s.tick(tick,true));assertEquals(OpticalSoundSchedule.STOP,s.tick(13,true));assertEquals(-1,s.tick(14,true));assertEquals(740,OpticalSoundSchedule.MAX_TICKS[OpticalSoundSchedule.READ]);
 }
}
