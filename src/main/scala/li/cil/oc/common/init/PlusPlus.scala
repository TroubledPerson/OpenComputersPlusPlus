package li.cil.oc.common.init

import li.cil.oc.common.block.Case
import li.cil.oc.common.item
import li.cil.oc.plusplus.TierProfiles
import li.cil.oc.common.recipe.Recipes

/** Separate item registry preserves every upstream delegator's metadata order. */
object PlusPlus {
  def init(): Unit = {
    val components = Items.newItem(new item.Delegator(), "plusplus")
    for (tier <- TierProfiles.FIRST to TierProfiles.LAST) {
      Recipes.addSubItem(new item.CPU(components, tier), "ocpp_cpu" + tier, "oc:ocpp_cpu" + tier)
      Recipes.addSubItem(new item.Memory(components, tier + 2), "ocpp_ram" + tier, "oc:ocpp_ram" + tier)
      Recipes.addSubItem(new item.HardDiskDrive(components, tier), "ocpp_hdd" + tier, "oc:ocpp_hdd" + tier)
      Recipes.addBlock(new Case(tier), "ocpp_case" + tier, "oc:ocpp_case" + tier)
    }
    // Append after all existing OC++ metadata entries; never interleave upgrades.
    for (tier <- TierProfiles.FIRST to TierProfiles.LAST) {
      Recipes.addSubItem(new item.GraphicsCard(components, tier), "ocpp_gpu" + tier, "oc:ocpp_gpu" + tier)
      Recipes.addBlock(new li.cil.oc.common.block.Screen(tier), "ocpp_screen" + tier, "oc:ocpp_screen" + tier)
    }
    for (tier <- 1 to 2) Items.registerItem(new item.AdvancedNetworkCard(components, tier), "ocpp_network" + (tier + 1))
    // Metadata 14..16. Never insert ahead of any dev.5 component.
    for (tier <- TierProfiles.FIRST to TierProfiles.LAST)
      Recipes.addSubItem(new item.ExtendedAPU(components, tier), "ocpp_apu" + tier, "oc:ocpp_apu" + tier)
    // Metadata17..19 chips,20..22 half RAM. Never shift any previous entry.
    for (tier <- TierProfiles.FIRST to TierProfiles.LAST)
      Recipes.addSubItem(new item.Microchip(components, tier), "ocpp_chip" + tier, "oc:ocpp_chip" + tier)
    for (tier <- TierProfiles.FIRST to TierProfiles.LAST)
      Recipes.addSubItem(new item.Memory(components, tier + 5), "ocpp_ram" + tier + "_5", "oc:ocpp_ram" + tier + "_5")
    // Metadata23/24; preserve all dev.9 metadata and independent floppy slots.
    Recipes.addSubItem(new item.OpticalDisc(components, false), "ocpp_cdr", "oc:ocpp_cdr")
    Recipes.addSubItem(new item.OpticalDisc(components, true), "ocpp_cdrw", "oc:ocpp_cdrw")
    Recipes.addBlock(new li.cil.oc.common.block.CDDrive(), "ocpp_cddrive", "oc:ocpp_cddrive")
    net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(classOf[li.cil.oc.common.tileentity.CDDrive], "oc:ocpp_cddrive")
    // Sound cards append at25/26/27; builtin PC beep stays independent.
    for(tier <- 0 to 2)
      Recipes.addSubItem(new item.SoundCard(components,tier),"ocpp_sound"+(tier+1),"oc:ocpp_sound"+(tier+1))
    // Metadata28 only. OpenOS is for normal OC computers, not Tier7 SuperBIOS.
    Recipes.addSubItem(new item.OpenOSOpticalDisc(components),"ocpp_openos_cdr","oc:ocpp_openos_cdr")
    // Creative-only preview; no survival cost or automatic migration to a VM case.
    Items.registerBlock(new li.cil.oc.common.block.VMCasePreview(), "ocpp_case7_preview")
    net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(classOf[li.cil.oc.common.tileentity.VMCasePreview], "oc:ocpp_case7_preview")
    Recipes.addBlock(new li.cil.oc.common.block.VMScreenPreview(), "ocpp_screen7_preview", "oc:ocpp_screen7_preview")
    net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(classOf[li.cil.oc.common.tileentity.VMScreenPreview], "oc:ocpp_screen7_preview")
  }
}
