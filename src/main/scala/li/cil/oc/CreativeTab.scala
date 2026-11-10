package li.cil.oc

import net.minecraft.creativetab.CreativeTabs

object CreativeTab extends CreativeTabs(CreativeTabs.getNextID, OpenComputers.Name) {
  // Resolve on every request: an early lookup must not cache a missing icon.
  override def createIcon = Option(common.init.Items.get("ocpp_case6"))
    .map(_.createItemStack(1)).getOrElse(net.minecraft.item.ItemStack.EMPTY)

  override def getTranslationKey = "OpenComputers++"
}
