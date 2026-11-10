package li.cil.oc.common.recipe

import li.cil.oc.{Settings,api}
import li.cil.oc.common.item.{Delegator,OpticalDisc,OpenOSOpticalDisc}
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.IRecipe
import net.minecraft.util.NonNullList
import net.minecraft.world.World
import net.minecraftforge.registries.IForgeRegistryEntry

/** Blank CD is consumed; the authentic bundled OpenOS floppy is returned intact. */
class OpenOSCDRecipe(output: ItemStack) extends IForgeRegistryEntry.Impl[IRecipe] with IRecipe {
  def isSource(s: ItemStack): Boolean = !s.isEmpty && api.Items.get("floppy")!=null && s.getItem==api.Items.get("floppy").item() && s.hasTagCompound &&
    s.getTagCompound.getString(Settings.namespace+"lootFactory")==Settings.resourceDomain+":openos"
  def isBlank(s: ItemStack): Boolean = !s.isEmpty && (!s.hasTagCompound||s.getTagCompound.isEmpty) && Delegator.subItem(s).exists {
    case d: OpticalDisc => !d.rewritable && !d.isInstanceOf[OpenOSOpticalDisc]
    case _ => false
  }
  override def matches(c: InventoryCrafting,w: World): Boolean = {
    val all=(0 until c.getSizeInventory).map(c.getStackInSlot).filterNot(_.isEmpty)
    all.size==2 && all.count(isSource)==1 && all.count(isBlank)==1
  }
  override def getCraftingResult(c: InventoryCrafting): ItemStack = if(matches(c,null))output.copy() else ItemStack.EMPTY
  override def getRecipeOutput: ItemStack = output
  override def canFit(width: Int,height: Int): Boolean = width*height>=2
  override def getRemainingItems(c: InventoryCrafting): NonNullList[ItemStack] = {
    val result=NonNullList.withSize[ItemStack](c.getSizeInventory,ItemStack.EMPTY)
    for(i<-0 until c.getSizeInventory if isSource(c.getStackInSlot(i))){val copy=c.getStackInSlot(i).copy();copy.setCount(1);result.set(i,copy)}
    result
  }
}
