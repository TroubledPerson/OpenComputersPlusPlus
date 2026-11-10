package li.cil.oc.server.component

import java.util
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo.{DeviceAttribute, DeviceClass}
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Node
import li.cil.oc.plusplus.TierProfiles
import net.minecraft.nbt.NBTTagCompound
import scala.collection.convert.WrapAsJava._

/** One CPU slot, GPU primary node plus independently addressed full T3 data.
  * Direct ownership is necessary because component visibility is Neighbors.
  * Preserve the GPU's existing node tag; never substitute its saved address.
  */
class APUWithData(tier: Int) extends APU(tier) {
  require(tier >= TierProfiles.FIRST && tier <= TierProfiles.LAST)
  private val data = new DataCard.Tier3()

  override def onConnect(other: Node): Unit = {
    super.onConnect(other)
    if (other.host.isInstanceOf[Context] && other.isNeighborOf(node)) data.node.connect(other)
  }

  override def onDisconnect(other: Node): Unit = {
    super.onDisconnect(other)
    if (other == node) data.node.remove()
    else if (other.host.isInstanceOf[Context]) data.node.disconnect(other)
  }

  override def load(nbt: NBTTagCompound): Unit = {
    super.load(nbt)
    if (nbt.hasKey("ocppData")) data.load(nbt.getCompoundTag("ocppData"))
  }

  override def save(nbt: NBTTagCompound): Unit = {
    super.save(nbt)
    val child = new NBTTagCompound()
    data.save(child)
    nbt.setTag("ocppData", child)
  }

  private lazy val extendedDeviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Processor,
    DeviceAttribute.Description -> "APU (Builtin Graphics and Data T3)",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> ("FlexiArch " + TierProfiles.displayTier(tier) + " Processor (Builtin Graphics)"),
    DeviceAttribute.Capacity -> capacityInfo,
    DeviceAttribute.Width -> widthInfo,
    DeviceAttribute.Clock -> ((Settings.get.callBudgets(tier) * 1000).toInt.toString + "+" + clockInfo)
  )
  override def getDeviceInfo: util.Map[String, String] = extendedDeviceInfo
}
