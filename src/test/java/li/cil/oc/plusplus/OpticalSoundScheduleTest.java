package li.cil.oc.plusplus;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpticalSoundScheduleTest {
 @Test public void realReadsPulseAtMostOncePerTenTicksAndIdleStopsOnce(){
  OpticalSoundSchedule s=new OpticalSoundSchedule();assertEquals(-1,s.tick(0,true));s.requestRead();assertEquals(2,s.tick(1,true));
  for(int t=2;t<=10;t++){s.requestRead();assertEquals(-1,s.tick(t,true));}s.requestRead();assertEquals(2,s.tick(11,true));
  assertEquals(-1,s.tick(23,true));assertEquals(3,s.tick(24,true));assertEquals(-1,s.tick(25,true));
 }
 @Test public void verifiedShortReadDuringClosingIsDeferredOnceAndEjectCancelsIt(){
  OpticalSoundSchedule s=new OpticalSoundSchedule();assertEquals(1,s.tray(0,true));s.requestRead();assertEquals(-1,s.tick(1,true));assertEquals(2,s.tick(40,true));
  assertEquals(-1,s.tick(41,true));assertEquals(0,s.tray(42,false));assertFalse(s.isReading());assertEquals(-1,s.tick(43,false));
 }
 @Test public void continuousReadsWaitForTrayCloseAndDoNotStack(){
  OpticalSoundSchedule s=new OpticalSoundSchedule();s.tray(0,true);for(int i=1;i<40;i++){s.requestRead();assertEquals(-1,s.tick(i,true));}s.requestRead();assertEquals(2,s.tick(40,true));
  for(int i=41;i<50;i++){s.requestRead();assertEquals(-1,s.tick(i,true));}
 }
 @Test public void removalResetAndClockRollbackRevokePendingRead(){
  OpticalSoundSchedule s=new OpticalSoundSchedule();s.requestRead();assertEquals(2,s.tick(100,true));assertEquals(3,s.tick(101,false));assertEquals(-1,s.tick(102,false));
  s.requestRead();s.reset();assertEquals(-1,s.tick(103,true));s.requestRead();assertEquals(2,s.tick(104,true));assertEquals(3,s.tick(0,true));assertEquals(-1,s.tick(1,true));
 }
 @Test public void directCallbacksCoalesceIntoOnePendingBit(){
  OpticalSoundSchedule s=new OpticalSoundSchedule();for(int i=0;i<1000;i++)s.requestRead();assertEquals(2,s.tick(0,true));assertEquals(-1,s.tick(1,true));assertEquals(3,s.tick(13,true));
 }
 @Test public void fixedEventsAndClientBoundsHaveNoLoopOrUnboundedDuration(){
  assertArrayEquals(new String[]{"ocpp_cd_open","ocpp_cd_close","ocpp_cd_read"},OpticalSoundSchedule.EVENTS);
  assertArrayEquals(new int[]{42,42,740},OpticalSoundSchedule.MAX_TICKS);assertEquals(8,OpticalSoundSchedule.MAX_CLIENT_DRIVES);assertEquals(3,OpticalSoundSchedule.STOP);
 }
 @Test public void deferredBurstHasBoundedLeaseAndNeverPlaysWithoutARead(){
  OpticalSoundSchedule s=new OpticalSoundSchedule();s.tray(0,true);for(int t=1;t<=60;t++)assertEquals(-1,s.tick(t,true));
  s.tray(100,true);for(int t=101;t<140;t++){if(t==102)for(int i=0;i<1000;i++)s.requestRead();assertEquals(-1,s.tick(t,true));}
  assertEquals(2,s.tick(140,true));assertEquals(-1,s.tick(152,true));assertEquals(3,s.tick(153,true));assertEquals(-1,s.tick(154,true));
 }
 @Test public void removalClockRollbackAndNewTrayRevokeDeferredOldActivity(){
  OpticalSoundSchedule s=new OpticalSoundSchedule();s.tray(0,true);s.requestRead();s.tick(1,true);s.tick(2,false);assertEquals(-1,s.tick(40,true));
  s.tray(100,true);s.requestRead();s.tick(101,true);s.tray(102,false);s.tick(103,false);s.tray(104,true);assertEquals(-1,s.tick(144,true));
  s.tray(200,true);s.requestRead();s.tick(201,true);s.tick(0,true);assertEquals(-1,s.tick(240,true));
 }
}
