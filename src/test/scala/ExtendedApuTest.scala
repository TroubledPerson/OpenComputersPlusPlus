import li.cil.oc.api.API
import li.cil.oc.api.detail.{Builder, NetworkAPI}
import li.cil.oc.api.machine.{Callback, Context}
import li.cil.oc.api.network.{ComponentConnector, Environment, Node, Visibility}
import li.cil.oc.server.component.{APUWithData, DataCard}
import net.minecraft.nbt.NBTTagCompound
import org.junit.runner.RunWith
import org.mockito.{Matchers, Mockito}
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ExtendedApuTest extends FunSpec {
  def withNodes(test: (ComponentConnector, ComponentConnector) => Unit): Unit = {
    val previous = API.network
    val network = Mockito.mock(classOf[NetworkAPI])
    val builder = Mockito.mock(classOf[Builder.NodeBuilder])
    val gpuBuilder = Mockito.mock(classOf[Builder.ComponentBuilder])
    val dataBuilder = Mockito.mock(classOf[Builder.ComponentBuilder])
    val gpuConnector = Mockito.mock(classOf[Builder.ComponentConnectorBuilder])
    val dataConnector = Mockito.mock(classOf[Builder.ComponentConnectorBuilder])
    val gpu = Mockito.mock(classOf[ComponentConnector]); val data = Mockito.mock(classOf[ComponentConnector])
    Mockito.when(network.newNode(Matchers.any(classOf[Environment]), Matchers.eq(Visibility.Neighbors))).thenReturn(builder)
    Mockito.when(builder.withComponent("gpu")).thenReturn(gpuBuilder)
    Mockito.when(builder.withComponent("data", Visibility.Neighbors)).thenReturn(dataBuilder)
    Mockito.when(gpuBuilder.withConnector()).thenReturn(gpuConnector)
    Mockito.when(dataBuilder.withConnector()).thenReturn(dataConnector)
    Mockito.when(gpuConnector.create()).thenReturn(gpu)
    Mockito.when(dataConnector.create()).thenReturn(data)
    Mockito.when(gpu.address()).thenReturn("gpu-address")
    Mockito.when(data.address()).thenReturn("data-address")
    API.network = network
    try ExtendedHardwareFixtures.withSettings { test(gpu, data) } finally API.network = previous
  }
  describe("extended APU with mocked owning computer and OC nodes") {
    it("keeps full matching GPU limits, cost scaling and inherited GPU callbacks") {
      withNodes { (gpu, data) =>
        for (tier <- 4 to 6) {
          val apu = new APUWithData(tier)
          assert(apu.node eq gpu)
          assert(apu.widthInfo == Seq("16","24","32")(tier-4))
          assert(apu.capacityInfo == Seq("27000","40680","72000")(tier-4))
          assert(apu.setCosts(tier) == (1.0/256)*Seq(4,6,9)(tier-4))
          assert(apu.getDeviceInfo.get("product").contains(tier.toString))
        }
        val callbacks = classOf[APUWithData].getMethods.filter(_.isAnnotationPresent(classOf[Callback])).map(_.getName).toSet
        assert(Set("bind","getDepth","setDepth","getRGBA","setForegroundRGBA","allocateBuffer").subsetOf(callbacks))
        val inherited = classOf[DataCard.Tier3].getMethods.filter(_.isAnnotationPresent(classOf[Callback])).map(_.getName).toSet
        assert(Set("generateKeyPair","ecdsa","inflate","getInflateLimit","encrypt","sha256").subsetOf(inherited))
      }
    }
    it("connects data directly only to adjacent contexts and removes it with its APU") {
      withNodes { (gpu, data) =>
        val apu = new APUWithData(4)
        val computer = Mockito.mock(classOf[Node])
        val context = Mockito.mock(classOf[Context], Mockito.withSettings().extraInterfaces(classOf[Environment]))
        Mockito.when(computer.host()).thenReturn(context.asInstanceOf[Environment])
        Mockito.when(computer.isNeighborOf(gpu)).thenReturn(true)
        apu.onConnect(computer)
        Mockito.verify(data).connect(computer)
        val stranger = Mockito.mock(classOf[Node])
        Mockito.when(stranger.host()).thenReturn(context.asInstanceOf[Environment])
        apu.onConnect(stranger)
        Mockito.verify(data, Mockito.never()).connect(stranger)
        apu.onDisconnect(computer)
        Mockito.verify(data).disconnect(computer)
        apu.onDisconnect(gpu)
        Mockito.verify(data).remove()
      }
    }
    it("retains the GPU node and VRAM tags with an independent saved data child") {
      withNodes { (gpu, data) =>
        val apu = new APUWithData(6); val saved = new NBTTagCompound()
        apu.save(saved)
        assert(saved.hasKey("node") && saved.hasKey("videoRam") && saved.hasKey("ocppData"))
        val gpuTag = saved.getCompoundTag("node")
        val dataTag = saved.getCompoundTag("ocppData").getCompoundTag("node")
        gpuTag.setString("address", "saved-gpu"); dataTag.setString("address", "saved-data")
        apu.load(saved)
        Mockito.verify(gpu).load(gpuTag)
        Mockito.verify(data).load(dataTag)
        assert(apu.getActiveBuffer(null,null)(0) == Int.box(0))
      }
    }
    it("loads an older GPU-only tag without treating the GPU address as data") {
      withNodes { (gpu, data) =>
        val saved = new NBTTagCompound(); val tag = new NBTTagCompound()
        tag.setString("address", "existing-gpu"); saved.setTag("node", tag)
        new APUWithData(4).load(saved)
        Mockito.verify(gpu).load(tag)
        Mockito.verify(data, Mockito.never()).load(Matchers.any(classOf[NBTTagCompound]))
      }
    }
  }
}
