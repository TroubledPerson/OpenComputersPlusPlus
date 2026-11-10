package li.cil.oc.integration.minecraft

import com.typesafe.config.Config
import li.cil.oc.common.recipe.ExtendedShapedOreRecipe
import li.cil.oc.common.recipe.ExtendedShapelessOreRecipe
import li.cil.oc.common.recipe.PristineShapedOreRecipe
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.common.recipe.Recipes.RecipeException
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.FurnaceRecipes
import net.minecraftforge.fml.common.registry.GameRegistry
import net.minecraftforge.oredict.OreDictionary

import scala.collection.convert.WrapAsScala._
import scala.collection.mutable

object RecipeHandler {
  def init(): Unit = {
    Recipes.registerRecipeHandler("shaped", addShapedRecipe)
    Recipes.registerRecipeHandler("ocpp_pristine_shaped", (output, config) => addShapedRecipe(output, config, true))
    Recipes.registerRecipeHandler("ocpp_openos_cd", (output, config) => Recipes.addRecipe(new li.cil.oc.common.recipe.OpenOSCDRecipe(output)))
    Recipes.registerRecipeHandler("shapeless", addShapelessRecipe)
    Recipes.registerRecipeHandler("furnace", addFurnaceRecipe)
  }

  def addShapedRecipe(output: ItemStack, recipe: Config): Unit = addShapedRecipe(output, recipe, false)

  private def addShapedRecipe(output: ItemStack, recipe: Config, pristine: Boolean) {
    val rows = recipe.getList("input").unwrapped().map {
      case row: java.util.List[AnyRef]@unchecked => row.map(Recipes.parseIngredient)
      case other => throw new RecipeException(s"Invalid row entry for shaped recipe (not a list: $other).")
    }
    output.setCount(Recipes.tryGetCount(recipe))

    var number = -1
    var shape = mutable.ArrayBuffer.empty[String]
    val input = mutable.ArrayBuffer.empty[AnyRef]
    for (row <- rows) {
      val (pattern, ingredients) = row.foldLeft((new StringBuilder, Iterable.empty[AnyRef]))((acc, ingredient) => {
        val (pattern, ingredients) = acc
        ingredient match {
          case _@(_: ItemStack | _: String) =>
            number += 1
            (pattern.append(('a' + number).toChar), ingredients ++ Iterable(Char.box(('a' + number).toChar), ingredient))
          case _ => (pattern.append(' '), ingredients)
        }
      })
      shape += pattern.toString
      input ++= ingredients
    }
    if (input.nonEmpty && output.getCount > 0) {
      if (pristine) Recipes.addRecipe(new PristineShapedOreRecipe(output, shape ++ input: _*))
      else Recipes.addRecipe(new ExtendedShapedOreRecipe(output, shape ++ input: _*))
    }
  }

  def addShapelessRecipe(output: ItemStack, recipe: Config) {
    val input = recipe.getValue("input").unwrapped() match {
      case list: java.util.List[AnyRef]@unchecked => list.map(Recipes.parseIngredient)
      case other => Seq(Recipes.parseIngredient(other))
    }
    output.setCount(Recipes.tryGetCount(recipe))

    if (input.nonEmpty && output.getCount > 0) {
      Recipes.addRecipe(new ExtendedShapelessOreRecipe(output, input: _*))
    }
  }

  def addFurnaceRecipe(output: ItemStack, recipe: Config) {
    val input = Recipes.parseIngredient(recipe.getValue("input").unwrapped())
    output.setCount(Recipes.tryGetCount(recipe))

    input match {
      case stack: ItemStack =>
        FurnaceRecipes.instance.addSmeltingRecipe(stack, output, 0)
      case name: String =>
        for (stack <- OreDictionary.getOres(name)) {
          FurnaceRecipes.instance.addSmeltingRecipe(stack, output, 0)
        }
      case _ =>
    }
  }
}
