import li.cil.oc.{Settings,api}
import li.cil.oc.common.item.{Delegator,Microchip,OpticalDisc,OpenOSOpticalDisc}
import li.cil.oc.common.recipe.OpenOSCDRecipe
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SuperBiosIntegrationTest extends FunSpec {
 net.minecraft.init.Bootstrap.register()
 describe("OpenOS factory-master recipe and preserved metadata") {
  it("appends metadata28 and returns authentic OpenOS source without consuming its data") {
   val parent=new Delegator;for(i<-0 until 23)new Microchip(parent,0)
   val blank=new OpticalDisc(parent,false);new OpticalDisc(parent,true);for(i<-0 until 3)new Microchip(parent,0)
   val master=new OpenOSOpticalDisc(parent);assert(master.itemId==28 && master.maxStackSize==1)
   val old=api.API.items;val items=Mockito.mock(classOf[api.detail.ItemAPI]);val info=Mockito.mock(classOf[api.detail.ItemInfo]);
   Mockito.when(items.get("floppy")).thenReturn(info);Mockito.when(info.item()).thenReturn(net.minecraft.init.Items.PAPER);api.API.items=items
   try{
    val source=new ItemStack(net.minecraft.init.Items.PAPER);val tag=new NBTTagCompound;tag.setString(Settings.namespace+"lootFactory",Settings.resourceDomain+":openos");source.setTagCompound(tag)
    val disc=blank.createItemStack();val inv=Mockito.mock(classOf[InventoryCrafting]);Mockito.when(inv.getSizeInventory).thenReturn(2);Mockito.when(inv.getStackInSlot(0)).thenReturn(source);Mockito.when(inv.getStackInSlot(1)).thenReturn(disc)
    val recipe=new OpenOSCDRecipe(master.createItemStack());assert(recipe.matches(inv,null));assert(recipe.canFit(2,1)&& !recipe.canFit(1,1));assert(recipe.getCraftingResult(inv).getMetadata==28)
    assert(ItemStack.areItemStacksEqual(source,recipe.getRemainingItems(inv).get(0)));assert(recipe.getRemainingItems(inv).get(1).isEmpty)
    val data=new NBTTagCompound;data.setString("optical.id",java.util.UUID.randomUUID().toString);disc.setTagCompound(data);assert(!recipe.matches(inv,null));assert(disc.hasTagCompound)
    disc.setTagCompound(null);source.setTagCompound(null);assert(!recipe.matches(inv,null))
   }finally{api.API.items=old}
  }
 }
 describe("Tier7 player inventory and server status synchronization") {
  it("keeps only36 player slots below the board, and no screen or machine slots") {
   val player=Mockito.mock(classOf[net.minecraft.entity.player.EntityPlayer]);val inventory=new net.minecraft.entity.player.InventoryPlayer(player)
   val tile=new li.cil.oc.common.tileentity.VMCasePreview;val c=new li.cil.oc.common.container.VMCasePreview(tile,inventory)
   assert(c.inventorySlots.size()==36);val it=c.inventorySlots.iterator();while(it.hasNext){val s=it.next();assert(s.inventory eq inventory);assert(s.yPos>=142&&s.yPos<=200)}
   val screen=new li.cil.oc.common.container.VMScreenPreview(new li.cil.oc.common.tileentity.VMScreenPreview,player);assert(screen.inventorySlots.isEmpty)
   c.updateProgressBar(0,1);assert(c.providerStatus==1);c.updateProgressBar(0,99);assert(c.providerStatus==0)
   assert(!c.canInteractWith(player)) // no loaded world, never grants a host capability
  }
 }
}
