package li.cil.oc.server.component

import li.cil.oc.Settings
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{EnvironmentHost, Node}
import net.minecraft.nbt.NBTTagCompound

/** One physical slot; two independently addressed, directly owned components. */
class AdvancedNetworkCard(host: EnvironmentHost, val cardTier: Int) extends NetworkCard(host) {
  require(cardTier == 1 || cardTier == 2)
  private val internet = new RemoteInternetCard()
  override def onConnect(other: Node): Unit = {
    super.onConnect(other)
    if (other.host.isInstanceOf[Context] && other.isNeighborOf(node)) internet.node.connect(other)
  }
  override def onDisconnect(other: Node): Unit = {
    super.onDisconnect(other)
    if (other == node) internet.node.remove()
    else if (other.host.isInstanceOf[Context]) internet.node.disconnect(other)
  }
  override def load(nbt: NBTTagCompound): Unit = {
    super.load(nbt)
    if (nbt.hasKey("ocppInternet")) internet.load(nbt.getCompoundTag("ocppInternet"))
  }
  override def save(nbt: NBTTagCompound): Unit = {
    super.save(nbt)
    val child = new NBTTagCompound(); internet.save(child); nbt.setTag("ocppInternet", child)
  }
  @Callback(direct = true, doc = """function():table -- Reports host-filesystem options. Real-host backend is unfinished and access stays disabled.""")
  def hostFilesystemStatus(context: Context, args: Arguments): Array[AnyRef] = {
    if (cardTier != 2) throw new IllegalArgumentException("requires Network Card Tier 3")
    val config = Settings.get.config
    val mode = if (config.hasPath("plusplus.hostFilesystem.mode")) config.getString("plusplus.hostFilesystem.mode") else "off"
    li.cil.oc.plusplus.HostAccessPolicy.Mode.parse(mode)
    import scala.collection.JavaConverters._
    result(Map[String, AnyRef]("active" -> Boolean.box(false), "configuredMode" -> mode,
      "backend" -> "unfinished; no real host access", "modes" -> Array("off", "minecraft_read_only", "system_read_only", "system_read_write")).asJava)
  }
}
