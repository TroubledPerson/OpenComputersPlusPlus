package li.cil.oc.plusplus

import li.cil.oc.Settings

/** Shared cost scale for runtime GPUs and extended GPU/APU tooltips. */
object GpuCallCosts {
  def areaScale(tier: Int): Double = {
    val (width, height) = Settings.screenResolutionsByTier(tier)
    math.max(1.0, math.ceil(width.toDouble * height / 8000.0))
  }

  def tooltipValue(tier: Int): String = tier match {
    case 0 => "1/1/4/2/2"
    case 1 => "2/4/8/4/4"
    case 2 | 3 => "4/8/16/8/8"
    // This is a cost multiplier, not an unmeasured operations/tick claim.
    case t if t >= TierProfiles.FIRST && t <= TierProfiles.LAST => areaScale(t).toInt.toString
    case _ => throw new IllegalArgumentException("invalid GPU tier")
  }
}
