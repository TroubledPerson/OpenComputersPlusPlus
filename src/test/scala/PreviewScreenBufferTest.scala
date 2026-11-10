package li.cil.oc.client.renderer.tileentity

import java.nio.{ByteOrder,FloatBuffer}
import org.junit.Test
import org.junit.Assert._
import org.lwjgl.{BufferChecks,BufferUtils,LWJGLUtil}

/** Production allocation + actual LWJGL validation; no GLContext, Display or native GL call. */
class PreviewScreenBufferTest {
  private def requireChecks():Unit=assertTrue("LWJGL buffer validation must remain enabled",LWJGLUtil.CHECKS)
  private def rejected(buffer:FloatBuffer):Unit={
    requireChecks()
    var failed=false
    try BufferChecks.checkBuffer(buffer,16) catch {case _:IllegalArgumentException=>failed=true}
    assertTrue("LWJGL must reject an invalid query buffer",failed)
  }
  @Test def productionAllocationMeetsTheBindingContract():Unit={
    requireChecks();val buffer=PreviewScreenRenderState.colorQueryBuffer()
    assertTrue(buffer.isDirect);assertEquals(ByteOrder.nativeOrder(),buffer.order())
    assertEquals(0,buffer.position());assertEquals(16,buffer.limit());assertEquals(16,buffer.remaining())
    BufferChecks.checkBuffer(buffer,16)
  }
  @Test def oldFourFloatAllocationReproducesThePrecondition():Unit=rejected(BufferUtils.createFloatBuffer(4))
  @Test def capacityAloneDoesNotMakeLimitedOrPositionedBuffersValid():Unit={
    val limited=PreviewScreenRenderState.colorQueryBuffer();limited.limit(4);rejected(limited)
    val positioned=PreviewScreenRenderState.colorQueryBuffer();positioned.position(1);rejected(positioned)
  }
  @Test def heapBufferIsNotAValidNativeQueryDestination():Unit=rejected(FloatBuffer.allocate(16))
  @Test def savedRgbaAndNestedAllocationsRemainIndependent():Unit={
    requireChecks();val first=PreviewScreenRenderState.colorQueryBuffer();val second=PreviewScreenRenderState.colorQueryBuffer()
    assertNotSame(first,second);val rgba=Array(0.25f,0.5f,0.75f,0.125f)
    for(i<-0 until 4)first.put(i,rgba(i))
    for(i<-0 until 4){assertEquals(rgba(i),first.get(i),0f);assertEquals(0f,second.get(i),0f)}
    assertEquals(0,first.position());assertEquals(16,first.remaining());BufferChecks.checkBuffer(first,16)
  }
}
