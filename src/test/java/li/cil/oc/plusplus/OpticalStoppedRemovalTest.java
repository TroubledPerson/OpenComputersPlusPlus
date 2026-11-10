package li.cil.oc.plusplus;
import org.junit.Test;import static org.junit.Assert.*;import static li.cil.oc.plusplus.OpticalDrivePlayback.*;
public class OpticalStoppedRemovalTest {
 @Test public void authoritativeIdleRemovalNeverInventsStop(){for(int state=0;state<3;state++){OpticalTrayPowerTest.Drive d=new OpticalTrayPowerTest.Drive();if(state>0){d.running();d.event(CANCEL,52);}d.event(OPEN,60);d.event(OPEN,61);assertEquals(0,d.s.count("play:SPINDOWN"));assertEquals(1,d.s.count("play:OPEN"));}}
 @Test public void idleRemovalRetainsExistingStopGainAndDeadline(){OpticalTrayPowerTest.Drive d=new OpticalTrayPowerTest.Drive();d.running();d.event(SEEK,52);d.p.tick(53,true,d.s);d.event(STOP,54);float g=d.p.gain();d.event(OPEN,55);d.event(OPEN,56);assertEquals(g,d.p.gain(),0);assertEquals(1,d.s.count("play:SPINDOWN"));assertEquals(1,d.s.count("play:OPEN"));d.p.tick(54+STOP_TICKS,true,d.s);assertEquals(Phase.OFF,d.p.phase());}
 @Test public void authoritativeSpinningRemovalWorksForFreshObserver(){OpticalTrayPowerTest.Drive d=new OpticalTrayPowerTest.Drive();d.event(EJECT,0);d.event(STOP,1);d.event(OPEN,2);assertEquals(1,d.s.count("play:SPINDOWN"));assertEquals(1,d.s.count("play:OPEN"));}
}
