package li.cil.oc.common.item.traits

import li.cil.oc.Settings
import li.cil.oc.util.PackedColor

trait GPULike extends Delegate {
  def gpuTier: Int

  override protected def tooltipData: Seq[Any] = {
    val (w, h) = Settings.screenResolutionsByTier(gpuTier)
    val depth = PackedColor.Depth.bits(Settings.screenDepthsByTier(gpuTier))
    Seq(w, h, depth, li.cil.oc.plusplus.GpuCallCosts.tooltipValue(gpuTier))
  }
}
