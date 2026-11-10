package li.cil.oc.common

object InventorySlots {
  val computer = Array(
    Array(
      InventorySlot(Slot.Card, Tier.One),
      InventorySlot(Slot.Card, Tier.One),
      InventorySlot(Slot.Memory, Tier.One),
      InventorySlot(Slot.HDD, Tier.One),
      InventorySlot(Slot.CPU, Tier.One),
      InventorySlot(Slot.Memory, Tier.One),
      InventorySlot(Slot.EEPROM, Tier.Any)
    ),

    Array(
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Card, Tier.One),
      InventorySlot(Slot.Memory, Tier.Two),
      InventorySlot(Slot.Memory, Tier.Two),
      InventorySlot(Slot.HDD, Tier.Two),
      InventorySlot(Slot.HDD, Tier.One),
      InventorySlot(Slot.CPU, Tier.Two),
      InventorySlot(Slot.EEPROM, Tier.Any)
    ),

    Array(
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Two),
      InventorySlot(Slot.Floppy, Tier.One),
      InventorySlot(Slot.CPU, Tier.Three),
      InventorySlot(Slot.EEPROM, Tier.Any)
    ),

    Array(
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.Floppy, Tier.One),
      InventorySlot(Slot.CPU, Tier.Three),
      InventorySlot(Slot.EEPROM, Tier.Any)
    )
  )

  // Preserve all slot positions/types. Only the creative case's hardware caps grow.
  // Player ownership/creative-mode and driver host restrictions remain enforced.
  for (index <- computer(Tier.Four).indices) {
    val slot = computer(Tier.Four)(index)
    if (Set(Slot.Card, Slot.Memory, Slot.HDD, Slot.CPU)(slot.slot))
      computer(Tier.Four)(index) = slot.copy(tier = li.cil.oc.plusplus.TierProfiles.LAST)
  }

  // Two disks stay separate mounts.
  val extendedComputers = (li.cil.oc.plusplus.TierProfiles.FIRST to li.cil.oc.plusplus.TierProfiles.LAST).map(tier => Array(
    InventorySlot(Slot.Card, tier),
    InventorySlot(Slot.Card, tier),
    InventorySlot(Slot.Card, tier),
    InventorySlot(Slot.Memory, tier),
    InventorySlot(Slot.Memory, tier),
    InventorySlot(Slot.HDD, tier),
    InventorySlot(Slot.HDD, tier),
    InventorySlot(Slot.Floppy, Tier.One),
    InventorySlot(Slot.CPU, tier),
    InventorySlot(Slot.EEPROM, Tier.Any)
  )).toArray

  val computerByTier = computer ++ extendedComputers

  val server = Array(
    Array(
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.CPU, Tier.Two),
      InventorySlot(Slot.ComponentBus, Tier.Two),
      InventorySlot(Slot.Memory, Tier.Two),
      InventorySlot(Slot.Memory, Tier.Two),
      InventorySlot(Slot.HDD, Tier.Two),
      InventorySlot(Slot.HDD, Tier.Two),
      InventorySlot(Slot.EEPROM, Tier.Any)
    ),

    Array(
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.CPU, Tier.Three),
      InventorySlot(Slot.ComponentBus, Tier.Three),
      InventorySlot(Slot.ComponentBus, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.EEPROM, Tier.Any)
    ),

    Array(
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.CPU, Tier.Three),
      InventorySlot(Slot.ComponentBus, Tier.Three),
      InventorySlot(Slot.ComponentBus, Tier.Three),
      InventorySlot(Slot.ComponentBus, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.Card, Tier.Two),
      InventorySlot(Slot.EEPROM, Tier.Any)
    ),

    Array(
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.CPU, Tier.Three),
      InventorySlot(Slot.ComponentBus, Tier.Three),
      InventorySlot(Slot.ComponentBus, Tier.Three),
      InventorySlot(Slot.ComponentBus, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.Memory, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.HDD, Tier.Three),
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.Card, Tier.Three),
      InventorySlot(Slot.EEPROM, Tier.Any)
    )
  )

  val relay = Array(
    InventorySlot(Slot.CPU, Tier.Three),
    InventorySlot(Slot.Memory, Tier.Three),
    InventorySlot(Slot.HDD, Tier.Three),
    InventorySlot(Slot.Card, Tier.Three)
  )

  val switch = Array(
    InventorySlot(Slot.CPU, Tier.Three),
    InventorySlot(Slot.Memory, Tier.Three),
    InventorySlot(Slot.HDD, Tier.Three)
  )

  case class InventorySlot(slot: String, tier: Int) {
    def accepts(itemSlot: String, itemTier: Int): Boolean = itemSlot == slot && itemTier <= tier
  }

}
