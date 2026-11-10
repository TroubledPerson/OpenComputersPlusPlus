package li.cil.oc.plusplus;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;import org.junit.Test;
import static org.junit.Assert.*;import static li.cil.oc.plusplus.OpticalDrivePlayback.*;
/** Production policy and qualified native lifecycle bindings; no sound device or game is started. */
public class OpticalTrayPowerTest {
 static final class Log implements Sink {
  final EnumMap<Channel,Sound> live=new EnumMap<>(Channel.class);final EnumMap<Channel,Float> gains=new EnumMap<>(Channel.class);final List<String> events=new ArrayList<>();int maximum;
  public void play(Channel c,Sound s,float g,float p,boolean loop){assertFalse(live.containsKey(c));assertEquals(1f,p,0);assertEquals(s==Sound.LOOP,loop);live.put(c,s);gains.put(c,g);events.add("play:"+s);maximum=Math.max(maximum,live.size());}
  public void mix(Channel c,float g,float p){assertTrue(live.containsKey(c));assertEquals(1f,p,0);gains.put(c,g);}
  public void stop(Channel c,Reason r){assertNotNull(live.remove(c));gains.remove(c);events.add("stop:"+c+":"+r);}
  int count(String e){return Collections.frequency(events,e);}
 }
 static final class Drive {
  final OpticalDrivePlayback p=new OpticalDrivePlayback();final Log s=new Log();long seq,generation=1;
  void event(int a,long t){assertTrue(p.accept(7,7,generation,seq++,a,0,t,true,s));}
  void running(){event(POWER,0);for(long t=1;t<=STARTUP_TICKS;t++){if(t%20==0)event(POWER,t);p.tick(t,true,s);}assertEquals(Phase.RUNNING,p.phase());}
 }
 @Test public void onlyTrayGainChangesAndOtherRuntimeGainsRemainExact(){
  assertEquals(.90f,TRAY_GAIN,0);assertEquals(.3f,MOTOR_GAIN,0);assertEquals(.105f,DUCK_GAIN,0);
  Drive d=new Drive();d.event(OPEN,0);assertEquals(.90f,d.s.gains.get(Channel.TRANSIENT),0);d.event(CLOSE,1);assertEquals(.90f,d.s.gains.get(Channel.TRANSIENT),0);d.event(OPEN,2);assertEquals(.90f,d.s.gains.get(Channel.TRANSIENT),0);assertFalse(d.s.live.containsKey(Channel.MOTOR));
  Drive r=new Drive();r.running();r.event(SEEK,STARTUP_TICKS+1);assertEquals(.3f,r.s.gains.get(Channel.TRANSIENT),0);r.p.tick(STARTUP_TICKS+2,true,r.s);assertEquals(.105f,r.p.gain(),.0001f);
 }
 @Test public void forcedPowerLossWhileSpinningRoutesOneRecordedStop(){Drive d=new Drive();d.running();OpticalMotorState server=new OpticalMotorState();assertEquals(OpticalMotorState.ACTIVE,server.tick(0,true,true,true));int action=server.tick(52,true,false,true);assertEquals(OpticalMotorState.FADE,action);d.event(STOP,52);assertEquals(Sound.SPINDOWN,d.s.live.get(Channel.MOTOR));assertEquals(1,d.s.count("play:SPINDOWN"));assertEquals(-1,server.tick(53,true,false,true));assertEquals(-1,server.tick(54,true,false,true));}
 @Test public void uninitializedOrEmptyDriveDoesNotInventForcedStop(){OpticalMotorState server=new OpticalMotorState();assertEquals(-1,server.tick(0,true,false,true));assertEquals(-1,server.tick(1,false,false,true));Drive d=new Drive();d.event(STOP,0);d.event(STOP,1);assertEquals(0,d.s.count("play:SPINDOWN"));assertTrue(d.s.live.isEmpty());}
 @Test public void repeatedPowerOffDoesNotRestartOrExtendRecordedStop(){Drive d=new Drive();d.running();d.event(STOP,52);d.event(STOP,53);d.event(STOP,54);d.p.tick(52+STOP_TICKS,true,d.s);assertEquals(1,d.s.count("play:SPINDOWN"));assertEquals(Phase.OFF,d.p.phase());assertTrue(d.s.live.isEmpty());}
 @Test public void removalRetainsAnAlreadyStartedStopGainAndDeadline(){Drive d=new Drive();d.running();d.event(SEEK,52);d.p.tick(53,true,d.s);d.event(STOP,54);float gain=d.p.gain();d.event(EJECT,55);d.event(EJECT,56);assertEquals(gain,d.p.gain(),0);assertEquals(1,d.s.count("play:SPINDOWN"));assertEquals(1,d.s.count("play:OPEN"));assertEquals(0,d.s.count("stop:MOTOR:EJECT"));assertEquals(.90f,d.s.gains.get(Channel.TRANSIENT),0);d.p.tick(54+STOP_TICKS,true,d.s);assertEquals(Phase.OFF,d.p.phase());assertTrue(d.s.live.isEmpty());assertEquals(2,d.s.maximum);}
 @Test public void removalBeforePowerOffDoesNotRestartStop(){Drive d=new Drive();d.running();d.event(EJECT,52);d.event(STOP,53);d.p.tick(52+STOP_TICKS,true,d.s);assertEquals(1,d.s.count("play:SPINDOWN"));assertEquals(1,d.s.count("play:OPEN"));assertTrue(d.s.live.isEmpty());}
 @Test public void removalAfterCompletedStopOnlyAddsTrayOpen(){Drive d=new Drive();d.running();d.event(STOP,52);d.p.tick(52+STOP_TICKS,true,d.s);d.event(EJECT,99);assertEquals(1,d.s.count("play:SPINDOWN"));assertEquals(Sound.OPEN,d.s.live.get(Channel.TRANSIENT));assertFalse(d.s.live.containsKey(Channel.MOTOR));}
 @Test public void insertingNewMediaRearmsRemovalWithoutPoweringMotor(){Drive d=new Drive();d.event(OPEN,0);d.event(CLOSE,1);assertFalse(d.s.live.containsKey(Channel.MOTOR));d.event(OPEN,2);assertEquals(0,d.s.count("play:SPINDOWN"));assertEquals(2,d.s.count("play:OPEN"));}
 @Test public void newActivationRearmsOneStop(){Drive d=new Drive();d.running();d.event(STOP,52);d.p.tick(98,true,d.s);d.generation=2;d.event(POWER,100);for(long t=101;t<=151;t++){if(t%20==0)d.event(POWER,t);d.p.tick(t,true,d.s);}d.event(STOP,152);assertEquals(2,d.s.count("play:SPINDOWN"));}
 @Test public void teardownAfterStopCannotInventOrLeakAnotherCue(){for(Reason reason:new Reason[]{Reason.UNLOAD,Reason.DISCONNECT,Reason.RELOAD,Reason.MUTED}){Drive d=new Drive();d.running();d.event(STOP,52);d.p.reset(reason,53,d.s);d.event(STOP,54);assertEquals(1,d.s.count("play:SPINDOWN"));assertTrue(d.s.live.isEmpty());}}
 @Test public void forcedOffSignalAndNativeVolumeClampingBindingsRemainPreserved()throws Exception{
  File p=new File(System.getProperty("ocpp.sourceRoot","."));String machine="",packet="",client="",settings="";
  try(ZipFile z=new ZipFile(new File(p.getParentFile(),"deliverables/onejar-dev.31/OpenComputersPlusPlus-onejar-dev.31-source.zip"))){
   for(String path:new String[]{"src/main/scala/li/cil/oc/server/machine/Machine.scala","src/main/scala/li/cil/oc/server/PacketHandler.scala","src/main/scala/li/cil/oc/client/OpticalAudio.scala","src/main/scala/li/cil/oc/Settings.scala"}){
    byte[] now=Files.readAllBytes(new File(p,path).toPath());try(InputStream in=z.getInputStream(z.getEntry(path));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);assertArrayEquals(path,out.toByteArray(),now);}
    String text=new String(now,"UTF-8");if(path.endsWith("Machine.scala"))machine=text;if(path.endsWith("PacketHandler.scala"))packet=text;if(path.endsWith("OpticalAudio.scala"))client=text;if(path.endsWith("Settings.scala"))settings=text;
   }
  }
  assertTrue(packet.contains("else computer.stop()"));assertTrue(machine.contains("state.push(Machine.State.Stopping)"));assertTrue(machine.contains("state.top != Machine.State.Stopped && state.top != Machine.State.Stopping"));assertTrue(machine.contains("node.sendToReachable(\"computer.stopped\")"));assertTrue(client.contains("volume=(Settings.get.soundVolume*level) min 1f;pitch=frequency"));assertTrue(settings.contains("config.getDouble(\"client.soundVolume\").toFloat max 0 min 2"));
 }
}
