package li.cil.oc.common.item

import li.cil.oc.plusplus.TierProfiles

/** Old APUs retain their historical CPU/GPU pairing and registry metadata. */
class ExtendedAPU(parent: Delegator, extendedTier: Int) extends APU(parent, extendedTier) {
  require(extendedTier >= TierProfiles.FIRST && extendedTier <= TierProfiles.LAST)
  override val unlocalizedName = "apu" + extendedTier
  override def cpuTier = extendedTier
  override def gpuTier = extendedTier
  override protected def tooltipName = Some("apuextended")
}
