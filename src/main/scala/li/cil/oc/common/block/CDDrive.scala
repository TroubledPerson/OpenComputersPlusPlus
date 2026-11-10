package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.{EnumFacing, EnumHand}
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/** Separate optical drive. Normal use opens the backed media slot; sneak-use swaps. */
class CDDrive extends DiskDrive {
  override def createNewTileEntity(world: World, metadata: Int) = new tileentity.CDDrive()
  override def localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, held: ItemStack,
                                    side: EnumFacing, x: Float, y: Float, z: Float): Boolean = {
    world.getTileEntity(pos) match {
      case drive: tileentity.CDDrive if player.isSneaking =>
        val inserted = !drive.getStackInSlot(0).isEmpty
        val valid = !held.isEmpty && drive.isItemValidForSlot(0, held)
        if (!world.isRemote) {
          if (inserted) drive.dropSlot(0, 1, Option(drive.facing))
          if (valid) {
            val single = held.copy(); single.setCount(1)
            drive.setInventorySlotContents(0, single)
            held.shrink(1)
          }
        }
        inserted || valid
      case _: tileentity.CDDrive if !player.isSneaking =>
        super.localOnBlockActivated(world, pos, player, hand, held, side, x, y, z)
      case _ => false
    }
  }
}
