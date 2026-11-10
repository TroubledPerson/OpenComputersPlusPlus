package li.cil.oc.util

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Persistable
import net.minecraft.nbt.NBTTagCompound

object PackedColor {

  object Depth {
    def bits(depth: api.internal.TextBuffer.ColorDepth) = depth match {
      case api.internal.TextBuffer.ColorDepth.OneBit => 1
      case api.internal.TextBuffer.ColorDepth.FourBit => 4
      case api.internal.TextBuffer.ColorDepth.EightBit => 8
      case api.internal.TextBuffer.ColorDepth.SixteenBit => 16
      case api.internal.TextBuffer.ColorDepth.TwentyFourBit => 24
      case api.internal.TextBuffer.ColorDepth.ThirtyTwoBit => 32
    }

    def format(depth: api.internal.TextBuffer.ColorDepth) = depth match {
      case api.internal.TextBuffer.ColorDepth.OneBit => SingleBitFormat
      case api.internal.TextBuffer.ColorDepth.FourBit => new MutablePaletteFormat
      case api.internal.TextBuffer.ColorDepth.EightBit => new HybridFormat
      case depth => new DirectFormat(depth)
    }
  }

  private val rShift32 = 16
  private val gShift32 = 8
  private val bShift32 = 0

  private def extract(value: Int) = {
    val r = (value >>> rShift32) & 0xFF
    val g = (value >>> gShift32) & 0xFF
    val b = (value >>> bShift32) & 0xFF
    (r, g, b)
  }

  trait ColorFormat extends Persistable {
    def depth: api.internal.TextBuffer.ColorDepth

    def inflate(value: Int): Int

    def validate(value: Color) {
      if (value.isPalette) {
        throw new IllegalArgumentException("color palette not supported")
      }
    }

    def deflate(value: Color): Int

    def alpha(value: Int): Int = 255

    def isFromPalette(value: Int): Boolean = false

    override def load(nbt: NBTTagCompound) {}

    override def save(nbt: NBTTagCompound) {}
  }

  class SingleBitFormat(val color: Int) extends ColorFormat {
    override def depth = api.internal.TextBuffer.ColorDepth.OneBit

    override def inflate(value: Int) = if (value == 0) 0x000000 else color

    override def deflate(value: Color): Int = {
      if (value.value == 0) 0 else 1
    }
  }

  object SingleBitFormat extends SingleBitFormat(if (Settings.get == null) 0xFFFFFF else Settings.get.monochromeColor)

  abstract class PaletteFormat extends ColorFormat {
    override def inflate(value: Int) = palette(math.max(0, math.min(palette.length - 1, value)))

    override def validate(value: Color) {
      if (value.isPalette && (value.value < 0 || value.value >= palette.length)) {
        throw new IllegalArgumentException("invalid palette index")
      }
    }

    override def deflate(value: Color): Int =
      if (value.isPalette) math.max(0, value.value) % palette.length
      else palette.map(delta(value.value, _)).zipWithIndex.minBy(_._1)._2

    override def isFromPalette(value: Int) = true

    protected def palette: Array[Int]

    protected def delta(colorA: Int, colorB: Int) = {
      val (rA, gA, bA) = extract(colorA)
      val (rB, gB, bB) = extract(colorB)
      val dr = rA - rB
      val dg = gA - gB
      val db = bA - bB
      0.2126 * dr * dr + 0.7152 * dg * dg + 0.0722 * db * db
    }
  }

  class MutablePaletteFormat extends PaletteFormat {
    override def depth = api.internal.TextBuffer.ColorDepth.FourBit

    def apply(index: Int) = palette(index)

    def update(index: Int, value: Int) = palette(index) = value

    protected val palette = Array(
      0xFFFFFF, 0xFFCC33, 0xCC66CC, 0x6699FF,
      0xFFFF33, 0x33CC33, 0xFF6699, 0x333333,
      0xCCCCCC, 0x336699, 0x9933CC, 0x333399,
      0x663300, 0x336600, 0xFF3333, 0x000000)

    override def load(nbt: NBTTagCompound) {
      val loaded = nbt.getIntArray("palette")
      Array.copy(loaded, 0, palette, 0, math.min(loaded.length, palette.length))
    }

    override def save(nbt: NBTTagCompound) {
      nbt.setIntArray("palette", palette)
    }
  }

  class HybridFormat extends MutablePaletteFormat {
    private val reds = 6
    private val greens = 8
    private val blues = 5

