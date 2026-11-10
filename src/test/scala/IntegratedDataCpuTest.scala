import li.cil.oc.api.API
import li.cil.oc.api.detail.NetworkAPI
import li.cil.oc.api.network.{ComponentConnector, Environment, Visibility}
import li.cil.oc.api.machine.Callback
import li.cil.oc.server.component.{CPU, CPUWithData, DataCard}
import net.minecraft.nbt.NBTTagCompound
import org.junit.runner.RunWith
import org.mockito.{Matchers, Mockito}
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class IntegratedDataCpuTest extends FunSpec {
  def withMockNetwork(test: ComponentConnector => Unit): Unit = {
    val previous = API.network
    val network = Mockito.mock(classOf[NetworkAPI], Mockito.RETURNS_DEEP_STUBS)
    val node = Mockito.mock(classOf[ComponentConnector])
    Mockito.when(network.newNode(Matchers.any(classOf[Environment]), Matchers.eq(Visibility.Neighbors))
      .withComponent("data", Visibility.Neighbors).withConnector().create()).thenReturn(node)
    API.network = network
    try test(node) finally API.network = previous
  }
  describe("CPU-integrated Data Card T3") {
    it("integrates only T4-T6 while preserving all legacy and creative CPU types") {
      withMockNetwork { node =>
        for (tier <- 0 to 3) assert(CPU.create(tier).isInstanceOf[CPU])
        for (tier <- 4 to 6) {
          val cpu = CPU.create(tier)
          assert(cpu.isInstanceOf[CPUWithData] && cpu.isInstanceOf[DataCard.Tier3])
          assert(cpu.node() eq node)
        }
      }
    }
    it("inherits the original callback names and budgets from all three data tiers") {
      val methods = classOf[CPUWithData].getMethods.filter(_.isAnnotationPresent(classOf[Callback])).map(m => m.getName -> m).toMap
      val expected = Set("encode64", "decode64", "deflate", "inflate", "crc32", "md5", "sha256", "encrypt", "decrypt", "random", "generateKeyPair", "deserializeKey", "ecdh", "ecdsa", "getLimit", "getInflateLimit")
      assert(expected.subsetOf(methods.keySet))
      assert(methods("inflate").getAnnotation(classOf[Callback]).limit() == 4)
      assert(methods("random").getAnnotation(classOf[Callback]).limit() == 4)
    }
    it("passes old CPU node data through the same load tag without allocating a replacement address") {
      withMockNetwork { node =>
        val tag = new NBTTagCompound()
        val oldNode = new NBTTagCompound()
        oldNode.setString("address", "legacy-cpu-address")
        tag.setTag("node", oldNode)
        new CPUWithData(4).load(tag)
        Mockito.verify(node).load(oldNode)
      }
    }
    it("rejects construction on legacy or creative tiers") {
      withMockNetwork { _ =>
        intercept[IllegalArgumentException] { new CPUWithData(3) }
        intercept[IllegalArgumentException] { new CPUWithData(7) }
      }
    }
  }
}
