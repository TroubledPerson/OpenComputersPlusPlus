package li.cil.oc.common.container

import li.cil.oc.common.Slot
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.inventory.IInventory

class DiskDrive(playerInventory: InventoryPlayer, drive: IInventory) extends Player(playerInventory, drive) {
  // Both GUI handlers already pass the real tile inventory. Keep the floppy
  // ghost/filter presentation for ordinary drives; CDs get a neutral optical
  // slot backed by the CD tile's existing strict media validation.
  private val mediaSlot = if (drive.isInstanceOf[li.cil.oc.common.tileentity.CDDrive]) "optical" else Slot.Floppy
  addSlotToContainer(80, 35, mediaSlot)
  addPlayerInventorySlots(8, 84)
}
