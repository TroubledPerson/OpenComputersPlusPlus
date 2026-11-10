package li.cil.oc.common.item

import java.util
import li.cil.oc.Settings
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraft.client.util.ITooltipFlag

/** Separate media type: never inherits the floppy/raw-drive configuration GUI. */
class OpticalDisc(val parent: Delegator, val rewritable: Boolean) extends traits.Delegate {
  override def unlocalizedName = if (rewritable) "ocpp_cdrw" else "ocpp_cdr"
  override def maxStackSize = 1
  override protected def tooltipName = None
  override def tooltipLines(stack: ItemStack, world: World, lines: util.List[String], flag: ITooltipFlag): Unit = {
    lines.add("32 MiB default virtual optical media; CD drive required")
    lines.add(if (rewritable) "Rewritable; no finalization" else "Writable until explicit drive.finalize(); then read-only")
    super.tooltipLines(stack, world, lines, flag)
  }
}
