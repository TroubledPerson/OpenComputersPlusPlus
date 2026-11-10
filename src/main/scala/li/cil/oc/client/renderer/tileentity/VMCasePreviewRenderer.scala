package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.VMCasePreview
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.{GlStateManager,Tessellator}
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.opengl.GL11

/** Native BIOS-on/error overlays. BIOS power is not a running guest or disk activity. */
object VMCasePreviewRenderer extends TileEntitySpecialRenderer[VMCasePreview] {
  override def render(preview: VMCasePreview,x: Double,y: Double,z: Double,f: Float,damage: Int,alpha: Float): Unit = {
    val on=preview.biosOn
    if (!on && (!preview.failedStartActive || !RenderUtil.shouldShowErrorLight(preview.hashCode))) return
    RenderState.pushAttrib()
    GlStateManager.pushMatrix()
    try {
      RenderState.disableEntityLighting(); RenderState.makeItBlend(); RenderState.setBlendAlpha(1)
      GlStateManager.translate(x+0.5,y+0.5,z+0.5)
      GlStateManager.rotate(180,0,1,0)
      GlStateManager.translate(-0.5,0.5,0.505); GlStateManager.scale(1,-1,1)
      Textures.Block.bind()
      val icon=Textures.getSprite(if(on)Textures.Block.CaseFrontOn else Textures.Block.CaseFrontError)
      val t=Tessellator.getInstance; val r=t.getBuffer
      r.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX)
      r.pos(0,1,0).tex(icon.getMinU,icon.getMaxV).endVertex()
      r.pos(1,1,0).tex(icon.getMaxU,icon.getMaxV).endVertex()
      r.pos(1,0,0).tex(icon.getMaxU,icon.getMinV).endVertex()
      r.pos(0,0,0).tex(icon.getMinU,icon.getMinV).endVertex()
      t.draw()
    } finally {
      RenderState.disableBlend(); RenderState.enableEntityLighting()
      GlStateManager.popMatrix(); RenderState.popAttrib()
    }
  }
}
