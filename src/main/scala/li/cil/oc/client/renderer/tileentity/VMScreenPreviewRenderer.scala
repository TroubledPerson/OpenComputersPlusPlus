package li.cil.oc.client.renderer.tileentity

import li.cil.oc.common.tileentity.VMScreenPreview
import li.cil.oc.client.gui.BiosScreenView
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer

/** One origin renders one joined north-facing canvas, including internal joins. */
object VMScreenPreviewRenderer extends TileEntitySpecialRenderer[VMScreenPreview] {
  override def render(screen: VMScreenPreview,x: Double,y: Double,z: Double,dt: Float,damage: Int,alpha: Float): Unit = {
    if(!screen.canvasReady)return
    val state=new PreviewScreenRenderState
    GlStateManager.pushMatrix()
    try{
      state.begin()
      GlStateManager.translate(x+1,y+screen.canvasHeight,z-0.002)
      GlStateManager.rotate(180,0,1,0);GlStateManager.scale(1.0/128,-1.0/128,1.0/128)
      BiosScreenView.draw(getFontRenderer,18,18,screen.canvasWidth*128-36,screen.canvasHeight*128-36,screen.displayBoot,screen.displayPage,screen.displaySelection,screen.displayDiagnostic,screen.displayReport)
    }finally{GlStateManager.popMatrix();state.restore()}
  }
}
