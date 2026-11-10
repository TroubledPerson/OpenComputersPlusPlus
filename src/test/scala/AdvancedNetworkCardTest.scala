import li.cil.oc.api.API
import li.cil.oc.api.detail.{Builder, NetworkAPI}
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.{Component, Environment, EnvironmentHost, Node, Visibility}
import li.cil.oc.server.component.{AdvancedNetworkCard, RemoteInternetCard}
import net.minecraft.nbt.NBTTagCompound
import org.junit.runner.RunWith
import org.mockito.{Matchers, Mockito}
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AdvancedNetworkCardTest extends FunSpec {
  def withNodes(test: (Component, Component, EnvironmentHost) => Unit): Unit = {
    val previous = API.network
    val network = Mockito.mock(classOf[NetworkAPI])
    val builder = Mockito.mock(classOf[Builder.NodeBuilder])
    val modemBuilder = Mockito.mock(classOf[Builder.ComponentBuilder])
    val internetBuilder = Mockito.mock(classOf[Builder.ComponentBuilder])
    val modem = Mockito.mock(classOf[Component]); val internet = Mockito.mock(classOf[Component])
    Mockito.when(network.newNode(Matchers.any(classOf[Environment]), Matchers.any(classOf[Visibility]))).thenReturn(builder)
    Mockito.when(builder.withComponent("modem", Visibility.Neighbors)).thenReturn(modemBuilder)
    Mockito.when(builder.withComponent("internet", Visibility.Neighbors)).thenReturn(internetBuilder)
    Mockito.when(modemBuilder.create()).thenReturn(modem)
    Mockito.when(internetBuilder.create()).thenReturn(internet)
    Mockito.when(modem.address()).thenReturn("modem-address")
    Mockito.when(internet.address()).thenReturn("internet-address")
    API.network = network
    try test(modem, internet, Mockito.mock(classOf[EnvironmentHost])) finally API.network = previous
  }
  describe("combined network card lifecycle with mocked OC nodes") {
    it("reports wired modem capability without claiming in-game wireless on either new tier") {
      withNodes { (_, _, host) =>
        for (tier <- 1 to 2) {
          val card = new AdvancedNetworkCard(host, tier)
          assert(card.isWired(null, null)(0) == Boolean.box(true))
          assert(card.isWireless(null, null)(0) == Boolean.box(false))
        }
      }
    }
    it("connects the internet child directly to the adjacent owning context") {
      withNodes { (modem, internet, host) =>
        val computer = Mockito.mock(classOf[Node]); val context = Mockito.mock(classOf[Context], Mockito.withSettings().extraInterfaces(classOf[Environment]))
        Mockito.when(computer.host()).thenReturn(context.asInstanceOf[Environment])
        Mockito.when(computer.isNeighborOf(modem)).thenReturn(true)
        val card = new AdvancedNetworkCard(host, 1)
        card.onConnect(computer)
        Mockito.verify(internet).connect(computer)
        card.onDisconnect(modem)
        Mockito.verify(internet).remove()
      }
    }
    it("keeps separate saved node tags for modem and internet") {
      withNodes { (modem, internet, host) =>
        val card = new AdvancedNetworkCard(host, 2); val tag = new NBTTagCompound()
        card.save(tag)
        assert(tag.hasKey("node") && tag.hasKey("ocppInternet"))
        card.load(tag)
        Mockito.verify(modem).load(tag.getCompoundTag("node"))
        Mockito.verify(internet).load(tag.getCompoundTag("ocppInternet").getCompoundTag("node"))
      }
    }
    it("loads request userdata into a closed state without reconnecting or retaining credentials") {
      val restored = new RemoteInternetCard.RemoteValue()
      val tag = new NBTTagCompound()
      restored.save(tag); restored.load(tag)
      assert(tag == new NBTTagCompound() && restored.future == null)
      intercept[IllegalArgumentException] { restored.ready(null, null) }
    }
  }
}
