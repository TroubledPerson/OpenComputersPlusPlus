package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object DriverNetworkCard extends Item with HostAware {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.NetworkCard), api.Items.get("ocpp_network2"), api.Items.get("ocpp_network3"))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else if (tier(stack) > 0) new component.AdvancedNetworkCard(host, tier(stack))
    else new component.NetworkCard(host)

  override def tier(stack: ItemStack): Int = li.cil.oc.common.item.Delegator.subItem(stack) match {
    case Some(card: li.cil.oc.common.item.AdvancedNetworkCard) => card.networkTier
    case _ => 0
  }

  override def slot(stack: ItemStack) = Slot.Card

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (worksWith(stack))
        classOf[component.NetworkCard]
      else null
  }

}
