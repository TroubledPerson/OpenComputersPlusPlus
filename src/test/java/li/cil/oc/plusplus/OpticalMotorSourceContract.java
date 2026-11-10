package li.cil.oc.plusplus;
import java.io.*;import java.nio.file.*;import java.util.zip.*;import static org.junit.Assert.*;
/** Narrow approved motor delta; prior media methods and unrelated files stay exact. */
public final class OpticalMotorSourceContract {
 private static String read(InputStream in)throws Exception{try(InputStream s=in;ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=s.read(b))>=0)o.write(b,0,n);return new String(o.toByteArray(),"UTF-8").replace("\r\n","\n");}}
 private static String region(String text,String from,String to){int a=text.indexOf(from);assertTrue("missing preserved region "+from,a>=0);assertEquals(-1,text.indexOf(from,a+from.length()));int b=text.indexOf(to,a+from.length());assertTrue("region terminator "+to,b>a);return text.substring(a,b);}
 public static void assertDrive(File project,byte[] currentBytes)throws Exception{
  String current=new String(currentBytes,"UTF-8").replace("\r\n","\n"),old;
  try(ZipFile z=new ZipFile(new File(project.getParentFile(),"deliverables/onejar-dev.25/OpenComputersPlusPlus-onejar-dev.25-source.zip"))){old=read(z.getInputStream(z.getEntry("src/main/scala/li/cil/oc/common/tileentity/CDDrive.scala")));}
  for(String[] bounds:new String[][]{
   {"  def requestReadSound(): Unit = {","  protected def emitOpticalSound"},
   {"  protected def emitOpticalSound(action: Int): Unit =","  override def updateEntity"},
   {"  override def getDeviceInfo:","  override val node:"},
   {"  override val node:","  override protected def playInsertSound"},
   {"  @Callback(value = \"finalize\"","  @Callback(doc = \"function():boolean -- Returns whether inserted media is finalized"},
   {"  @Callback(doc = \"function():boolean -- Returns whether inserted media is finalized","\n}\n"}
  })assertEquals("unchanged read/media API "+bounds[0],region(old,bounds[0],bounds[1]),region(current,bounds[0],bounds[1]));
  assertTrue(current.contains("updateMotor(getWorld.getTotalWorldTime,present)"));assertTrue(current.contains("OpticalMotorOwners.powered(node,filesystemNode,getWorld)"));
  assertTrue(current.contains("machine.isRunning"));assertTrue(current.contains("c.canBeSeenFrom(n)"));assertTrue(current.contains("OWNER_SCAN_LIMIT"));assertTrue(current.contains("if(nodes.hasNext)false else running"));
  assertTrue(current.contains("if(message.name==\"computer.started\"||message.name==\"computer.stopped\")rescan.set(true)"));
  assertTrue(current.contains("if(other==node&&getWorld!=null&&isServer){readiness.cancel();emitMotor(motor.cancel());syncMediaPublication(false)}"));
  assertFalse(current.contains("topologyLost"));
  assertTrue(current.contains("syncMediaPublication(readiness.tick"));
  assertTrue(current.contains("fs.node.setVisibility(Visibility.None)"));
  assertTrue(current.contains("fs.optical.close()"));
  assertTrue(current.contains("if(spinning)OpticalDrivePlayback.EJECT else OpticalDrivePlayback.OPEN"));
  assertTrue(current.contains("if(getWorld!=null&&isServer)emitMotor(motor.cancel())"));assertTrue(current.contains("motor.tray(getWorld.getTotalWorldTime)"));
  String client=read(new FileInputStream(new File(project,"src/main/scala/li/cil/oc/client/OpticalAudio.scala")));
  assertTrue(client.contains("repeat=loop"));assertTrue(client.contains("drive.opticalPlayback.accept(drive.motorEpoch,epoch,generation,sequence,action,age,tick,audible,sink)"));assertTrue(client.contains("voices.admit(pos)"));
  assertTrue(client.contains("extends Playback.Sink"));assertTrue(client.contains("entries.keys.toList.foreach(p=>remove(p,reason));voices.clear()"));
  assertTrue(client.contains("onWorldUnload"));assertTrue(client.contains("onSoundReload"));assertTrue(client.contains("onChunkUnload"));
  assertTrue(client.contains("Playback.Reason.DISCONNECT"));assertTrue(client.contains("Playback.Reason.MUTED"));assertTrue(client.contains("repeat=loop"));
  assertTrue(current.contains("val event=OpticalDrivePlayback.legacyEvent(action)"));assertTrue(current.contains("motor.active()"));
  assertTrue(client.contains("def receive(pos:BlockPos,action:Int):Unit=Diag.record(Diag.PacketRejected)"));
 }
}
