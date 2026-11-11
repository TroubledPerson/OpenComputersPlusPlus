package li.cil.oc.plusplus;
import org.junit.Test;import static org.junit.Assert.*;
public class OpticalMotorTest {
 @Test public void initializedPoweredDiscKeepsMotorAcrossLongIdleAndManyClipLengths(){
  OpticalMotorState s=new OpticalMotorState();OpticalMotorPlayback p=new OpticalMotorPlayback();long epoch=OpticalMotorState.newEpoch();
  for(int t=0;t<10000;t++){int action=s.tick(t,true,true,true);if(action>=0)assertTrue(p.accept(epoch,epoch,s.nextSequence(),action,t));assertTrue(s.active());assertTrue(p.alive(t));assertEquals(1,p.gain(t),0);}
 }
 @Test public void mediaAloneAndPowerWithoutMediaNeverStartMotor(){
  OpticalMotorState s=new OpticalMotorState();for(int t=0;t<100;t++){assertEquals(-1,s.tick(t,true,false,true));assertFalse(s.active());}
  assertEquals(-1,s.tick(101,false,true,true));assertEquals(-1,s.tick(102,true,true,false));
 }
 @Test public void trayCloseWaitsFortyTicksThenStartsWithoutInventingARead(){
  OpticalMotorState s=new OpticalMotorState();assertEquals(2,s.tray(10));for(int t=11;t<50;t++)assertEquals(-1,s.tick(t,true,true,true));assertEquals(0,s.tick(50,true,true,true));
 }
 @Test public void lossOfLastPowerFadesOnceAndMissingMediaOrConnectionCancelsImmediately(){
  OpticalMotorState s=new OpticalMotorState();assertEquals(0,s.tick(0,true,true,true));assertEquals(1,s.tick(1,true,false,true));assertEquals(-1,s.tick(2,true,false,true));
  assertEquals(0,s.tick(3,true,true,true));assertEquals(2,s.tick(4,false,true,true));assertEquals(0,s.tick(5,true,true,true));assertEquals(2,s.tick(6,true,true,false));
 }
 @Test public void normalStopHasFiniteSyntheticPitchAndVolumeRampAndRepeatedStopDoesNotExtendIt(){
  OpticalMotorPlayback p=new OpticalMotorPlayback();assertTrue(p.accept(4,4,1,0,0));assertTrue(p.accept(4,4,2,1,3));
  assertEquals(1,p.gain(3),0);assertEquals(0.5,p.gain(8),0.0001);assertEquals(0.825,p.pitch(8),0.0001);assertTrue(p.accept(4,4,3,1,8));assertFalse(p.alive(13));assertEquals(0,p.gain(13),0);
 }
 @Test public void staleEpochSequenceAndMalformedStateNeverRefreshPlayback(){
  OpticalMotorPlayback p=new OpticalMotorPlayback();assertTrue(p.accept(7,7,2,0,0));assertFalse(p.accept(7,7,2,0,50));assertFalse(p.accept(7,6,99,0,50));assertFalse(p.accept(7,7,3,99,50));assertFalse(p.accept(0,0,3,0,50));assertFalse(p.alive(60));
 }
 @Test public void watchdogRequiresFreshAuthoritativeStateAndCancelHasNoFade(){
  OpticalMotorPlayback p=new OpticalMotorPlayback();p.accept(9,9,1,0,0);assertFalse(p.alive(60));assertTrue(p.accept(9,9,2,0,61));assertTrue(p.alive(61));p.accept(9,9,3,2,62);assertFalse(p.alive(62));assertFalse(p.accept(9,9,1,0,63));
 }
 @Test public void repeatedHeartbeatsAndEpochReplacementDoNotAcceptOldSession(){
  OpticalMotorPlayback p=new OpticalMotorPlayback();for(int i=0;i<10;i++)assertTrue(p.accept(10,10,i,0,i*20));assertTrue(p.accept(11,11,0,0,201));assertFalse(p.accept(11,10,999,0,202));
 }
 @Test public void sharedSlotsBoundEightDrivesAndTwoKindsNeverMultiplyDriveSlots(){
  OpticalMotorVoices b=new OpticalMotorVoices();for(int i=0;i<8;i++){assertTrue(b.admit(i));assertTrue(b.admit(i));}assertEquals(8,b.size());assertFalse(b.admit(8));b.release(3);assertTrue(b.admit(8));assertEquals(8,b.size());b.clear();assertEquals(0,b.size());
 }
 @Test public void readClickScheduleAndAssetEventRemainIndependentOfMotor(){
  OpticalSoundSchedule reads=new OpticalSoundSchedule();OpticalMotorState motor=new OpticalMotorState();assertEquals(0,motor.tick(0,true,true,true));reads.requestRead();assertEquals(2,reads.tick(0,true));assertEquals(3,reads.tick(13,true));assertTrue(motor.active());assertEquals("ocpp_cd_seek",OpticalSoundSchedule.EVENTS[2]);assertEquals(8,OpticalSoundSchedule.MAX_CLIENT_DRIVES);
 }
 @Test public void clockRollbackCancelsRatherThanLeakingPreviousControlState(){OpticalMotorState s=new OpticalMotorState();s.tick(100,true,true,true);assertEquals(2,s.tick(1,true,true,true));assertFalse(s.active());}
}
