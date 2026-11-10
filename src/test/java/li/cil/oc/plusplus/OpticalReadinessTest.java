package li.cil.oc.plusplus;
import org.junit.Test;import static org.junit.Assert.*;
public class OpticalReadinessTest {
 @Test public void exactServerBoundaryDoesNotDependOnClientAudio(){OpticalMediaReadiness r=new OpticalMediaReadiness();for(int t=40;t<91;t++)assertFalse(r.tick(t,1,true,true,true));assertTrue(r.tick(91,1,true,true,true));assertTrue(r.tick(10000,1,true,true,true));}
 @Test public void offEmptyDisconnectedAndStoppedCancelDeadline(){for(int mode=0;mode<4;mode++){OpticalMediaReadiness r=new OpticalMediaReadiness();assertFalse(r.tick(0,1,true,true,true));assertFalse(r.tick(50,1,mode!=0,mode!=1&&mode!=3,mode!=2));assertFalse(r.ready());assertFalse(r.tick(100,2,true,true,true));assertFalse(r.tick(150,2,true,true,true));assertTrue(r.tick(151,2,true,true,true));}}
 @Test public void replacementDoesNotInheritPreviousDeadline(){OpticalMediaReadiness r=new OpticalMediaReadiness();r.tick(0,1,true,true,true);assertFalse(r.tick(50,2,true,true,true));assertFalse(r.tick(51,2,true,true,true));assertTrue(r.tick(101,2,true,true,true));}
 @Test public void explicitRemovalAndReloadRequireFreshReadiness(){OpticalMediaReadiness r=new OpticalMediaReadiness();r.tick(0,1,true,true,true);assertTrue(r.tick(51,1,true,true,true));r.cancel();assertFalse(r.ready());assertFalse(r.tick(100,1,true,true,true));OpticalMediaReadiness loaded=new OpticalMediaReadiness();assertFalse(loaded.tick(151,1,true,true,true));assertFalse(loaded.ready());}
 @Test public void clockRollbackFailsClosed(){OpticalMediaReadiness r=new OpticalMediaReadiness();r.tick(100,1,true,true,true);assertFalse(r.tick(99,1,true,true,true));assertFalse(r.tick(150,1,true,true,true));assertTrue(r.tick(201,1,true,true,true));}
}
