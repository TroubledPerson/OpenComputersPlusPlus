package li.cil.oc.server.component

import li.cil.oc.api.fs.Label
import li.cil.oc.api.network.{EnvironmentHost, Node}
import li.cil.oc.plusplus.{OpticalFileSystem => Media}
import li.cil.oc.api.machine.{Arguments, Callback, Context}

class OpticalFileSystem(val optical: Media, label: Label, host: EnvironmentHost)
  extends FileSystem(optical, label, Option(host), None, 1) {
  @Callback(direct = true, doc = "function(handle:userdata,whence:string,offset:number):number -- Seek virtual CD data; a successful authorized seek requests bounded drive activity audio.")
  override def seek(context: Context, args: Arguments): Array[AnyRef] = {
    val value = super.seek(context, args) // Validation/ownership/position first.
    li.cil.oc.plusplus.OpticalAudioDiagnostics.record(li.cil.oc.plusplus.OpticalAudioDiagnostics.Seek)
    host match {
      case drive: li.cil.oc.common.tileentity.CDDrive => drive.requestReadSound()
      case _ =>
    }
    value
  }
  @Callback(direct = true, limit = 15, doc = "function(handle:userdata,count:number):string or nil -- Read virtual CD data; successful nonempty reads request bounded drive activity audio.")
  override def read(context: Context, args: Arguments): Array[AnyRef] = {
    val value = super.read(context, args)
    if (value.nonEmpty && value(0).isInstanceOf[Array[Byte]] && value(0).asInstanceOf[Array[Byte]].nonEmpty) {
      li.cil.oc.plusplus.OpticalAudioDiagnostics.record(li.cil.oc.plusplus.OpticalAudioDiagnostics.Read)
      host match {
      case drive: li.cil.oc.common.tileentity.CDDrive => drive.requestReadSound()
      case _ =>
      }
    }
    value
  }
  @Callback(direct = true, doc = "function(handle:userdata,value:string):boolean -- Successful nonempty optical writes request bounded activity audio.")
  override def write(context: Context, args: Arguments): Array[AnyRef] = {
    val value = super.write(context, args) // Actual ownership, energy, readonly and write result first.
    if (value.nonEmpty && value(0) == java.lang.Boolean.TRUE && args.checkByteArray(1).nonEmpty) host match {
      case drive: li.cil.oc.common.tileentity.CDDrive => drive.requestReadSound()
      case _ =>
    }
    value
  }
  override def load(nbt: net.minecraft.nbt.NBTTagCompound): Unit = {
    val clean = nbt.copy()
    clean.removeTag("owners") // Disc remount/load invalidates all outstanding handles.
    super.load(clean)
  }
  override def onConnect(node: Node): Unit = {
    if (node == this.node) optical.reopen()
    super.onConnect(node)
  }
}
