package li.cil.oc.integration.opencomputers

import java.io.File
import java.util.UUID
import li.cil.oc.{Settings, api}
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.item.{Delegator, OpticalDisc}
import li.cil.oc.common.tileentity.CDDrive
import li.cil.oc.plusplus.OpticalFileSystem
import li.cil.oc.server.component
import li.cil.oc.server.fs.FileSystem.ReadOnlyLabel
import net.minecraft.item.ItemStack
import net.minecraftforge.common.DimensionManager

object DriverOpticalDisc extends Item {
  override def worksWith(stack: ItemStack) = Delegator.subItem(stack).exists(_.isInstanceOf[OpticalDisc])
  override def worksWith(stack: ItemStack, host: Class[_ <: EnvironmentHost]): Boolean =
    classOf[CDDrive].isAssignableFrom(host) && super.worksWith(stack, host)
  override def slot(stack: ItemStack) = "optical"
  override def createEnvironment(stack: ItemStack, host: EnvironmentHost): api.network.ManagedEnvironment = try {
    if (!host.isInstanceOf[CDDrive] || host.world == null || host.world.isRemote) null
    else Delegator.subItem(stack) match {
      case Some(disc: OpticalDisc) =>
        val tag = dataTag(stack)
        val fresh = !tag.hasKey("optical.id")
        val id = if (tag.hasKey("optical.id")) {
          val raw = tag.getString("optical.id")
          val parsed = UUID.fromString(raw).toString
          if (parsed != raw) throw new IllegalArgumentException("invalid optical identity")
          parsed
        } else {
          val generated = UUID.randomUUID().toString
          tag.setString("optical.id", generated)
          generated
        }
        val root = new File(DimensionManager.getCurrentSaveRootDirectory, Settings.savePath + "ocpp-optical/" + id)
        mountAt(stack, host, root, fresh)
      case _ => null
    }
  } catch {
    case scala.util.control.NonFatal(e) =>
      li.cil.oc.OpenComputers.log.warn("Optical medium could not mount; contents preserved: " + e.getMessage)
      null
  }

  // Package-scoped fixture seam; production always derives this root from the
  // world-owned optical UUID above. No path is exposed through component APIs.
  private[opencomputers] def mountAt(stack: ItemStack, host: EnvironmentHost, root: File, fresh: Boolean): component.OpticalFileSystem = {
        val disc = Delegator.subItem(stack).get.asInstanceOf[OpticalDisc]
        val tag = dataTag(stack)
        val id = if(!tag.hasKey("optical.id")) UUID.randomUUID().toString else tag.getString("optical.id")
        if(UUID.fromString(id).toString != id) throw new IllegalArgumentException("invalid optical identity")
        tag.setString("optical.id",id)
        val fs = new OpticalFileSystem(root.toPath, disc.rewritable, Settings.get.opticalCapacityBytes, Settings.get.fileCost)
        try {
          val master=disc.isInstanceOf[li.cil.oc.common.item.OpenOSOpticalDisc]
          val masterHash = if(master) {
            if(tag.hasKey("optical.master") && tag.getString("optical.master").matches("[a-f0-9]{64}") && fs.isReadOnly) tag.getString("optical.master")
            else if(fresh) li.cil.oc.plusplus.OpenOSMaster.seed(fs,Settings.get.fileCost,li.cil.oc.plusplus.OpenOSMaster.BUNDLED)
            else if(!tag.hasKey("optical.master") && fs.isReadOnly) li.cil.oc.plusplus.OpenOSMaster.verifySealed(fs,li.cil.oc.plusplus.OpenOSMaster.BUNDLED)
            else throw new java.io.IOException("Existing or interrupted CD will not be overwritten as an OpenOS master")
          } else null
          tag.setString("optical.id",id)
          if(masterHash != null) tag.setString("optical.master",masterHash)
          new component.OpticalFileSystem(fs, new ReadOnlyLabel(if(master) "OpenOS" else if (disc.rewritable) "CD-RW" else "CD-R"), host) {
          override def save(nbt: net.minecraft.nbt.NBTTagCompound): Unit = {
            super.save(nbt)
            nbt.setString("optical.id", id)
            // ComponentInventory deliberately clears the data tag before save.
            // Capture the verified value, rather than rereading that cleared tag.
            if(masterHash != null) nbt.setString("optical.master", masterHash)
          }
        }} catch { case scala.util.control.NonFatal(e) => fs.close(); throw e }
  }
}
