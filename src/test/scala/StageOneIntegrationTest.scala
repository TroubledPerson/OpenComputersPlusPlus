import li.cil.oc.{CreativeTab, Settings}
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.common.init.Items
import li.cil.oc.common.item.{Delegator, Memory, Microchip}
import li.cil.oc.common.recipe.{PristineShapedOreRecipe, ExtendedShapedOreRecipe}
import li.cil.oc.integration.opencomputers.DriverMemory
import li.cil.oc.plusplus.TierProfiles
import li.cil.oc.util.Rarity
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.{EnumRarity, ItemStack}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.text.TextFormatting
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class StageOneIntegrationTest extends FunSpec {
  // Real ItemStacks are final; initialize vanilla data in this test JVM only.
  net.minecraft.init.Bootstrap.register()
  describe("half RAM driver compatibility") {
    it("uses exact appended capacities and original whole-tier slots/call budgets") {
      ExtendedHardwareFixtures.withSettings {
        val sizes=TierProfiles.memorySizes(Array(192,256,384,512,768,1024),Array(2048,6144,16384),Array(2731,8192,21846))
        Mockito.when(Settings.get.ramSizes).thenReturn(sizes)
        // A mocked Item lacks Forge's registry delegate; use the real item path.
        val parent=new Delegator
        for(level<-0 to 8){
          val memory=new Memory(parent,level)
          val stack=memory.createItemStack()
          assert(DriverMemory.amount(stack)==sizes(level));assert(DriverMemory.tier(stack)==TierProfiles.memoryTier(level))
          assert(DriverMemory.getCallBudget(stack)==Settings.get.callBudgets(TierProfiles.memoryTier(level)))
        }
        for(index<-9 until 17)new Microchip(parent,0)
        for(tier<-4 to 6){val chip=new Microchip(parent,tier);assert(chip.itemId==tier+13)}
        for(level<-9 to 11){
          val memory=new Memory(parent,level);val stack=memory.createItemStack()
          assert(memory.itemId==level+11) // Half-RAM metadata20/21/22.
          assert(DriverMemory.amount(stack)==sizes(level));assert(DriverMemory.tier(stack)==level-5)
          assert(DriverMemory.getCallBudget(stack)==Settings.get.callBudgets(level-5))
        }
      }
    }
  }
  describe("tier title and advanced line colours") {
    it("retains all legacy rarity colours and gives extended tier families their own colours") {
      assert(Rarity.byTier(0)==EnumRarity.COMMON);assert(Rarity.byTier(1)==EnumRarity.UNCOMMON)
      assert(Rarity.byTier(2)==EnumRarity.RARE);assert(Rarity.byTier(3)==EnumRarity.EPIC)
      assert(Rarity.byTier(4).color==TextFormatting.GREEN)
      assert(Rarity.byTier(5).color==TextFormatting.GOLD)
      assert(Rarity.byTier(6).color==TextFormatting.RED)
    }
  }
  describe("creative tab initialization") {
    it("uses exact branding and resolves the T6 icon after an early missing lookup") {
      val key="ocpp_case6";val previous=Items.descriptors.remove(key)
      try {
        assert(CreativeTab.getTranslationKey=="OpenComputers++")
        assert(CreativeTab.createIcon eq ItemStack.EMPTY)
        val info=Mockito.mock(classOf[ItemInfo]);val first=new ItemStack(net.minecraft.init.Items.PAPER);val second=new ItemStack(net.minecraft.init.Items.DIAMOND)
        Mockito.when(info.createItemStack(1)).thenReturn(first);Items.descriptors(key)=info
        assert(CreativeTab.createIcon eq first)
        Mockito.when(info.createItemStack(1)).thenReturn(second)
        assert(CreativeTab.createIcon eq second)
        Items.descriptors.remove(key);assert(CreativeTab.createIcon eq ItemStack.EMPTY)
      } finally {Items.descriptors.remove(key);previous.foreach(p=>Items.descriptors(key)=p)}
    }
  }
  describe("safe hardware assembly") {
    it("returns six vanilla glass bottles from a six-dragon-breath shaped craft") {
      // Offline vanilla item registry only: no client/server/world is launched.
      net.minecraft.init.Bootstrap.register()
      val breath=net.minecraft.init.Items.DRAGON_BREATH
      val bottle=net.minecraft.init.Items.GLASS_BOTTLE
      val slots=Array.tabulate(9)(i=>new ItemStack(if(i<3||i>=6)breath else if(i==4)net.minecraft.init.Items.IRON_INGOT else net.minecraft.init.Items.REDSTONE))
      val inventory=Mockito.mock(classOf[InventoryCrafting])
      Mockito.when(inventory.getSizeInventory).thenReturn(9);Mockito.when(inventory.getWidth).thenReturn(3);Mockito.when(inventory.getHeight).thenReturn(3)
      for(i<-0 until 9){Mockito.when(inventory.getStackInSlot(i)).thenReturn(slots(i));Mockito.when(inventory.getStackInRowAndColumn(i%3,i/3)).thenReturn(slots(i))}
      val recipe=new ExtendedShapedOreRecipe(new ItemStack(net.minecraft.init.Items.PAPER),"ddd","rtr","ddd",
        Char.box('d'),breath,Char.box('r'),net.minecraft.init.Items.REDSTONE,Char.box('t'),net.minecraft.init.Items.IRON_INGOT)
      assert(recipe.matches(inventory,null));val remainder=recipe.getRemainingItems(inventory)
      for(i<-0 until 9)if(i<3||i>=6){assert(remainder.get(i).getItem==bottle);assert(remainder.get(i).getCount==1)}else assert(remainder.get(i).isEmpty)
      assert(recipe.getRecipeOutput.getCount==1)
    }
    it("accepts fresh ingredients and rejects data-bearing inputs before crafting") {
      val inventory=Mockito.mock(classOf[InventoryCrafting]);Mockito.when(inventory.getSizeInventory).thenReturn(2)
      val item=new ItemStack(net.minecraft.init.Items.PAPER);Mockito.when(inventory.getStackInSlot(0)).thenReturn(item)
      assert(PristineShapedOreRecipe.isPristine(inventory))
      val data=new NBTTagCompound;data.setString("label","preserve this data")
      item.setTagCompound(data)
      assert(!PristineShapedOreRecipe.isPristine(inventory))
      item.setTagCompound(new NBTTagCompound)
      assert(PristineShapedOreRecipe.isPristine(inventory))
    }
  }
}
