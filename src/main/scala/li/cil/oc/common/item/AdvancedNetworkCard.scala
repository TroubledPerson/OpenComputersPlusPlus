package li.cil.oc.common.item

class AdvancedNetworkCard(parent: Delegator, val networkTier: Int) extends NetworkCard(parent) {
  override val unlocalizedName = "networkcard" + (networkTier + 1)
}
