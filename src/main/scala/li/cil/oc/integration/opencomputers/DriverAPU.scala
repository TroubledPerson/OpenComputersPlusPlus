package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common
import li.cil.oc.common.Tier
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object DriverAPU extends DriverCPU with HostAware {
  override def worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.APUTier1),
    api.Items.get(Constants.ItemName.APUTier2),
    api.Items.get(Constants.ItemName.APUCreative),
    api.Items.get("ocpp_apu4"),
    api.Items.get("ocpp_apu5"),
    api.Items.get("ocpp_apu6"))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else gpuTier(stack) match {
      case Tier.One => new component.APU(Tier.One)
      case Tier.Two => new component.APU(Tier.Two)
      case Tier.Three => new component.APU(Tier.Three)
      case tier if tier >= 4 && tier <= 6 => new component.APUWithData(tier)
      case _ => null
    }

  override def cpuTier(stack: ItemStack) =
    Delegator.subItem(stack) match {
      case Some(apu: common.item.APU) => apu.cpuTier
      case _ => Tier.One
    }

  def gpuTier(stack: ItemStack) =
    Delegator.subItem(stack) match {
      case Some(apu: common.item.APU) => apu.gpuTier
      case _ => Tier.One
    }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (worksWith(stack))
        classOf[component.GraphicsCard]
      else null
  }

}
