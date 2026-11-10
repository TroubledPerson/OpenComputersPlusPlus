import li.cil.oc.common.{container,tileentity}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.world.World
import net.minecraft.util.math.BlockPos
import org.junit.Test
import org.mockito.Mockito

/** Real server authorization/state methods with synthetic world/player and silent sound sink. */
class VMPreviewLifecycleTest {
  private class Preview extends tileentity.VMCasePreview {
    var soundCount=0
    override protected def emitFailedStartSound(): Unit = {soundCount+=1}
  }
  private def fixture(test:(Preview,EntityPlayer,World)=>Unit): Unit = {
    val w=Mockito.mock(classOf[World]);val p=Mockito.mock(classOf[EntityPlayer]);val t=new Preview
    t.setWorld(w);t.setPos(BlockPos.ORIGIN);p.world=w
    Mockito.when(w.isBlockLoaded(BlockPos.ORIGIN)).thenReturn(true)
    Mockito.when(w.getTileEntity(BlockPos.ORIGIN)).thenReturn(t)
    Mockito.when(p.isEntityAlive).thenReturn(true)
    p.openContainer=new container.VMCasePreview(t)
    test(t,p,w)
  }
  @Test def explicitOpenGuiAttemptBlinksBeepsOnceAndExpiresWithoutBoot(): Unit = fixture { (t,p,w) =>
    assert(!t.failedStartActive);assert(t.trySimulatedStart(p));assert(t.failedStartActive);assert(t.soundCount==1)
    for(i<-1 to 39){Mockito.when(w.getTotalWorldTime).thenReturn(i.toLong);assert(!t.trySimulatedStart(p));t.update();assert(t.failedStartActive)}
    Mockito.when(w.getTotalWorldTime).thenReturn(40L);t.update();assert(!t.failedStartActive);assert(t.trySimulatedStart(p));assert(t.soundCount==2)
    t.onChunkUnload();assert(!t.failedStartActive)
  }
  @Test def wrongGuiDistanceWorldAndUnloadedTileNeverBlinkOrBeep(): Unit = fixture { (t,p,w) =>
    p.openContainer=null;assert(!t.trySimulatedStart(p))
    p.openContainer=new container.VMCasePreview(t)
    Mockito.when(p.getDistanceSq(0.5,0.5,0.5)).thenReturn(65);assert(!t.trySimulatedStart(p))
    Mockito.when(p.getDistanceSq(0.5,0.5,0.5)).thenReturn(0)
    Mockito.when(w.isBlockLoaded(BlockPos.ORIGIN)).thenReturn(false);assert(!t.trySimulatedStart(p))
    Mockito.when(w.isBlockLoaded(BlockPos.ORIGIN)).thenReturn(true)
    p.world=Mockito.mock(classOf[World]);assert(!t.trySimulatedStart(p));assert(t.soundCount==0);assert(!t.failedStartActive)
  }
}
