package li.cil.oc.common.container

import li.cil.oc.common.tileentity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.inventory.Container
import net.minecraft.inventory.{Slot,IContainerListener}

/** Only the player's inventory; no case component/EEPROM or VM storage slots. */
class VMCasePreview(val preview: net.minecraft.tileentity.TileEntity,playerInventory: InventoryPlayer=null) extends Container {
  var providerStatus=li.cil.oc.api.vm.SuperBios.MISSING
  if(playerInventory!=null) {
    for(row<-0 until 3;col<-0 until 9)addSlotToContainer(new Slot(playerInventory,col+row*9+9,8+col*18,142+row*18))
    for(col<-0 until 9)addSlotToContainer(new Slot(playerInventory,col,8+col*18,200))
  }
  override def addListener(listener: IContainerListener): Unit = {
    super.addListener(listener)
    listener.sendWindowProperty(this,0,li.cil.oc.api.vm.SuperBios.statusCode())
  }
  override def detectAndSendChanges(): Unit = {
    super.detectAndSendChanges()
    val current=li.cil.oc.api.vm.SuperBios.statusCode()
    if(current!=providerStatus){providerStatus=current;val it=listeners.iterator();while(it.hasNext)it.next().sendWindowProperty(this,0,current)}
  }
  override def updateProgressBar(id: Int,value: Int): Unit = if(id==0)providerStatus=if(value==1)1 else 0
  override def canInteractWith(player: EntityPlayer): Boolean =
    (playerInventory==null||playerInventory.player==player) && !preview.isInvalid && preview.getWorld != null && player.world == preview.getWorld && player.isEntityAlive &&
      preview.getWorld.getTileEntity(preview.getPos) == preview &&
      player.getDistanceSq(preview.getPos.getX + 0.5, preview.getPos.getY + 0.5, preview.getPos.getZ + 0.5) <= 64
}
