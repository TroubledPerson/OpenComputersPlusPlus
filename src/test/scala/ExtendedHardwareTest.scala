import li.cil.oc.Settings
import li.cil.oc.common.{InventorySlots, Slot, Tier}
import li.cil.oc.common.item.{APU, Delegator, ExtendedAPU, GraphicsCard}
import li.cil.oc.plusplus.GpuCallCosts
import li.cil.oc.util.Color
import net.minecraft.item.EnumDyeColor
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

/** Configuration injected into the test JVM only; no configuration files/worlds. */
object ExtendedHardwareFixtures {
  def withSettings(test: => Unit): Unit = {
    val field = Settings.getClass.getDeclaredFields.find(_.getType == classOf[Settings]).get
    field.setAccessible(true)
    val previous = field.get(Settings)
    val settings = Mockito.mock(classOf[Settings])
    Mockito.when(settings.ocppScreenResolutions).thenReturn(Array((50,16),(80,25),(160,50),(160,50),(300,90),(360,113),(480,150)))
    Mockito.when(settings.cpuComponentSupport).thenReturn(Array(8,12,16,16,24,32,48))
    Mockito.when(settings.callBudgets).thenReturn(Array(0.5,1.0,1.5,1.5,1.5,1.5,1.5))
    Mockito.when(settings.vramSizes).thenReturn(Array.fill(7)(1.0))
    Mockito.when(settings.bitbltCost).thenReturn(0.5)
    field.set(Settings, settings)
    try test finally field.set(Settings, previous)
  }
}

@RunWith(classOf[JUnitRunner])
class ExtendedHardwareTest extends FunSpec {
  describe("case slot compatibility") {
    it("accepts extended hardware only in sufficiently capable cases and preserves slot types") {
      val hardware = Set(Slot.Card, Slot.Memory, Slot.HDD, Slot.CPU)
      for (caseTier <- 0 to 6; slot <- InventorySlots.computerByTier(caseTier) if hardware(slot.slot)) {
        for (itemTier <- 0 to 7) {
          val expected = itemTier <= (if (caseTier == Tier.Four) 6 else slot.tier)
          assert(slot.accepts(slot.slot, itemTier) == expected)
          assert(!slot.accepts(Slot.EEPROM, itemTier))
        }
      }
      for (caseTier <- 0 to 2; slot <- InventorySlots.computerByTier(caseTier) if hardware(slot.slot))
        assert(slot.tier <= Tier.Three)
    }
    it("preserves creative index, row lengths, floppy and EEPROM restrictions") {
      assert(Tier.Four == 3)
      assert(InventorySlots.computerByTier.map(_.length).sameElements(Array(7,8,10,10,10,10,10)))
      val row = InventorySlots.computerByTier(Tier.Four)
      assert(row.map(_.slot).sameElements(Array(Slot.Card,Slot.Card,Slot.Card,Slot.Memory,Slot.Memory,Slot.HDD,Slot.HDD,Slot.Floppy,Slot.CPU,Slot.EEPROM)))
      assert(row(7).tier == Tier.One && !row(7).accepts(Slot.Floppy, 1))
      assert(row(9).tier == Tier.Any && row(9).accepts(Slot.EEPROM, 6))
      assert(!row(9).accepts(Slot.HDD, 6))
    }
  }
  describe("APU mappings and extended tooltips") {
    it("preserves legacy APU pairings and gives each new APU equal CPU/GPU tiers") {
      val parent = Mockito.mock(classOf[Delegator])
      for (tier <- 0 to 2) {
        val old = new APU(parent, tier)
        assert(old.cpuTier == math.min(2, tier + 1) && old.gpuTier == tier)
      }
      for (tier <- 4 to 6) {
        val apu = new ExtendedAPU(parent, tier)
        assert(apu.cpuTier == tier && apu.gpuTier == tier && apu.unlocalizedName == "apu" + tier)
      }
      intercept[IllegalArgumentException] { new ExtendedAPU(parent, 3) }
      intercept[IllegalArgumentException] { new ExtendedAPU(parent, 7) }
    }
    it("evaluates every GPU tooltip and extended APU tooltip with the matching limits") {
      ExtendedHardwareFixtures.withSettings {
        val parent = Mockito.mock(classOf[Delegator])
        for (tier <- 0 to 6) {
          val gpu = new GraphicsCard(parent, tier) { def values = tooltipData; def key = tooltipName }
          val (w,h) = Settings.screenResolutionsByTier(tier)
          assert(gpu.values.take(2) == Seq(w,h))
          if (tier >= 4) {
            assert(gpu.key.contains("graphicscardextended"))
            val apu = new ExtendedAPU(parent, tier) { def values = tooltipData; def key = tooltipName }
            assert(apu.values == Seq(Settings.get.cpuComponentSupport(tier)) ++ gpu.values)
            assert(apu.key.contains("apuextended"))
            assert(gpu.values(2) == Seq(16,24,32)(tier-4))
          }
        }
      }
    }
    it("keeps legacy tooltip values and shares conservative extended area scaling") {
      ExtendedHardwareFixtures.withSettings {
        assert((0 to 2).map(GpuCallCosts.tooltipValue) == Seq("1/1/4/2/2","2/4/8/4/4","4/8/16/8/8"))
        assert((4 to 6).map(GpuCallCosts.areaScale) == Seq(4.0,6.0,9.0))
        assert((4 to 6).map(GpuCallCosts.tooltipValue) == Seq("4","6","9"))
      }
    }
  }
  describe("tier colours") {
    it("lightens only T4 without changing dye green or T5/T6/creative accents") {
      assert(Color.rgbValues(Color.byTier(4)) == 0x66FF66)
      assert(Color.rgbValues(EnumDyeColor.GREEN) == 0x339911)
      assert(Color.byTier(3) == EnumDyeColor.MAGENTA)
      assert(Color.byTier(5) == EnumDyeColor.ORANGE && Color.byTier(6) == EnumDyeColor.RED)
    }
  }
}
