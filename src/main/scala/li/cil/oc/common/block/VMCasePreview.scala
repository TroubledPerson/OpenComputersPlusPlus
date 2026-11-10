package li.cil.oc.common.block

import li.cil.oc.common.{GuiType, tileentity}
import net.minecraft.world.World

/** Deliberately separate registry identity: never a functioning Machine. */
class VMCasePreview extends SimpleBlock with traits.GUI {
  override def guiType = GuiType.VMCasePreview
  override def createNewTileEntity(world: World, metadata: Int) = new tileentity.VMCasePreview
}
