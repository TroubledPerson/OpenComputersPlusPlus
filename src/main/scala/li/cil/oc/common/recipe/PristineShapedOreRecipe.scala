package li.cil.oc.common.recipe

import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraft.world.World

/** Hardware assembly must never silently consume a configured CPU/GPU/RAM. */
object PristineShapedOreRecipe {
  def isPristine(inventory: InventoryCrafting): Boolean =
    (0 until inventory.getSizeInventory).forall { index =>
      val stack = inventory.getStackInSlot(index)
      stack == null || stack.isEmpty || !stack.hasTagCompound || stack.getTagCompound.isEmpty
    }
}

class PristineShapedOreRecipe(output: ItemStack, ingredients: AnyRef*)
  extends ExtendedShapedOreRecipe(output, ingredients: _*) {
  override def matches(inventory: InventoryCrafting, world: World): Boolean =
    PristineShapedOreRecipe.isPristine(inventory) && super.matches(inventory, world)

  override def getCraftingResult(inventory: InventoryCrafting): ItemStack =
    if (PristineShapedOreRecipe.isPristine(inventory)) super.getCraftingResult(inventory)
    else ItemStack.EMPTY
}
