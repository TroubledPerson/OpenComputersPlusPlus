package li.cil.oc.client.renderer.tileentity

import net.minecraft.client.renderer.{GlStateManager,OpenGlHelper}
import org.lwjgl.{BufferUtils}
import org.lwjgl.opengl.{GL11,GL13,GL14}

private[tileentity] object PreviewScreenRenderState {
  /** LWJGL2's general glGetFloat overload requires16 remaining floats even for RGBA. */
  def colorQueryBuffer(): java.nio.FloatBuffer = BufferUtils.createFloatBuffer(16)
}

/** Explicit scoped state: RenderState's attribute stack is intentionally a no-op.
  * UI painter order does not write depth, but existing world depth still occludes it. */
private[tileentity] final class PreviewScreenRenderState {
  private val lighting=GL11.glIsEnabled(GL11.GL_LIGHTING)
  private val cull=GL11.glIsEnabled(GL11.GL_CULL_FACE)
  private val depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
  private val blend=GL11.glIsEnabled(GL11.GL_BLEND)
  private val alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
  private val alphaFunction=GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC)
  private val alphaReference=GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF)
  private val depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
  private val depthFunction=GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
  private val sourceRGB=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
  private val destRGB=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
  private val sourceAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
  private val destAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
  private val activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
  private val brightnessX=OpenGlHelper.lastBrightnessX
  private val brightnessY=OpenGlHelper.lastBrightnessY
  private val color=PreviewScreenRenderState.colorQueryBuffer()
  GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color)
  GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
  private val texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
  private val binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
  def begin(): Unit = {
    GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableTexture2D()
    GlStateManager.enableDepth();GlStateManager.depthFunc(GL11.GL_LEQUAL);GlStateManager.depthMask(false)
    GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO)
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240f,240f)
    GlStateManager.color(1,1,1,1)
  }
  def restore(): Unit = {
    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
    GlStateManager.bindTexture(binding)
    if(texture)GlStateManager.enableTexture2D()else GlStateManager.disableTexture2D()
    GlStateManager.tryBlendFuncSeparate(sourceRGB,destRGB,sourceAlpha,destAlpha)
    if(blend)GlStateManager.enableBlend()else GlStateManager.disableBlend()
    GlStateManager.alphaFunc(alphaFunction,alphaReference)
    if(alpha)GlStateManager.enableAlpha()else GlStateManager.disableAlpha()
    GlStateManager.depthFunc(depthFunction);GlStateManager.depthMask(depthWrite)
    if(depth)GlStateManager.enableDepth()else GlStateManager.disableDepth()
    if(cull)GlStateManager.enableCull()else GlStateManager.disableCull()
    if(lighting)GlStateManager.enableLighting()else GlStateManager.disableLighting()
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,brightnessX,brightnessY)
    GlStateManager.color(color.get(0),color.get(1),color.get(2),color.get(3))
    GlStateManager.setActiveTexture(activeTexture)
  }
}
