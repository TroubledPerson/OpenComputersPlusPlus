package li.cil.oc.common.item

import java.util
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraft.client.util.ITooltipFlag

/** Factory master for normal OC BIOS/OpenOS, unrelated to Tier7 SuperBIOS. */
class OpenOSOpticalDisc(parent: Delegator) extends OpticalDisc(parent,false) {
  override def unlocalizedName = "ocpp_openos_cdr"
  override def tooltipLines(stack: ItemStack,world: World,lines: util.List[String],flag: ITooltipFlag): Unit = {
    lines.add("OpenOS boot/installer CD for ordinary OC computers")
    lines.add("Factory finalized; not a Tier 7 / QEMU boot image")
  }
}
