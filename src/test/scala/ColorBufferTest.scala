import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import li.cil.oc.api.internal.TextBuffer.ColorDepth
import li.cil.oc.common.{ColorBrushPacket, PacketType}
import li.cil.oc.common.component.GpuTextBuffer
import li.cil.oc.common.component.traits.VideoRamDevice
import li.cil.oc.util.{PackedColor, TextBuffer}
import net.minecraft.nbt.NBTTagCompound
import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ColorBufferTest extends FunSpec {
  def format(depth: ColorDepth) = PackedColor.Depth.format(depth)
  describe("cell colour compatibility and storage") {
    it("preserves legacy depth ordinals and existing message IDs") {
      assert(ColorDepth.OneBit.ordinal == 0 && ColorDepth.FourBit.ordinal == 1 && ColorDepth.EightBit.ordinal == 2)
      assert(PacketType.TextBufferMultiColorChange.id == 50)
      assert(PacketType.TextBufferMultiDepthChange.id == 52)
      assert(PacketType.ServerPower.id == 87 && PacketType.TextBufferMultiColorAlphaChange.id == 88)
    }
    it("round trips text and colours at every depth with legacy short payloads") {
      for (depth <- ColorDepth.values().toVector) {
        val buffer = new TextBuffer(4, 3, format(depth))
        buffer.foreground = PackedColor.Color(0xCC3322, false, if (depth == ColorDepth.ThirtyTwoBit) 128 else 255)
        buffer.background = PackedColor.Color(0x123456)
        buffer.set(0, 0, "A\uD83D\uDE00", false)
        buffer.fill(0, 1, 4, 2, '#')
        val tag = new NBTTagCompound()
        buffer.save(tag)
        if (PackedColor.Depth.bits(depth) <= 8) assert(tag.getByteArray("colors").length == 24)
        else assert(tag.getByteArray("ocppColors64").length == 96)
        val restored = new TextBuffer(1, 1, format(ColorDepth.EightBit))
        restored.load(tag)
        assert(restored.size == buffer.size)
        assert(restored.buffer.map(_.toVector).toVector == buffer.buffer.map(_.toVector).toVector)
        assert(restored.color.map(_.toVector).toVector == buffer.color.map(_.toVector).toVector)
        assert(restored.foreground == buffer.foreground && restored.background == buffer.background)
      }
    }
    it("keeps RGB24 exact and composes RGBA over the cell background and black backing") {
      val rgb = new TextBuffer(2, 1, format(ColorDepth.TwentyFourBit))
      rgb.foreground = PackedColor.Color(0x123456)
      rgb.fill(0, 0, 2, 1, 'X')
      assert(PackedColor.unpackForeground(rgb.color(0)(0), rgb.format) == 0x123456)
      val data = new TextBuffer(2, 1, format(ColorDepth.ThirtyTwoBit))
      val page = new GpuTextBuffer("test", 1, data)
      page.setBackgroundColorRGBA(0x0000FF, 255)
      page.setForegroundColorRGBA(0xFF0000, 128)
      page.fill(0, 0, 2, 1, 'X')
      assert(PackedColor.renderForeground(data.color(0)(0), data.format) == 0x80007F)
      page.setForegroundColorRGBA(0x00FF00, 0)
      page.fill(0, 0, 1, 1, 'X')
      assert(PackedColor.renderForeground(data.color(0)(0), data.format) == 0x0000FF)
      page.setForegroundColor(0x00FF00)
      page.fill(0, 0, 1, 1, 'X')
      assert(page.getForegroundAlpha(0, 0) == 255)
      assert(PackedColor.renderForeground(data.color(0)(0), data.format) == 0x00FF00)
    }
    it("preserves alpha through copy and resets only the addressed raw RGB channel") {
      val data = new TextBuffer(4, 2, format(ColorDepth.ThirtyTwoBit))
      val page = new GpuTextBuffer("test", 1, data)
      page.setForegroundColorRGBA(0xFF0000, 64)
      page.setBackgroundColorRGBA(0x0000FF, 128)
      page.fill(0, 0, 4, 2, 'Z')
      page.copy(0, 0, 2, 1, 1, 1)
      assert(page.getForegroundAlpha(2, 1) == 64 && page.getBackgroundAlpha(2, 1) == 128)
      page.rawSetForeground(0, 0, Array(Array(0x123456)))
      assert(page.getForegroundAlpha(0, 0) == 255 && page.getBackgroundAlpha(0, 0) == 128)
      page.rawSetBackground(0, 0, Array(Array(0xABCDEF)))
      assert(page.getBackgroundAlpha(0, 0) == 255 && page.getForegroundColor(0, 0) == 0x123456)
    }
    it("keeps VRAM maximum depth, viewport dimensions and dirty state across changes/reload") {
      val page = new GpuTextBuffer("test", 1, new TextBuffer(3, 2, format(ColorDepth.ThirtyTwoBit)))
      page.dirty = false
      page.setColorDepth(ColorDepth.EightBit)
      assert(page.dirty && page.getMaximumColorDepth == ColorDepth.ThirtyTwoBit)
      assert(page.getViewportWidth == 3 && page.getViewportHeight == 2)
      val tag = new NBTTagCompound()
      page.save(tag)
      val device = new VideoRamDevice {}
      device.loadBuffer("test", 1, tag)
      val restored = device.getBuffer(1).get
      assert(restored.getColorDepth == ColorDepth.EightBit)
      assert(restored.getMaximumColorDepth == ColorDepth.ThirtyTwoBit)
      restored.setColorDepth(ColorDepth.ThirtyTwoBit)
      restored.setForegroundColorRGBA(0x123456, 0)
      restored.setColorDepth(ColorDepth.TwentyFourBit)
      assert(restored.getForegroundAlpha == 255)
    }
    it("rejects malformed wide colour data and oversized save dimensions") {
      val data = new TextBuffer(2, 2, format(ColorDepth.ThirtyTwoBit))
      val tag = new NBTTagCompound()
      data.save(tag)
      tag.setByteArray("ocppColors64", Array[Byte](1))
      intercept[IllegalArgumentException] { data.load(tag) }
      tag.setInteger("width", Int.MaxValue)
      tag.setInteger("height", Int.MaxValue)
      intercept[IllegalArgumentException] { data.load(tag) }
    }
    it("stores the full T6 target without truncation") {
      val data = new TextBuffer(480, 150, format(ColorDepth.ThirtyTwoBit))
      data.set(479, 149, "Q", false)
      val tag = new NBTTagCompound()
      data.save(tag)
      assert(tag.getByteArray("ocppColors64").length == 576000)
      val restored = new TextBuffer(1, 1, format(ColorDepth.EightBit))
      restored.load(tag)
      assert(restored.size == (480, 150) && restored.get(479, 149) == 'Q')
    }
    it("retains opaque packet layout and appends explicit alpha bytes only for RGBA brushes") {
      def encode(fg: PackedColor.Color, bg: PackedColor.Color): Array[Byte] = {
        val bytes = new ByteArrayOutputStream()
        val builder = new DataOutputStream(bytes)
        ColorBrushPacket.write(builder, fg, bg)
        builder.flush()
        bytes.toByteArray
      }
      val opaque = encode(PackedColor.Color(0x112233), PackedColor.Color(0x445566))
      assert(opaque.length == 11 && (opaque(0) & 255) == 50)
      val rgba = encode(PackedColor.Color(0x112233, false, 128), PackedColor.Color(0x445566, false, 0))
      assert(rgba.length == 13 && (rgba(0) & 255) == 88)
      val input = new DataInputStream(new ByteArrayInputStream(rgba))
      input.readUnsignedByte()
      assert(input.readInt() == 0x112233 && !input.readBoolean())
      assert(input.readInt() == 0x445566 && !input.readBoolean())
      assert(input.readUnsignedByte() == 128 && input.readUnsignedByte() == 0 && input.available() == 0)
      val decodedInput = new DataInputStream(new ByteArrayInputStream(rgba.drop(1)))
      val (fg, bg) = ColorBrushPacket.read(decodedInput, alpha = true)
      assert(fg == PackedColor.Color(0x112233, false, 128) && bg == PackedColor.Color(0x445566, false, 0))
      intercept[java.io.EOFException] { ColorBrushPacket.read(new DataInputStream(new ByteArrayInputStream(rgba.drop(1).dropRight(1))), alpha = true) }
    }
  }
}
