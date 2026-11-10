import li.cil.oc.api.network.{Component,Network,Node}
import li.cil.oc.api.vm.SuperBios
import li.cil.oc.common.{container,tileentity}
import li.cil.oc.plusplus.{BiosBootState,BiosReport,OpticalAudioDiagnostics}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.junit.Test
import org.mockito.{Matchers,Mockito}
import scala.collection.mutable

/** Actual mapped tiles and metadata with mock cable nodes. No world, disk or sound device. */
class BiosContentIntegrationTest {
 private class Machine extends tileentity.VMCasePreview {var forced:Node=null;var posts=0;override def node=forced;override protected def emitPostSound():Unit={posts+=1};override protected def emitFailedStartSound():Unit=()}
 private class Screen extends tileentity.VMScreenPreview {var forced:Node=null;override def node=forced}
 private class Fixture {
  net.minecraft.init.Bootstrap.register()
  val world=Mockito.mock(classOf[World]);val player=Mockito.mock(classOf[EntityPlayer]);val network=Mockito.mock(classOf[Network]);val nodes=new java.util.ArrayList[Node]
  val tiles=mutable.Map.empty[BlockPos,TileEntity];val machine=new Machine;val screen=new Screen
  player.world=world;Mockito.when(player.isEntityAlive).thenReturn(true);Mockito.when(player.getUniqueID).thenReturn(java.util.UUID.randomUUID())
  Mockito.when(world.isBlockLoaded(Matchers.any(classOf[BlockPos]))).thenAnswer(new org.mockito.stubbing.Answer[java.lang.Boolean]{def answer(i:org.mockito.invocation.InvocationOnMock)=java.lang.Boolean.valueOf(tiles.contains(i.getArguments()(0).asInstanceOf[BlockPos]))})
  Mockito.when(world.getTileEntity(Matchers.any(classOf[BlockPos]))).thenAnswer(new org.mockito.stubbing.Answer[TileEntity]{def answer(i:org.mockito.invocation.InvocationOnMock)=tiles.getOrElse(i.getArguments()(0).asInstanceOf[BlockPos],null)})
  Mockito.when(network.nodes()).thenReturn(nodes)
  def loaded(t:TileEntity,x:Int):Unit={t.setWorld(world);t.setPos(new BlockPos(x,0,0));tiles(t.getPos)=t}
  def node(host:li.cil.oc.api.network.Environment):Node={val n=Mockito.mock(classOf[Node]);Mockito.when(n.host).thenReturn(host);Mockito.when(n.network).thenReturn(network);nodes.add(n);n}
  loaded(machine,2);loaded(screen,0);machine.forced=node(machine);screen.forced=node(screen)
  def component(host:li.cil.oc.api.network.Environment,name:String):Component={val n=Mockito.mock(classOf[Component]);Mockito.when(n.host).thenReturn(host);Mockito.when(n.network).thenReturn(network);Mockito.when(n.name()).thenReturn(name);Mockito.when(n.canBeSeenFrom(machine.node)).thenReturn(true);nodes.add(n);n}
  def tick(t:Long):Unit=Mockito.when(world.getTotalWorldTime).thenReturn(t)
  def power():Boolean={player.openContainer=new container.VMCasePreview(machine);machine.trySimulatedStart(player)}
  def available(body: =>Unit):Unit={val provider=new li.cil.oc.vm.companion.OCVMIntegration.OfflineProvider;SuperBios.register(provider);try{assert(power());body}finally{SuperBios.unregister(provider)}}
 }
 @Test def inventoryCountsOnlyVisibleComponentsAndOneAggregateRaidQuotaWithoutOpeningFiles():Unit={val f=new Fixture
  val raid=Mockito.mock(classOf[tileentity.Raid]);val raidPos=new BlockPos(4,0,0);Mockito.when(raid.getWorld).thenReturn(f.world);Mockito.when(raid.getPos).thenReturn(raidPos);f.tiles(raidPos)=raid;f.node(raid)
  val fs=Mockito.mock(classOf[li.cil.oc.server.component.FileSystem]);val disk=Mockito.mock(classOf[li.cil.oc.api.fs.FileSystem]);Mockito.when(fs.host).thenReturn(Some(raid));Mockito.when(fs.fileSystem).thenReturn(disk);Mockito.when(disk.spaceTotal()).thenReturn(48L*1024*1024);Mockito.when(disk.isReadOnly()).thenReturn(true);f.component(fs,"filesystem")
  val gpu=f.component(Mockito.mock(classOf[li.cil.oc.api.network.Environment]),"gpu");val hidden=f.component(Mockito.mock(classOf[li.cil.oc.api.network.Environment]),"modem");Mockito.when(hidden.canBeSeenFrom(f.machine.node)).thenReturn(false)
  f.available{val r=f.machine.currentReport();assert(r(BiosReport.STATUS)==1&&r(BiosReport.RAIDS)==1&&r(BiosReport.FILESYSTEMS)==1);assert(r(BiosReport.CAPACITY_KIB)==49152&&r(BiosReport.READ_ONLY)==1);assert(r(BiosReport.GPU)==1&&r(BiosReport.MODEM)==0);assert(r(BiosReport.SCREENS)==1)
   Mockito.verify(disk).spaceTotal();Mockito.verify(disk).isReadOnly();Mockito.verifyNoMoreInteractions(disk)
   r(BiosReport.GPU)=99;assert(f.machine.currentReport()(BiosReport.GPU)==1)
  }
 }
 @Test def duplicateOrOversizedGraphsFailClosed():Unit={val f=new Fixture
  f.available{f.nodes.add(f.machine.node);assert(tileentity.BiosInventory.capture(f.machine)(BiosReport.STATUS)==2);f.nodes.remove(f.nodes.size()-1)
   for(i<-0 until 257)f.node(Mockito.mock(classOf[li.cil.oc.api.network.Environment]));assert(tileentity.BiosInventory.capture(f.machine)(BiosReport.STATUS)==2)
  }
 }
 @Test def opticalMediaMetadataIsVirtualAndUnloadedQuotasAreNotQueried():Unit={val f=new Fixture
  val cd=Mockito.mock(classOf[tileentity.CDDrive]);val p=new BlockPos(5,0,0);Mockito.when(cd.getWorld).thenReturn(f.world);Mockito.when(cd.getPos).thenReturn(p);f.tiles(p)=cd;f.component(cd,"cd_drive")
  val fs=Mockito.mock(classOf[li.cil.oc.server.component.FileSystem]);val disk=Mockito.mock(classOf[li.cil.oc.api.fs.FileSystem]);Mockito.when(fs.host).thenReturn(Some(cd));Mockito.when(fs.fileSystem).thenReturn(disk);Mockito.when(disk.spaceTotal()).thenReturn(32L*1024*1024);Mockito.when(disk.isReadOnly()).thenReturn(true);val media=f.component(fs,"filesystem");Mockito.when(cd.filesystemNode).thenReturn(Some(media))
  f.available{val r=f.machine.currentReport();assert(r(BiosReport.CD_DRIVES)==1&&r(BiosReport.MEDIA)==1&&r(BiosReport.READ_ONLY)==1&&r(BiosReport.CAPACITY_KIB)==32768)
   Mockito.verify(disk).spaceTotal();Mockito.verify(disk).isReadOnly();Mockito.verifyNoMoreInteractions(disk)
   f.tiles.remove(p);Mockito.reset(disk);val unavailable=tileentity.BiosInventory.capture(f.machine);assert(unavailable(BiosReport.CD_DRIVES)==0&&unavailable(BiosReport.QUOTA_UNKNOWN)==1&&unavailable(BiosReport.CAPACITY_KIB)==0);Mockito.verifyNoMoreInteractions(disk)
  }
 }
 @Test def scratchInputAndAudioPagesAreExplicitRateLimitedAndNeverReplayPost():Unit={val f=new Fixture
  f.available{f.machine.bios.click(.5,.62,f.machine.bios.epoch(),true);f.screen.refreshCanvas(true)
   val controls=new container.VMScreenPreview(f.screen,f.player);f.player.openContainer=controls
   assert(controls.enchantItem(f.player,20));assert(controls.systemReport(BiosReport.MEMORY_TEST)==1);assert(!controls.enchantItem(f.player,21));f.tick(20);assert(controls.enchantItem(f.player,21));assert(controls.systemReport(BiosReport.INPUT_TEST)==1)
   f.tick(22);assert(controls.enchantItem(f.player,22));assert(f.machine.bios.page()==BiosBootState.Page.AUDIO);assert(f.machine.posts==1);assert(!SuperBios.mayExecuteGuest())
   val before=OpticalAudioDiagnostics.snapshot();for(i<-0 until 10)OpticalAudioDiagnostics.snapshot();assert(java.util.Arrays.equals(before,OpticalAudioDiagnostics.snapshot()))
   f.machine.onChunkUnload();assert(f.machine.currentReport()(BiosReport.MEMORY_TEST)==0)
  }
 }
 @Test def focusedSnapshotCommitsAtomicallyAndMalformedUpdatesDoNotBecomeSuccess():Unit={val s=new tileentity.VMScreenPreview;val c=new container.VMScreenPreview(s,null);val r=BiosReport.empty();r(BiosReport.CAPACITY_KIB)=Int.MaxValue;r(BiosReport.STATUS)=1;r(BiosReport.MEMORY_TEST)=1
  for(i<-0 until BiosReport.SIZE;half<-0 until 2){c.updateProgressBar(BiosReport.REPORT_FIRST+i*2+half,((r(i) >>> (16*half))&0xffff).toShort.toInt);assert(c.systemReport(BiosReport.MEMORY_TEST)==0)}
  c.updateProgressBar(BiosReport.REPORT_COMMIT,1);assert(java.util.Arrays.equals(r,c.systemReport));c.updateProgressBar(BiosReport.REPORT_FIRST,99);c.updateProgressBar(BiosReport.REPORT_COMMIT,2);assert(java.util.Arrays.equals(r,c.systemReport))
  val tag=new net.minecraft.nbt.NBTTagCompound;tag.setIntArray("ocppBiosReport",Array(1,1));s.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(BlockPos.ORIGIN,0,tag));assert(s.displayReport.length==BiosReport.SIZE&&s.displayReport(BiosReport.STATUS)==2&&s.displayReport(BiosReport.MEMORY_TEST)==0)
 }
 @Test def realContainerListenerCarriesCompleteServerSnapshotAndDiagnosticResults():Unit={val f=new Fixture
  f.available{f.screen.refreshCanvas(true);val server=new container.VMScreenPreview(f.screen,f.player);f.player.openContainer=server
   val client=new container.VMScreenPreview(new tileentity.VMScreenPreview,null);val listener=Mockito.mock(classOf[net.minecraft.inventory.IContainerListener])
   Mockito.doAnswer(new org.mockito.stubbing.Answer[AnyRef]{def answer(i:org.mockito.invocation.InvocationOnMock):AnyRef={client.updateProgressBar(i.getArguments()(1).asInstanceOf[java.lang.Integer].intValue(),i.getArguments()(2).asInstanceOf[java.lang.Integer].intValue());null}}).when(listener).sendWindowProperty(Matchers.any(classOf[net.minecraft.inventory.Container]),Matchers.anyInt(),Matchers.anyInt())
   server.addListener(listener);assert(client.biosOn&&client.systemReport(BiosReport.STATUS)==1);assert(client.systemReport(BiosReport.COLUMNS)==1&&client.systemReport(BiosReport.ROWS)==1)
   f.machine.bios.click(.5,.62,f.machine.bios.epoch(),true);assert(server.enchantItem(f.player,20));assert(client.systemReport(BiosReport.MEMORY_TEST)==1);assert(f.machine.posts==1)
  }
 }
}
