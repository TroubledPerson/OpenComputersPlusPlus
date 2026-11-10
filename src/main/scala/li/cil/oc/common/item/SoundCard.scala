package li.cil.oc.common.item

class SoundCard(val parent: Delegator, val tier: Int) extends traits.Delegate with traits.ItemTier {
  require(tier >= 0 && tier <= 2)
  override def unlocalizedName = "ocpp_sound" + (tier + 1)
}
