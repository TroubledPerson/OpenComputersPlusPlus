package li.cil.oc.plusplus;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static li.cil.oc.plusplus.OpticalDrivePlayback.*;
/** Exercises the production coordinator with delayed native bookkeeping, never an audio device. */
public class OpticalStartupHandoffTest {
 static final class Log implements Sink {
  final EnumMap<Channel,Sound> live=new EnumMap<>(Channel.class);
  final List<String> events=new ArrayList<>();long time;int status=0;
  public long audioTimeMicros(){return time;}
  public int motorPlaying(){return status;}
  public void play(Channel c,Sound s,float g,float p,boolean loop){assertFalse(live.containsKey(c));assertEquals(s==Sound.LOOP,loop);assertEquals(1f,p,0);live.put(c,s);events.add("play:"+s);}
  public void mix(Channel c,float g,float p){assertTrue(live.containsKey(c));}
  public void stop(Channel c,Reason r){assertNotNull(live.remove(c));events.add("stop:"+c+":"+r);}
  int count(String e){return Collections.frequency(events,e);}
 }
 static final class Drive {
  final OpticalDrivePlayback p=new OpticalDrivePlayback();final Log s=new Log();long seq;
  boolean event(int action,long tick){return p.accept(7,7,1,seq++,action,0,tick,true,s);}
  void start(){assertTrue(event(POWER,0));}
  void observed(long time){s.time=time;s.status=1;p.audio(s);}
  void complete(){s.time+=startupMicros();s.status=0;p.audio(s);assertEquals(Phase.RUNNING,p.phase());}
 }
 @Test public void queuedNativeStartIsNotReplacedByClientTickCatchup(){Drive d=new Drive();d.start();d.s.time=1000000;d.event(POWER,STARTUP_TICKS);assertEquals(Phase.STARTUP,d.p.phase());assertEquals(0,d.s.count("play:LOOP"));d.observed(1200000);d.s.time+=startupMicros()-1;d.s.status=0;d.p.audio(d.s);assertEquals(Phase.STARTUP,d.p.phase());d.s.time++;d.p.audio(d.s);assertEquals(Phase.RUNNING,d.p.phase());}
 @Test public void trackedPlayingStartupIsNeverCutAtDurationFloor(){Drive d=new Drive();d.start();d.observed(100000);d.s.time+=startupMicros()+100000;d.p.audio(d.s);assertEquals(Phase.STARTUP,d.p.phase());assertEquals(0,d.s.count("stop:MOTOR:REPLACED"));d.s.status=0;d.p.audio(d.s);assertEquals(1,d.s.count("play:LOOP"));}
 @Test public void unknownNativeStatusUsesExplicitSoftwareDurationFallback(){Drive d=new Drive();d.s.status=-1;d.start();d.event(POWER,STARTUP_TICKS);assertEquals(Phase.STARTUP,d.p.phase());d.s.time=startupMicros()-1;d.p.audio(d.s);assertEquals(Phase.STARTUP,d.p.phase());d.s.time++;d.p.audio(d.s);assertEquals(Phase.RUNNING,d.p.phase());}
 @Test public void sampledPauseDoesNotConsumeStartupDuration(){Drive d=new Drive();OpticalAudioClock clock=new OpticalAudioClock();d.s.time=clock.sample(0,false);d.start();d.observed(clock.sample(100000000,false));d.s.time=clock.sample(200000000,true);d.p.audio(d.s);d.s.time=clock.sample(10000000000L,true);d.p.audio(d.s);d.s.time=clock.sample(10100000000L,false);d.p.audio(d.s);assertEquals(Phase.STARTUP,d.p.phase());assertEquals(0,d.s.count("play:LOOP"));}
 @Test public void heartbeatAndReadDoNotResetObservedStartupAndPendingReadRunsAfterLoop(){Drive d=new Drive();d.start();d.observed(100000);d.s.time=500000;d.event(POWER,20);d.event(SEEK,20);d.event(SEEK,21);assertEquals(1,d.s.count("play:SPINUP"));assertEquals(0,d.s.count("play:SEEK"));d.s.time=100000+startupMicros();d.s.status=0;d.p.audio(d.s);assertEquals(1,d.s.count("play:LOOP"));assertEquals(1,d.s.count("play:SEEK"));assertFalse(d.p.pendingRead());for(int i=0;i<20;i++)d.p.audio(d.s);assertEquals(1,d.s.count("play:LOOP"));}
 @Test public void neverStartedSourceFailsBoundedlyWithoutInventingLoop(){Drive d=new Drive();d.start();d.s.time=STARTUP_STATUS_WAIT_MICROS;d.p.audio(d.s);assertEquals(Phase.OFF,d.p.phase());assertEquals(1,d.s.count("stop:MOTOR:STARTUP_TIMEOUT"));assertEquals(0,d.s.count("play:LOOP"));assertTrue(d.s.live.isEmpty());}
 @Test public void stuckPlayingStatusFailsBoundedlyWithoutInventingLoop(){Drive d=new Drive();d.start();d.observed(100000);d.s.time=100000+startupMicros()+STARTUP_STATUS_WAIT_MICROS;d.p.audio(d.s);assertEquals(Phase.OFF,d.p.phase());assertEquals(1,d.s.count("stop:MOTOR:STARTUP_TIMEOUT"));assertEquals(0,d.s.count("play:LOOP"));}
 @Test public void actualRemovalDuringStartupPlaysRecordedStopAndOpenTogether(){Drive d=new Drive();d.start();d.event(SEEK,1);d.event(EJECT,2);assertEquals(Sound.SPINDOWN,d.s.live.get(Channel.MOTOR));assertEquals(Sound.OPEN,d.s.live.get(Channel.TRANSIENT));assertFalse(d.p.pendingRead());assertEquals(1,d.s.count("play:SPINDOWN"));assertEquals(1,d.s.count("play:OPEN"));}
 @Test public void duplicateRemovalEventsDoNotRestartEitherVoice(){Drive d=new Drive();d.start();d.observed(0);d.complete();d.event(EJECT,1);d.event(EJECT,2);assertEquals(1,d.s.count("play:SPINDOWN"));assertEquals(1,d.s.count("play:OPEN"));}
 @Test public void emptyTrayOpenDoesNotInventStopCue(){Drive d=new Drive();d.event(OPEN,0);assertEquals(0,d.s.count("play:SPINDOWN"));assertEquals(Sound.OPEN,d.s.live.get(Channel.TRANSIENT));}
 @Test public void closeRearmsOnlyTheNextActualRemoval(){Drive d=new Drive();d.event(OPEN,0);d.event(OPEN,1);d.event(CLOSE,2);assertEquals(Sound.CLOSE,d.s.live.get(Channel.TRANSIENT));d.event(OPEN,3);assertEquals(0,d.s.count("play:SPINDOWN"));assertEquals(2,d.s.count("play:OPEN"));}
 @Test public void cancellationDuringHandoffOrRemovalCannotLeakVoices(){for(Reason r:new Reason[]{Reason.UNLOAD,Reason.RELOAD,Reason.DISCONNECT,Reason.MUTED}){Drive d=new Drive();d.start();d.event(EJECT,1);d.p.reset(r,2,d.s);d.s.time=10000000;d.p.audio(d.s);assertTrue(d.s.live.isEmpty());assertEquals(0,d.s.count("play:LOOP"));}}
 @Test public void staleRemovalCannotCancelCurrentStartup(){Drive d=new Drive();d.start();assertFalse(d.p.accept(7,7,1,0,EJECT,0,1,true,d.s));assertFalse(d.p.accept(7,8,1,10,EJECT,0,1,true,d.s));assertEquals(Sound.SPINUP,d.s.live.get(Channel.MOTOR));}
 @Test public void normalStopDuringQueuedStartupReplacesItOnce(){Drive d=new Drive();d.start();d.event(STOP,1);d.event(STOP,2);d.s.time=10000000;d.p.audio(d.s);assertEquals(Phase.FADING,d.p.phase());assertEquals(1,d.s.count("play:SPINDOWN"));assertEquals(0,d.s.count("play:LOOP"));}
}
