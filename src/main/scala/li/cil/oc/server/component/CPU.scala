package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.api.prefab.AbstractManagedEnvironment

import scala.collection.convert.WrapAsJava._

class CPU(val tier: Int) extends AbstractManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Neighbors).
    create()

  override def getDeviceInfo: util.Map[String, String] = CPU.deviceInfo(tier)
}

object CPU {
  def create(tier: Int): AbstractManagedEnvironment =
    if (tier >= li.cil.oc.plusplus.TierProfiles.FIRST && tier <= li.cil.oc.plusplus.TierProfiles.LAST) new CPUWithData(tier)
    else new CPU(tier)

  def deviceInfo(tier: Int): util.Map[String, String] = Map(
    DeviceAttribute.Class -> DeviceClass.Processor,
    DeviceAttribute.Description -> "CPU",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> ("FlexiArch " + li.cil.oc.plusplus.TierProfiles.displayTier(tier).toString + " Processor"),
    DeviceAttribute.Clock -> (Settings.get.callBudgets(tier) * 1000).toInt.toString
  )

}

/** One physical CPU slot and one data component, using the CPU's existing node tag.
  * DataCard.Tier3 inherits T1/T2 callbacks, limits and power accounting unchanged.
  * Plain saved CPU node addresses load into this component without replacement.
  */
class CPUWithData(val tier: Int) extends DataCard.Tier3 {
  require(tier >= li.cil.oc.plusplus.TierProfiles.FIRST && tier <= li.cil.oc.plusplus.TierProfiles.LAST)
  override def getDeviceInfo: util.Map[String, String] = CPU.deviceInfo(tier)
}
