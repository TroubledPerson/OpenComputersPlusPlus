package li.cil.oc.client.renderer.block

import java.util
import java.util.Collections
import li.cil.oc.client.Textures
import li.cil.oc.common.block.property.PropertyTile
import li.cil.oc.common.tileentity.VMScreenPreview
import li.cil.oc.util.RotationHelper
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.util.{EnumFacing,ResourceLocation}
import net.minecraftforge.common.property.IExtendedBlockState

/** Native connected casing selection for the preview's fixed north-facing plane.
  * Geometry is independent of provider/power/menu readiness. No TextBuffer or GPU. */
object VMScreenPreviewModel extends SmartBlockModelBase {
  private def part(value: Int,high: Int): Int = if(value==0)2 else if(value==high)0 else 1
  def nativeTexture(width: Int,height: Int,x: Int,y: Int,side: EnumFacing): ResourceLocation = {
    val facing=RotationHelper.toLocal(EnumFacing.NORTH,EnumFacing.NORTH,side)
    val px=part(x,width-1)
    val py=part(y,height-1)
    // Same bottom-face mirror as ScreenModel; north yaw is two quarter turns.
    val ax=if(side==EnumFacing.DOWN)2-px else px
    val ay=if(side==EnumFacing.DOWN)2-py else py
    val t=Textures.Block.Screen
    if(width==1&&height==1){if(facing==EnumFacing.SOUTH)t.SingleFront(0)else t.Single(side.getIndex)}
    else if(width==1){if(facing==EnumFacing.SOUTH)t.VerticalFront(0)(ay)else t.Vertical(0)(ay)(facing.getIndex)}
    else if(height==1){if(facing==EnumFacing.SOUTH)t.HorizontalFront(0)(ax)else t.Horizontal(0)(ax)(facing.getIndex)}
    else {if(facing==EnumFacing.SOUTH)t.MultiFront(0)(ay)(ax)else t.Multi(0)(ay)(ax)(facing.getIndex)}
  }
  override def getQuads(state: IBlockState,side: EnumFacing,rand: Long): util.List[BakedQuad] = {
    // All quads are face-associated; emitting another generic face doubles it.
    if(side==null)return Collections.emptyList[BakedQuad]()
    val face=side
    val screen=state match {
      case s: IExtendedBlockState => s.getValue(PropertyTile.Tile) match {case t: VMScreenPreview => t;case _ => null}
      case _ => null
    }
    val valid=screen!=null&&screen.canvasOrigin!=null&&screen.canvasFingerprint.nonEmpty&&screen.canvasWidth>=1&&screen.canvasHeight>=1&&screen.canvasWidth<=256&&screen.canvasHeight<=256&&screen.canvasWidth.toLong*screen.canvasHeight<=256
    val width=if(valid)screen.canvasWidth else 1
    val height=if(valid)screen.canvasHeight else 1
    val x=if(valid)screen.canvasOrigin.getX-screen.getPos.getX else 0
    val y=if(valid)screen.getPos.getY-screen.canvasOrigin.getY else 0
    if(valid&&screen.getWorld!=null){
      val p=screen.getPos.offset(face)
      if(screen.getWorld.isBlockLoaded(p))screen.getWorld.getTileEntity(p) match {
        case other: VMScreenPreview if other.canvasOrigin==screen.canvasOrigin&&other.canvasFingerprint==screen.canvasFingerprint&&other.canvasWidth==width&&other.canvasHeight==height => return Collections.emptyList[BakedQuad]()
        case _ =>
      }
    }
    val native=nativeTexture(width,height,x,y,face)
    val rotation=if(face==EnumFacing.UP)2 else if(face==EnumFacing.DOWN)-2 else 0
    Collections.singletonList(bakeQuad(face,Textures.getSprite(Textures.Block.vmScreen(native)),None,rotation))
  }
}
