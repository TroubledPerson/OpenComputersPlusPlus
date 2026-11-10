package li.cil.oc.common.event

import li.cil.oc.Settings
import li.cil.oc.api.event.FileSystemAccessEvent
import li.cil.oc.api.internal.Rack
import li.cil.oc.common.tileentity.Case
import li.cil.oc.common.tileentity.DiskDrive
import li.cil.oc.common.tileentity.CDDrive
import li.cil.oc.common.tileentity.Raid
import li.cil.oc.server.component.DiskDriveMountable
import li.cil.oc.server.component.Server
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import net.minecraft.util.SoundEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object FileSystemAccessHandler {
  // Optical nonempty reads own a bounded activity channel. A legacy filesystem
  // event must never add a second floppy/HDD voice at that optical position.
  def isLegacyOpticalSound(tile: net.minecraft.tileentity.TileEntity, name: String): Boolean =
    tile.isInstanceOf[CDDrive] && (name == Settings.resourceDomain + ":floppy_access" || name == Settings.resourceDomain + ":hdd_access")

  @SubscribeEvent
  def onFileSystemAccess(e: FileSystemAccessEvent.Server) {
    if (isLegacyOpticalSound(e.getTileEntity, e.getSound)) {
      if (e.isCancelable) e.setCanceled(true)
      return // Not a read notification: metadata must not synthesize a whirr.
    }
    e.getTileEntity match {
      case t: Rack =>
        for (slot <- 0 until t.getSizeInventory) {
          t.getMountable(slot) match {
            case server: Server =>
              val containsNode = server.componentSlot(e.getNode.address) >= 0
              if (containsNode) {
                server.lastFileSystemAccess = System.currentTimeMillis()
                t.markChanged(slot)
              }
            case diskDrive: DiskDriveMountable =>
              val containsNode = diskDrive.filesystemNode.contains(e.getNode)
              if (containsNode) {
                diskDrive.lastAccess = System.currentTimeMillis()
                t.markChanged(slot)
              }
            case _ =>
          }
        }
      case _ =>
    }
  }

  @SubscribeEvent
  def onFileSystemAccess(e: FileSystemAccessEvent.Client) {
    if (isLegacyOpticalSound(e.getTileEntity, e.getSound)) {
      if (e.isCancelable) e.setCanceled(true)
      return
    }
    val volume = Settings.get.soundVolume
    val sound = new SoundEvent(new ResourceLocation(e.getSound))
    e.getWorld.playSound(e.getX, e.getY, e.getZ, sound, SoundCategory.BLOCKS, volume, 1, false)
    e.getTileEntity match {
      case t: DiskDrive => t.lastAccess = System.currentTimeMillis()
      case t: Case => t.lastFileSystemAccess = System.currentTimeMillis()
      case t: Raid => t.lastAccess = System.currentTimeMillis()
      case _ =>
    }
  }
}
