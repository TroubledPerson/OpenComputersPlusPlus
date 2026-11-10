package li.cil.oc.client.gui

import li.cil.oc.client.{Textures,PacketSender}
import li.cil.oc.common.{container,tileentity}
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.util.ResourceLocation

/** Case controls are power and player inventory only; SuperBIOS belongs to screen. */
class VMCasePreview(preview: tileentity.VMCasePreview,inventory: InventoryPlayer=null) extends CustomGuiContainer(new container.VMCasePreview(preview,inventory)) {
  xSize=176
  ySize=224
  private var powerButton: ImageButton = _
  override def initGui(): Unit = {
    super.initGui();buttonList.clear()
    powerButton=new ImageButton(0,guiLeft+79,guiTop+55,18,18,Textures.GUI.ButtonPower,canToggle=true)
    add(buttonList,powerButton)
  }
  override def actionPerformed(button: GuiButton): Unit = if(button.id==0)PacketSender.sendPreviewPower(preview)
  override def updateScreen(): Unit = {
    super.updateScreen()
    if(!inventorySlots.canInteractWith(mc.player))mc.player.closeScreen()
  }
  override def drawGuiContainerBackgroundLayer(dt: Float,mouseX: Int,mouseY: Int): Unit = {
    GlStateManager.color(1,1,1,1)
    Textures.bind(new ResourceLocation("opencomputers","textures/gui/ocpp_vm_case.png"))
    drawTexturedModalRect(guiLeft,guiTop,0,0,xSize,ySize)
  }
  override def drawGuiContainerForegroundLayer(mouseX: Int,mouseY: Int): Unit = ()
  override def drawScreen(mouseX: Int,mouseY: Int,dt: Float): Unit = {
    powerButton.toggled=preview.biosOn
    super.drawScreen(mouseX,mouseY,dt)
  }
}
