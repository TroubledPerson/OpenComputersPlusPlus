package li.cil.oc.plusplus;
import org.junit.Test;import static org.junit.Assert.*;
public class OpticalAudioClockTest {
 @Test public void arbitraryOriginAndSubTickElapsedTime(){OpticalAudioClock c=new OpticalAudioClock();assertEquals(0,c.sample(-1000000,false));assertEquals(500,c.sample(-500000,false));assertEquals(1500,c.sample(500000,false));}
 @Test public void sampledPausesDoNotExpireTheWholeCue(){OpticalAudioClock c=new OpticalAudioClock();c.sample(0,false);assertEquals(20000,c.sample(20000000,false));assertEquals(20000,c.sample(30000000,true));assertEquals(20000,c.sample(900000000,true));assertEquals(20000,c.sample(910000000,false));assertEquals(30000,c.sample(920000000,false));}
 @Test public void multipleSameFrameQueriesNeverDoubleCount(){OpticalAudioClock c=new OpticalAudioClock();c.sample(0,false);for(int i=0;i<8;i++)assertEquals(16000,c.sample(16000000,false));}
 @Test public void backwardsSampleNeverMakesElapsedNegative(){OpticalAudioClock c=new OpticalAudioClock();c.sample(100000000,false);assertEquals(10000,c.sample(110000000,false));assertEquals(10000,c.sample(105000000,false));assertEquals(15000,c.sample(110000000,false));}
}
