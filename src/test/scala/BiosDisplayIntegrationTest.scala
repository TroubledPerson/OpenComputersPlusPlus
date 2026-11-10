import li.cil.oc.api.network.{Network,Node}
import li.cil.oc.api.vm.SuperBios
import li.cil.oc.common.{container,tileentity}
import li.cil.oc.plusplus.BiosBootState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.junit.Test
import org.mockito.{Matchers,Mockito}
import scala.collection.mutable

/** Real mapped tile/container synchronization and input; synthetic loaded world only. */
class BiosDisplayIntegrationTest {
 private class Machine extends tileentity.VMCasePreview {var forced: Node=null;var beeps=0;var posts=0;override def node=forced;override protected def emitFailedStartSound(): Unit={beeps+=1};override protected def emitPostSound(): Unit={posts+=1}}
 private class Screen extends tileentity.VMScreenPreview {var forced: Node=null;override def node=forced}
 private class Fixture {
  net.minecraft.init.Bootstrap.register()
  val world=Mockito.mock(classOf[World]);val player=Mockito.mock(classOf[EntityPlayer]);val network=Mockito.mock(classOf[Network])
  val tiles=mutable.Map.empty[BlockPos,net.minecraft.tileentity.TileEntity];val nodes=new java.util.ArrayList[Node]()
  val machine=new Machine;machine.setWorld(world);machine.setPos(new BlockPos(4,0,0));tiles(machine.getPos)=machine
  player.world=world;Mockito.when(player.isEntityAlive).thenReturn(true);Mockito.when(player.getUniqueID).thenReturn(java.util.UUID.randomUUID())
  Mockito.when(world.isBlockLoaded(Matchers.any(classOf[BlockPos]))).thenAnswer(new org.mockito.stubbing.Answer[java.lang.Boolean]{def answer(i: org.mockito.invocation.InvocationOnMock)=java.lang.Boolean.valueOf(tiles.contains(i.getArguments()(0).asInstanceOf[BlockPos]))})
  Mockito.when(world.getTileEntity(Matchers.any(classOf[BlockPos]))).thenAnswer(new org.mockito.stubbing.Answer[net.minecraft.tileentity.TileEntity]{def answer(i: org.mockito.invocation.InvocationOnMock)=tiles.getOrElse(i.getArguments()(0).asInstanceOf[BlockPos],null)})
  Mockito.when(network.nodes()).thenReturn(nodes)
  private def node(t: net.minecraft.tileentity.TileEntity): Node={val n=Mockito.mock(classOf[Node]);Mockito.when(n.network).thenReturn(network);Mockito.when(n.host).thenReturn(t.asInstanceOf[li.cil.oc.api.network.Environment]);nodes.add(n);n}
  machine.forced=node(machine)
  def screen(x: Int,y: Int,z: Int=0): Screen={val s=new Screen;s.setWorld(world);s.setPos(new BlockPos(x,y,z));tiles(s.getPos)=s;s.forced=node(s);s}
  def tick(value: Long): Unit=Mockito.when(world.getTotalWorldTime).thenReturn(value)
  def power(): Boolean={player.openContainer=new container.VMCasePreview(machine);machine.trySimulatedStart(player)}
  def focus(s: Screen): container.VMScreenPreview={val c=new container.VMScreenPreview(s,player);player.openContainer=c;assert(c.canInteractWith(player));c}
  val provider=new li.cil.oc.vm.companion.OCVMIntegration.OfflineProvider
  def available(body: => Unit): Unit={SuperBios.register(provider);try body finally SuperBios.unregister(provider)}
 }
 @Test def missingCompanionBeepsButValidOfflineCompanionProjectsOnlyAfterPower(): Unit={val f=new Fixture;val s=f.screen(0,0)
  s.refreshCanvas(true);assert(!s.displayBoot);assert(f.power());assert(f.machine.beeps==1&& !f.machine.biosOn)
  f.available{f.machine.update();s.refreshCanvas(true);assert(!s.displayBoot);f.tick(40);assert(f.power());s.refreshCanvas(true);assert(s.displayBoot&&s.displayPage==0&&f.machine.beeps==1);assert(!SuperBios.mayExecuteGuest())}
  f.machine.update();s.refreshCanvas(true);assert(!s.displayBoot&& !f.machine.biosOn)
 }
 @Test def postOccursOncePerSuccessfulPowerOnAndNeverDuringPollingOrCanvasJoins(): Unit={val f=new Fixture;val s=f.screen(0,0)
  f.available{assert(f.power());assert(f.machine.posts==1&&f.machine.beeps==0)
   for(i<-1 to 12){f.tick(i);f.machine.update();s.refreshCanvas(true);assert(f.machine.biosOn&&f.machine.posts==1)}
   f.screen(1,0);s.refreshCanvas(true);assert(f.machine.posts==1)
   val c=f.focus(s);f.tick(20);assert(c.enchantItem(f.player,11));s.refreshCanvas(true);assert(f.machine.posts==1)
   f.tick(40);assert(f.power());assert(!f.machine.biosOn&&f.machine.posts==1)
   f.tick(80);assert(f.power());assert(f.machine.biosOn&&f.machine.posts==2&&f.machine.beeps==0)
  }
 }
 @Test def rejectedMissingAndCooldownStartsCannotEmitPostOrAutobootOnRecovery(): Unit={val f=new Fixture
  assert(!f.machine.trySimulatedStart(f.player));assert(f.machine.posts==0&&f.machine.beeps==0)
  assert(f.power());assert(f.machine.posts==0&&f.machine.beeps==1)
  f.available{f.machine.update();assert(!f.machine.biosOn&&f.machine.posts==0)
   f.tick(39);assert(!f.power());assert(f.machine.posts==0&&f.machine.beeps==1)
   f.tick(40);assert(f.power());assert(f.machine.posts==1&&f.machine.beeps==1)
   f.tick(41);assert(!f.power());assert(f.machine.posts==1)
  }
  f.machine.update();assert(!f.machine.biosOn&&f.machine.posts==1)
 }
 @Test def dependencyLossUnloadAndScreenResyncNeverReplayPost(): Unit={val f=new Fixture;val s=f.screen(0,0)
  f.available{assert(f.power());s.refreshCanvas(true);assert(f.machine.posts==1)}
  f.machine.update();s.refreshCanvas(true);assert(!s.displayBoot&&f.machine.posts==1)
  f.available{f.machine.update();s.refreshCanvas(true);assert(!s.displayBoot&&f.machine.posts==1)
   f.tick(40);assert(f.power());s.refreshCanvas(true);assert(s.displayBoot&&f.machine.posts==2)
   f.machine.onChunkUnload();s.refreshCanvas(true);assert(!s.displayBoot&&f.machine.posts==2)
  }
 }
 @Test def adjacentThreeByThreeHasOneOriginOneStateAndSharedRenderBounds(): Unit={val f=new Fixture;val screens=for(x<-0 until 3;y<-0 until 3)yield f.screen(x,y)
  f.available{assert(f.power());screens.head.refreshCanvas(true)
   assert(screens.count(_.isCanvasOrigin)==1);assert(screens.forall(s=>s.canvasWidth==3&&s.canvasHeight==3&&s.displayBoot));assert(screens.map(_.canvasFingerprint).distinct.size==1)
   val origin=screens.find(_.isCanvasOrigin).get;assert(origin.getPos==new BlockPos(2,0,0));val bounds=origin.getRenderBoundingBox;assert(bounds.minX==0&&bounds.maxX==3&&bounds.minY==0&&bounds.maxY==3)
   net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(classOf[Screen],"minecraft:ocpp_bios_screen_fixture")
   assert(origin.canvasReady);val member=screens.find(_ ne origin).get;member.displayEpoch+=1;assert(!origin.canvasReady);member.displayEpoch-=1;assert(origin.canvasReady)
   val copy=new tileentity.VMScreenPreview;copy.setPos(new BlockPos(1,1,0));copy.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(copy.getPos,0,screens.find(_.getPos==copy.getPos).get.getUpdateTag))
   assert(copy.displayBoot&&copy.canvasWidth==3&& !copy.isCanvasOrigin&&copy.canvasFingerprint==origin.canvasFingerprint)
  }
 }
 @Test def joinSplitAndReplacementRevokeFocusedSessionRatherThanRetarget(): Unit={val f=new Fixture;val s=f.screen(0,0)
  f.available{assert(f.power());val c=f.focus(s);val old=s.canvasFingerprint;f.screen(1,0);assert(!c.enchantItem(f.player,10));assert(!c.canInteractWith(f.player));assert(s.canvasFingerprint!=old)
   val next=f.focus(s);f.tiles.remove(new BlockPos(1,0,0));s.refreshCanvas(true);assert(!next.canInteractWith(f.player));assert(s.canvasWidth==1)
   val last=f.focus(s);f.tiles(f.machine.getPos)=new tileentity.VMCasePreview;assert(!last.canInteractWith(f.player))
  }
 }
 @Test def worldFrontHitAndFocusedKeyboardOperateTheSameCaseMenuWithGuards(): Unit={val f=new Fixture;val left=f.screen(1,0);val right=f.screen(0,0)
  f.available{assert(f.power());left.refreshCanvas(true)
   assert(!right.touch(f.player,EnumFacing.SOUTH,.5f,.7f,1f));assert(!right.touch(f.player,EnumFacing.NORTH,Float.NaN,.7f,0f))
   assert(right.touch(f.player,EnumFacing.NORTH,.5f,.65f,0f));assert(f.machine.bios.page()==BiosBootState.Page.LIBRARY)
   val c=f.focus(left);assert(!c.enchantItem(f.player,0));f.tick(2);assert(c.enchantItem(f.player,4));f.tick(4);assert(c.enchantItem(f.player,12));f.tick(6);assert(c.enchantItem(f.player,0));assert(f.machine.diagnosticStatus==1)
   right.refreshCanvas(true);assert(right.displayPage==3&&right.displayDiagnostic==1);assert(!c.enchantItem(f.player,0));f.tick(26);assert(c.enchantItem(f.player,0))
   f.player.openContainer=null;assert(!c.enchantItem(f.player,4))
  }
 }
 @Test def powerOffUnloadAndDependencyLossBlankProjectionAndRevokeInputs(): Unit={val f=new Fixture;val s=f.screen(0,0)
  f.available{assert(f.power());val c=f.focus(s);f.tick(40);assert(f.power());s.refreshCanvas(true);assert(!s.displayBoot);f.player.openContainer=c;assert(!c.enchantItem(f.player,10))
   f.tick(80);assert(f.power());s.refreshCanvas(true);assert(s.displayBoot);f.machine.onChunkUnload();s.refreshCanvas(true);assert(!s.displayBoot)
  }
 }
 @Test def malformedClientGeometryNeverProjectsAndEpochShortsRoundTrip(): Unit={val s=new tileentity.VMScreenPreview;s.setPos(BlockPos.ORIGIN);val tag=new net.minecraft.nbt.NBTTagCompound
  tag.setInteger("ocppRoute",1);tag.setInteger("ocppProvider",1);tag.setBoolean("ocppBiosOn",true);tag.setInteger("ocppCanvasWidth",Int.MaxValue);tag.setInteger("ocppCanvasHeight",Int.MaxValue)
  s.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(BlockPos.ORIGIN,0,tag));assert(!s.displayBoot&&s.canvasWidth==1)
  val c=new container.VMScreenPreview(s,null);val epoch=0x8001FFFFABCD1234L;for(i<-0 until 4)c.updateProgressBar(8+i,((epoch >>> (16*i))&0xFFFF).toInt.toShort.toInt);assert(c.epoch==epoch)
 }
 @Test def clonedTileIdentitiesFailClosedWithoutThrowingFromServerTick(): Unit={val f=new Fixture;val a=f.screen(0,0);val b=f.screen(1,0)
  val tag=new net.minecraft.nbt.NBTTagCompound;tag.setString("ocppControlIdentity",java.util.UUID.randomUUID().toString)
  a.readFromNBT(tag);a.setPos(BlockPos.ORIGIN);b.readFromNBT(tag);b.setPos(new BlockPos(1,0,0))
  a.refreshCanvas(true);assert(a.displayRoute==tileentity.VMControlRouting.Revoked&&b.displayRoute==tileentity.VMControlRouting.Revoked);assert(!a.displayBoot&&a.canvasFingerprint.isEmpty)
 }
}