    private val staticPalette = new Array[Int](240)

    {
      for (index <- staticPalette.indices) {
        val idxB = index % blues
        val idxG = (index / blues) % greens
        val idxR = (index / blues / greens) % reds
        val r = (idxR * 0xFF / (reds - 1.0) + 0.5).toInt
        val g = (idxG * 0xFF / (greens - 1.0) + 0.5).toInt
        val b = (idxB * 0xFF / (blues - 1.0) + 0.5).toInt
        staticPalette(index) = (r << rShift32) | (g << gShift32) | (b << bShift32)
      }
    }

    // Initialize palette to grayscale, excluding black and white, because
    // those are already contained in the normal color cube.
    for (i <- palette.indices) {
      val shade = 0xFF * (i + 1) / (palette.length + 1)
      this (i) = (shade << rShift32) | (shade << gShift32) | (shade << bShift32)
    }

    override def depth = api.internal.TextBuffer.ColorDepth.EightBit

    override def inflate(value: Int) =
      if (isFromPalette(value)) super.inflate(value)
      else staticPalette((value - palette.length) % 240)

    override def deflate(value: Color): Int = {
      val paletteIndex = super.deflate(value)
      if (value.isPalette) paletteIndex
      else {
        val (r, g, b) = extract(value.value)
        val idxR = (r * (reds - 1.0) / 0xFF + 0.5).toInt
        val idxG = (g * (greens - 1.0) / 0xFF + 0.5).toInt
        val idxB = (b * (blues - 1.0) / 0xFF + 0.5).toInt
        val deflated = palette.length + idxR * greens * blues + idxG * blues + idxB
        if (delta(inflate(deflated & 0xFF), value.value) < delta(inflate(paletteIndex & 0xFF), value.value)) {
          deflated
        }
        else {
          paletteIndex
        }
      }
    }

    override def isFromPalette(value: Int) = value >= 0 && value < palette.length
  }

  class DirectFormat(override val depth: api.internal.TextBuffer.ColorDepth) extends ColorFormat {
    private val bits = Depth.bits(depth)
    require(bits == 16 || bits == 24 || bits == 32)
    override def inflate(value: Int): Int =
      if (bits == 16) li.cil.oc.plusplus.ColorCodec.from565(value) else value & 0xFFFFFF
    override def alpha(value: Int): Int = if (bits == 32) value >>> 24 else 255
    override def deflate(value: Color): Int = {
      validate(value)
      if (bits == 16) li.cil.oc.plusplus.ColorCodec.to565(value.value)
      else if (bits == 24) value.value & 0xFFFFFF
      else (value.alpha << 24) | (value.value & 0xFFFFFF)
    }
  }

  case class Color(value: Int, isPalette: Boolean = false, alpha: Int = 255) {
    require(alpha >= 0 && alpha <= 255, "alpha must be 0..255")
  }

  // Two unsigned 32-bit components; legacy saves still use their original shorts.
  val ForegroundShift = 32
  val BackgroundMask = 0xFFFFFFFFL

  def pack(foreground: Color, background: Color, format: ColorFormat) = {
    ((format.deflate(foreground).toLong & 0xFFFFFFFFL) << ForegroundShift) | (format.deflate(background).toLong & BackgroundMask)
  }

  def extractForeground(color: Long): Int = (color >>> ForegroundShift).toInt

  def extractBackground(color: Long): Int = color.toInt

  def unpackForeground(color: Long, format: ColorFormat) =
    format.inflate(extractForeground(color))

  def unpackBackground(color: Long, format: ColorFormat) =
    format.inflate(extractBackground(color))

  def foregroundColor(color: Long, format: ColorFormat): Color =
    Color(unpackForeground(color, format), false, format.alpha(extractForeground(color)))

  def backgroundColor(color: Long, format: ColorFormat): Color =
    Color(unpackBackground(color, format), false, format.alpha(extractBackground(color)))

  def renderBackground(color: Long, format: ColorFormat): Int =
    li.cil.oc.plusplus.ColorCodec.over(unpackBackground(color, format), format.alpha(extractBackground(color)), 0)

  def renderForeground(color: Long, format: ColorFormat): Int = {
    val alpha = format.alpha(extractForeground(color))
    val foreground = unpackForeground(color, format)
    if (alpha == 255) foreground else li.cil.oc.plusplus.ColorCodec.over(foreground, alpha, renderBackground(color, format))
  }
}
