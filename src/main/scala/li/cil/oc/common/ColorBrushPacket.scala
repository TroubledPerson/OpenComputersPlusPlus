package li.cil.oc.common

import java.io.{DataInput, DataOutput}
import li.cil.oc.util.PackedColor

/** Wire codec independent of optional integration/transport class loading. */
object ColorBrushPacket {
  def write(output: DataOutput, foreground: PackedColor.Color, background: PackedColor.Color): Unit = {
    val alpha = foreground.alpha != 255 || background.alpha != 255
    if (alpha && (foreground.isPalette || background.isPalette)) throw new IllegalArgumentException("RGBA palette brush is invalid")
    output.writeByte((if (alpha) PacketType.TextBufferMultiColorAlphaChange else PacketType.TextBufferMultiColorChange).id)
    output.writeInt(foreground.value)
    output.writeBoolean(foreground.isPalette)
    output.writeInt(background.value)
    output.writeBoolean(background.isPalette)
    if (alpha) {
      output.writeByte(foreground.alpha)
      output.writeByte(background.alpha)
    }
  }

  def read(input: DataInput, alpha: Boolean): (PackedColor.Color, PackedColor.Color) = {
    val foreground = input.readInt()
    val foregroundPalette = input.readBoolean()
    val background = input.readInt()
    val backgroundPalette = input.readBoolean()
    val foregroundAlpha = if (alpha) input.readUnsignedByte() else 255
    val backgroundAlpha = if (alpha) input.readUnsignedByte() else 255
    if (alpha && (foregroundPalette || backgroundPalette)) throw new IllegalArgumentException("RGBA palette brush is invalid")
    (PackedColor.Color(foreground, foregroundPalette, foregroundAlpha), PackedColor.Color(background, backgroundPalette, backgroundAlpha))
  }
}
