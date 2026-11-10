import li.cil.oc.common.{InventorySlots, Slot, Tier}
import li.cil.oc.plusplus.TierProfiles

/** Exercises the actual slot table without Minecraft or a running world. */
object HardwareInventoryStandaloneTest {
  def main(args: Array[String]): Unit = {
    var checks = 0
    def check(value: Boolean): Unit = { checks += 1; assert(value, "check " + checks) }
    check(Tier.Four == 3) // Upstream creative index stays unchanged.
    check(InventorySlots.computer.length == 4)
    check(InventorySlots.computerByTier.length == 7)
    check(InventorySlots.computer.map(_.length).sameElements(Array(7, 8, 10, 10)))
    for (tier <- 0 to 3) check(InventorySlots.computerByTier(tier) eq InventorySlots.computer(tier))
    for (tier <- 4 to 6) {
      val row = InventorySlots.computerByTier(tier)
      check(row.length == 10)
      check(row.count(_.slot == Slot.Card) == 3)
      check(row.count(_.slot == Slot.Memory) == 2)
      check(row.count(_.slot == Slot.HDD) == 2)
      check(row.count(_.slot == Slot.CPU) == 1)
      check(row.count(_.slot == Slot.EEPROM) == 1)
      check(row(7).slot == Slot.Floppy)
      check(row(8).slot == Slot.CPU)
      check(row(9).slot == Slot.EEPROM)
      check(row.filter(s => Set(Slot.Card, Slot.Memory, Slot.HDD, Slot.CPU)(s.slot)).forall(_.tier == tier))
      check(TierProfiles.memoryTier(tier + 2) == tier)
      check(!row.exists(s => s.slot == Slot.HDD && s.tier == 3))
    }
    // Old physical cases still reject new hardware; creative accepts through T6.
    for (row <- InventorySlots.computer.take(3); slot <- row if Set(Slot.Memory, Slot.HDD, Slot.CPU)(slot.slot)) {
      check(!(4 <= slot.tier))
    }
    for (slot <- InventorySlots.computer(Tier.Four) if Set(Slot.Card, Slot.Memory, Slot.HDD, Slot.CPU)(slot.slot))
      check(slot.tier == TierProfiles.LAST)
    println("PASS actual inventory definitions: " + checks + " assertions")
  }
}
