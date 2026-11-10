package li.cil.oc.integration.opencomputers

import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.item.{Delegator, SoundCard}
import net.minecraft.item.ItemStack

object DriverSoundCard extends Item {
  override def worksWith(stack: ItemStack) = Delegator.subItem(stack).exists(_.isInstanceOf[SoundCard])
  override def slot(stack: ItemStack) = Slot.Card
  override def tier(stack: ItemStack) = Delegator.subItem(stack).collect {case card: SoundCard => card.tier}.getOrElse(0)
  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if(host.world == null || host.world.isRemote) null else new li.cil.oc.server.component.SoundCard(host,tier(stack))
}
