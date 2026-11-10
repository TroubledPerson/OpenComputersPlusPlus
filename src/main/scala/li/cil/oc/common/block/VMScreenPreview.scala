package li.cil.oc.common.block

import li.cil.oc.common.{GuiType, tileentity}
import net.minecraft.world.World
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.{EnumFacing,EnumHand}
import net.minecraft.util.math.BlockPos
import net.minecraft.block.state.IBlockState
import net.minecraft.world.IBlockAccess
import net.minecraftforge.common.property.{ExtendedBlockState,IExtendedBlockState}

/** Focus mirrors the projected BIOS; sneak-use touches its shared front canvas. */
class VMScreenPreview extends SimpleBlock with traits.GUI {
  override def createBlockState() = new ExtendedBlockState(this,Array.empty[net.minecraft.block.properties.IProperty[_]],Array(property.PropertyTile.Tile))
  override def getExtendedState(state: IBlockState,world: IBlockAccess,pos: BlockPos): IBlockState = (state,world.getTileEntity(pos)) match {
    case (extended: IExtendedBlockState,tile: tileentity.VMScreenPreview) => extended.withProperty(property.PropertyTile.Tile,tile)
    case _ => state
  }
  override def guiType = GuiType.VMScreenPreview
  override def createNewTileEntity(world: World, metadata: Int) = new tileentity.VMScreenPreview
  override def localOnBlockActivated(world: World,pos: BlockPos,player: EntityPlayer,hand: EnumHand,held: ItemStack,side: EnumFacing,x: Float,y: Float,z: Float): Boolean = {
    if(player.isSneaking&&side==EnumFacing.NORTH){
      if(!world.isRemote)world.getTileEntity(pos) match {case s: tileentity.VMScreenPreview => s.touch(player,side,x,y,z);case _ =>}
      true
    }else super.localOnBlockActivated(world,pos,player,hand,held,side,x,y,z)
  }
}
