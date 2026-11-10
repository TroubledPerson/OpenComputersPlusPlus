package li.cil.oc.common.item

class GraphicsCard(val parent: Delegator, val tier: Int) extends traits.Delegate with traits.ItemTier with traits.GPULike {
  override val unlocalizedName = super.unlocalizedName + tier

  override def gpuTier = tier

  override protected def tooltipName = Option(if (tier >= 4 && tier <= 6) "graphicscardextended" else super.unlocalizedName)
}
