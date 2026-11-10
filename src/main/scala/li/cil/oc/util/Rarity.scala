package li.cil.oc.util

import net.minecraft.item.EnumRarity
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.common.util.EnumHelper

object Rarity {
  private val lookup = Array(EnumRarity.COMMON, EnumRarity.UNCOMMON, EnumRarity.RARE, EnumRarity.EPIC)

  // Match the base mod's full-title rarity colouring, without touching specs.
  private lazy val extended = Array(
    EnumHelper.addRarity("OCPP_TIER_4", TextFormatting.GREEN, "OC++ Tier 4"),
    EnumHelper.addRarity("OCPP_TIER_5", TextFormatting.GOLD, "OC++ Tier 5"),
    EnumHelper.addRarity("OCPP_TIER_6", TextFormatting.RED, "OC++ Tier 6"))

  def byTier(tier: Int) =
    if (tier >= 4 && tier <= 6) extended(tier - 4)
    else lookup(tier max 0 min (lookup.length - 1))
}
