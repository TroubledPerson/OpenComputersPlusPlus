package li.cil.oc.plusplus;

import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Test-only, exact approved deltas; every unrelated byte remains compared. */
public final class ApprovedDiagnosticSource {
 private ApprovedDiagnosticSource(){}
 private static String replaceOne(String text,String from,String to){
  String eol=text.contains("\r\n")?"\r\n":"\n";
  from=from.replace("\n",eol);to=to.replace("\n",eol);
  int first=text.indexOf(from);
  assertTrue("Required authorized delta missing",first>=0);
  assertEquals("Authorized delta must occur exactly once",-1,text.indexOf(from,first+from.length()));
  return text.substring(0,first)+to+text.substring(first+from.length());
 }
 public static byte[] normalize(String path,byte[] bytes){
  path=path.replace('\\','/');if(path.startsWith("src/main/"))path=path.substring(9);
  String s=new String(bytes,StandardCharsets.UTF_8),d="li.cil.oc.plusplus.OpticalAudioDiagnostics";
  if(path.equals("scala/li/cil/oc/Settings.scala")){
   s=replaceOne(s,"  val opticalAudioDiagnostics = config.getBoolean(\"plusplus.opticalAudioDiagnostics\")\n","");
  }else if(path.equals("scala/li/cil/oc/client/renderer/tileentity/VMCasePreviewRenderer.scala")){
   s=replaceOne(s,"/** Native BIOS-on/error overlays. BIOS power is not a running guest or disk activity. */","/** Native red error-light overlay on the preview's fixed north front. */");
   s=replaceOne(s,"    val on=preview.biosOn\n    if (!on && (!preview.failedStartActive || !RenderUtil.shouldShowErrorLight(preview.hashCode))) return","    if (!preview.failedStartActive || !RenderUtil.shouldShowErrorLight(preview.hashCode)) return");
   s=replaceOne(s,"val icon=Textures.getSprite(if(on)Textures.Block.CaseFrontOn else Textures.Block.CaseFrontError)","val icon=Textures.getSprite(Textures.Block.CaseFrontError)");
  }else if(path.equals("scala/li/cil/oc/common/tileentity/CDDrive.scala")){
   s=replaceOne(s,"  def requestReadSound(): Unit = {\n    "+d+".record("+d+".Queued)\n    soundSchedule.requestRead()\n  }","  def requestReadSound(): Unit = soundSchedule.requestRead()");
   s=replaceOne(s,"      val present=filesystemNode.nonEmpty\n      if(!present&&soundSchedule.hasPendingRead())"+d+".record("+d+".NoMedia)\n      val action = soundSchedule.tick(getWorld.getTotalWorldTime, present)","      val action = soundSchedule.tick(getWorld.getTotalWorldTime, filesystemNode.nonEmpty)");
  }else if(path.equals("scala/li/cil/oc/server/component/OpticalFileSystem.scala")){
   // Remove only the exact approved, independently behavior-tested write hook.
   s=replaceOne(s,"  @Callback(direct = true, doc = \"function(handle:userdata,value:string):boolean -- Successful nonempty optical writes request bounded activity audio.\")\n  override def write(context: Context, args: Arguments): Array[AnyRef] = {\n    val value = super.write(context, args) // Actual ownership, energy, readonly and write result first.\n    if (value.nonEmpty && value(0) == java.lang.Boolean.TRUE && args.checkByteArray(1).nonEmpty) host match {\n      case drive: li.cil.oc.common.tileentity.CDDrive => drive.requestReadSound()\n      case _ =>\n    }\n    value\n  }\n","");
   s=replaceOne(s,"    "+d+".record("+d+".Seek)\n","");
   String gate="    if (value.nonEmpty && value(0).isInstanceOf[Array[Byte]] && value(0).asInstanceOf[Array[Byte]].nonEmpty)";
   String cases="      case drive: li.cil.oc.common.tileentity.CDDrive => drive.requestReadSound()\n      case _ =>\n";
   s=replaceOne(s,gate+" {\n      "+d+".record("+d+".Read)\n      host match {\n"+cases+"      }\n    }",gate+" host match {\n"+cases+"    }");
  }else if(path.equals("java/li/cil/oc/plusplus/SoundSprites.java")){
   return bytes; // No approved changes at all to speaker artwork/generation.
  }else throw new AssertionError("No approved source delta for "+path);
  return s.getBytes(StandardCharsets.UTF_8);
 }
}