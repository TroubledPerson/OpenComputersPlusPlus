package li.cil.oc.client.gui

import li.cil.oc.common.{container,tileentity}
import net.minecraft.entity.player.EntityPlayer
import li.cil.oc.plusplus.BiosBootState
import org.lwjgl.input.Keyboard

/** Controls mirror the connected screen's in-world SuperBIOS; no guest launch. */
class VMScreenPreview(preview: tileentity.VMScreenPreview,viewer: EntityPlayer) extends CustomGuiContainer(new container.VMScreenPreview(preview,viewer)) {
  xSize=320
  ySize=240
  private var viewColumns=preview.canvasWidth
  private var viewRows=preview.canvasHeight
  override def initGui(): Unit = {
    val aspect=(viewColumns-2*li.cil.oc.plusplus.BiosCanvasLayout.NATIVE_BORDER)/(viewRows-2*li.cil.oc.plusplus.BiosCanvasLayout.NATIVE_BORDER)
    val maxW=math.max(32,math.min(480,width-32));val maxH=math.max(32,math.min(360,height-32))
    xSize=math.max(1,math.min(maxW,maxH*aspect).toInt);ySize=math.max(1,(xSize/aspect).toInt)
    super.initGui();buttonList.clear()
  }
  private def send(action: Int): Unit = if(action>=0&&inventoryContainer.biosOn)mc.playerController.sendEnchantPacket(inventorySlots.windowId,action)
  override def mouseClicked(x: Int,y: Int,button: Int): Unit = {
    super.mouseClicked(x,y,button)
    if(button==0)send(BiosBootState.actionAt(inventoryContainer.page,(x-guiLeft).toDouble/xSize,(y-guiTop).toDouble/ySize))
  }
  override def keyTyped(character: Char,key: Int): Unit = key match {
    case Keyboard.KEY_UP | Keyboard.KEY_W => send(1)
    case Keyboard.KEY_DOWN | Keyboard.KEY_S => send(2)
    case Keyboard.KEY_RETURN | Keyboard.KEY_NUMPADENTER => send(3)
    case Keyboard.KEY_BACK => send(4)
    case _ => super.keyTyped(character,key)
  }
  override def updateScreen(): Unit = {
    super.updateScreen()
    if(!inventorySlots.canInteractWith(mc.player))mc.player.closeScreen()
    else if(viewColumns!=inventoryContainer.canvasWidth||viewRows!=inventoryContainer.canvasHeight){
      viewColumns=inventoryContainer.canvasWidth;viewRows=inventoryContainer.canvasHeight;initGui()
    }
  }
  override def drawGuiContainerBackgroundLayer(dt: Float,mouseX: Int,mouseY: Int): Unit = {
    BiosScreenView.draw(fontRenderer,guiLeft,guiTop,xSize,ySize,inventoryContainer.biosOn,inventoryContainer.page,inventoryContainer.selection,inventoryContainer.diagnosticStatus,inventoryContainer.systemReport)
  }
  override def drawGuiContainerForegroundLayer(mouseX: Int,mouseY: Int): Unit = ()
}
