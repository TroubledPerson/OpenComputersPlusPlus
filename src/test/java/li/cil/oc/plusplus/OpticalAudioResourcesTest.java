package li.cil.oc.plusplus;
import com.google.gson.*;import java.io.*;import java.nio.file.*;import java.security.MessageDigest;import java.util.*;import java.util.zip.*;import org.junit.Test;import static org.junit.Assert.*;
public class OpticalAudioResourcesTest {
 private File project(){return new File(System.getProperty("ocpp.sourceRoot","."));}
 private JsonObject json(File p)throws Exception{try(Reader r=new InputStreamReader(new FileInputStream(p),"UTF-8")){return new JsonParser().parse(r).getAsJsonObject();}}
 private String hash(byte[] b)throws Exception{StringBuilder s=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format("%02x",v&255));return s.toString();}
 @Test public void cachesSevenOpticalEventsAndPreservesOrdinaryMappings()throws Exception{
  JsonObject current=json(new File(project(),"src/main/resources/assets/opencomputers/sounds.json")),old;
  try(ZipFile z=new ZipFile(new File(project().getParentFile(),"deliverables/onejar-dev.10/OpenComputersPlusPlus-onejar-dev.10-source.zip"));Reader r=new InputStreamReader(z.getInputStream(z.getEntry("src/main/resources/assets/opencomputers/sounds.json")),"UTF-8")){old=new JsonParser().parse(r).getAsJsonObject();}
  assertEquals(old.entrySet().size()+7,current.entrySet().size());for(Map.Entry<String,JsonElement> e:old.entrySet())assertEquals(e.getValue(),current.get(e.getKey()));
  for(String suffix:new String[]{"open","close","read","motor","spinup","seek","stop"}){JsonArray sounds=current.getAsJsonObject("ocpp_cd_"+suffix).getAsJsonArray("sounds");assertEquals(1,sounds.size());JsonObject sound=sounds.get(0).getAsJsonObject();assertEquals(suffix.equals("read"),sound.get("stream").getAsBoolean());assertEquals("opencomputers:ocpp/cd_"+suffix,sound.get("name").getAsString());}
 }
 @Test public void fourAcceptedSourcesHaveQualifiedEncodedBindingsAndDurationLeases()throws Exception{
  File dir=new File(project(),"src/main/resources/assets/opencomputers/sounds/ocpp");JsonObject p=json(new File(dir,"cd-718390-provenance.json"));assertEquals(718390,p.get("sourceId").getAsInt());assertEquals("CC BY 4.0",p.get("license").getAsString());assertTrue(p.get("author").getAsString().contains("Marcus Dellicompagni"));assertFalse(p.get("modelAudibleInspection").getAsBoolean());assertTrue(p.get("ownerAuditionRequired").getAsBoolean());
  String[] inputs={"35f81c1e319e4d2277bbb6978740e6a2c3151267c6e3db420564983f61d07a0a","8ce82b76a2247296894c8f5c6c80fcc025771bcf1c7b26155e837ecd8ea3f925","f1ff9d8903c12e0e5a9b094f07522f8f41c4f8ea270d1462428d664fa8215f12","8efb6afc6be83e05babe230597c670e3979da0fd54df14e4d5b17e68050964b5"};int i=0;
  for(JsonElement e:p.getAsJsonArray("assets")){JsonObject a=e.getAsJsonObject();assertEquals(inputs[i++],a.get("inputSha256").getAsString());byte[] b=Files.readAllBytes(new File(dir,a.get("file").getAsString()).toPath());assertTrue(b.length<128*1024);assertEquals("OggS",new String(b,0,4,"US-ASCII"));assertEquals(a.get("sha256").getAsString(),hash(b));assertEquals(44100,a.get("sampleRate").getAsInt());assertEquals(1,a.get("channels").getAsInt());assertTrue(a.get("decodedPeakDb").getAsDouble()<0);assertEquals((int)Math.ceil(a.get("decodedFrames").getAsDouble()*20/44100),a.get("ticks").getAsInt());}
  assertEquals(4,i);JsonArray a=p.getAsJsonArray("assets");assertEquals(OpticalDrivePlayback.STARTUP_FRAMES,a.get(0).getAsJsonObject().get("decodedFrames").getAsInt());assertEquals(OpticalDrivePlayback.SEEK_FRAMES,a.get(2).getAsJsonObject().get("decodedFrames").getAsInt());assertEquals(OpticalDrivePlayback.STOP_FRAMES,a.get(3).getAsJsonObject().get("decodedFrames").getAsInt());
 }
 @Test public void trayReadReferencesAndUnrelatedDrivePipelineRemainExact()throws Exception{
  try(ZipFile z=new ZipFile(new File(project().getParentFile(),"deliverables/onejar-dev.28/OpenComputersPlusPlus-onejar-dev.28-source.zip"))){for(String path:new String[]{"src/main/resources/assets/opencomputers/sounds/ocpp/cd_open.ogg","src/main/resources/assets/opencomputers/sounds/ocpp/cd_close.ogg","src/main/resources/assets/opencomputers/sounds/ocpp/cd_read.ogg","src/main/java/li/cil/oc/plusplus/OpticalMotorState.java"}){try(InputStream in=z.getInputStream(z.getEntry(path));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);assertArrayEquals(path,out.toByteArray(),Files.readAllBytes(new File(project(),path).toPath()));}}}
 }
 @Test public void removalProtocolChangesOnlyQualifiedMediaRemovalAndPacketBound()throws Exception{
  try(ZipFile z=new ZipFile(new File(project().getParentFile(),"deliverables/onejar-dev.30/OpenComputersPlusPlus-onejar-dev.30-source.zip"))){
   for(String path:new String[]{"src/main/scala/li/cil/oc/common/tileentity/CDDrive.scala","src/main/scala/li/cil/oc/server/OpticalSounds.scala","src/main/scala/li/cil/oc/client/PacketHandler.scala"}){
    byte[] before;try(InputStream in=z.getInputStream(z.getEntry(path));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);before=out.toByteArray();}
    String old=new String(before,"UTF-8").replace("\r\n","\n"),now=new String(Files.readAllBytes(new File(project(),path).toPath()),"UTF-8").replace("\r\n","\n");
    if(path.endsWith("CDDrive.scala")){
     OpticalMotorSourceContract.assertDrive(project(),now.getBytes("UTF-8"));
     assertTrue(now.contains("val spinning=motor.active();motor.cancel()"));
     assertTrue(now.contains("sendPlayback(if(spinning)OpticalDrivePlayback.EJECT else OpticalDrivePlayback.OPEN)"));
    }else if(path.endsWith("PacketHandler.scala")){assertTrue(now.contains("action<=li.cil.oc.plusplus.OpticalDrivePlayback.EJECT"));assertEquals(old.replace("action<=5&&age","action<=li.cil.oc.plusplus.OpticalDrivePlayback.EJECT&&age"),now); }else{assertTrue(now.contains("action<=li.cil.oc.plusplus.OpticalDrivePlayback.EJECT"));assertEquals(old.replace("action<=li.cil.oc.plusplus.OpticalDrivePlayback.SEEK","action<=li.cil.oc.plusplus.OpticalDrivePlayback.EJECT"),now);}
   }
  }
 }
 @Test public void selectedBIsStereoSourceAndBoundedMonoRuntimeCue()throws Exception{
  JsonObject m=json(new File(project(),"src/main/resources/assets/opencomputers/sounds/ocpp/cd-718390-provenance.json"));JsonObject b=m.getAsJsonArray("assets").get(2).getAsJsonObject();
  assertEquals("B",b.get("ownerSelectedVariant").getAsString());assertEquals(96000,b.get("inputRate").getAsInt());assertEquals(2,b.get("inputChannels").getAsInt());assertEquals(2,b.get("inputSampleWidth").getAsInt());assertEquals(11040,b.get("inputFrames").getAsInt());assertEquals(.115,b.get("inputDurationSeconds").getAsDouble(),.000001);assertEquals(1,b.get("channels").getAsInt());assertEquals(44100,b.get("sampleRate").getAsInt());assertEquals(5072,b.get("containerFrames").getAsInt());assertEquals(.115,b.get("containerDurationSeconds").getAsDouble(),2.0/44100);assertEquals(5440,b.get("decodedFrames").getAsInt());assertEquals(368,b.get("gameDecoderPaddingFrames").getAsInt());assertEquals(4944,b.get("ffmpegDecodedFrames").getAsInt());assertEquals(5440.0/44100,b.get("decodedSeconds").getAsDouble(),.000001);assertTrue(b.get("decodedPeakDb").getAsDouble()<0);
 }
 @Test public void selectedTrayProvenanceRemainsHonestAndSeparate()throws Exception{
  File dir=new File(project(),"src/main/resources/assets/opencomputers/sounds/ocpp");JsonObject p=json(new File(dir,"cd-effects-provenance.json"));assertEquals("first opens, second closes",p.get("ownerConfirmedTrayOrder").getAsString());assertEquals(3,p.getAsJsonArray("assets").size());
  for(JsonElement e:p.getAsJsonArray("assets")){JsonObject a=e.getAsJsonObject();assertEquals(a.get("sha256").getAsString(),hash(Files.readAllBytes(new File(dir,a.get("file").getAsString()).toPath())));}
  String notice=new String(Files.readAllBytes(new File(project(),"src/main/resources/META-INF/ocpp-audio/CD-718390-NOTICES.txt").toPath()),"UTF-8");assertTrue(notice.contains("Marcus Dellicompagni"));assertTrue(notice.contains("https://creativecommons.org/licenses/by/4.0/"));assertTrue(notice.contains("0.191333"));assertTrue(notice.contains("separate from the mod code MIT"));
 }
 @Test public void nativeAdapterBindsRecordedStopAndRetainsCachedMixing()throws Exception{
  String client=new String(Files.readAllBytes(new File(project(),"src/main/scala/li/cil/oc/client/OpticalAudio.scala").toPath()),"UTF-8");assertTrue(client.contains("Playback.Sound.SPINDOWN=>\"ocpp_cd_stop\""));assertTrue(client.contains("repeat=loop"));assertTrue(client.contains("onSoundReload"));assertTrue(client.contains("RenderTickEvent"));assertTrue(client.contains("entry.playback.audio(entry)"));assertTrue(client.contains("audioClock.sample(System.nanoTime()"));assertTrue(client.contains("voices.admit(pos)"));
 }
}
