import java.nio.file.{Files,Paths}
import li.cil.oc.{Settings,api}
import li.cil.oc.api.detail.NetworkAPI
import li.cil.oc.api.network.{Component,ComponentConnector,Environment,Node,Visibility}
import li.cil.oc.common.tileentity.CDDrive
import li.cil.oc.plusplus.{OpticalMotorState,OpticalDrivePlayback,OpticalFileSystem=>Media}
import li.cil.oc.api.fs.Mode
import net.minecraft.world.World
import org.junit.Test
import org.mockito.{Matchers,Mockito}
import scala.collection.mutable.ArrayBuffer

/** Production drive callbacks and actual network disconnection ordering; no Minecraft world runs. */
class OpticalShutdownReadinessTest {
 net.minecraft.init.Bootstrap.register()
 private def settings(body: =>Unit):Unit=ExtendedHardwareFixtures.withSettings {
  val old=api.API.network;val oldSide=li.cil.oc.server.network.Network.isServer
  li.cil.oc.server.network.Network.isServer=()=>true
  try body finally {api.API.network=old;li.cil.oc.server.network.Network.isServer=oldSide}
 }
 @Test def temporaryFilesystemRemovalBeforeStoppedDoesNotCancelMotor():Unit=settings {
  api.API.network=li.cil.oc.server.network.Network
  val world=Mockito.mock(classOf[World])
  class Drive extends CDDrive {
   var running=true;val actions=ArrayBuffer.empty[Int]
   override def isServer:Boolean=true
   override protected def motorPowered():Boolean=running
   override protected def syncMediaPublication(ready:Boolean):Unit={}
   override protected def emitMotor(a:Int):Unit={actions+=a}
   def tick(t:Long):Unit=updateMotor(t,true)
  }
  class Tmp extends Environment {
   val node=api.Network.newNode(this,Visibility.Network).create()
   def onConnect(n:Node):Unit={};def onDisconnect(n:Node):Unit={};def onMessage(m:li.cil.oc.api.network.Message):Unit={}
  }
  val d=new Drive;d.setWorld(world);api.Network.joinNewNetwork(d.node)
  val tmp=new Tmp;d.node.connect(tmp.node);d.tick(0);assert(d.actions.toList==List(OpticalMotorState.ACTIVE));d.actions.clear()
  // Machine.tryClose(): close sets Stopped, removes /tmp, reconnects it, then emits stopped.
  d.running=false;tmp.node.remove();d.tick(1)
  assert(d.actions.toList==List(OpticalMotorState.FADE))
  d.node.connect(tmp.node);tmp.node.sendToReachable("computer.stopped");d.tick(2)
  assert(d.actions.toList==List(OpticalMotorState.FADE))
  d.actions.clear();d.running=true;tmp.node.sendToReachable("computer.started");d.tick(3)
  assert(d.actions.toList==List(OpticalMotorState.ACTIVE));d.actions.clear();d.node.remove()
  assert(d.actions.contains(OpticalMotorState.CANCEL));assert(!d.actions.contains(OpticalMotorState.FADE))
  api.Network.joinNewNetwork(d.node);d.running=true;d.tick(10);d.actions.clear()
  val owner=Mockito.mock(classOf[li.cil.oc.api.machine.Machine])
  val ownerNode=api.Network.newNode(owner,Visibility.Network).create();d.node.connect(ownerNode)
  d.running=false;ownerNode.remove();d.tick(11)
  assert(d.actions.toList==List(OpticalMotorState.CANCEL))
 }
 @Test def publicationWaitsThenWithdrawalClosesHandlesAndPreservesDisc():Unit=settings {
  val net=Mockito.mock(classOf[NetworkAPI])
  val driveNode=Mockito.mock(classOf[ComponentConnector]);val fsNode=Mockito.mock(classOf[ComponentConnector])
  val network=Mockito.mock(classOf[li.cil.oc.api.network.Network])
  Mockito.when(driveNode.network()).thenReturn(network)
  val nb=Mockito.mock(classOf[li.cil.oc.api.detail.Builder.NodeBuilder])
  val cb=Mockito.mock(classOf[li.cil.oc.api.detail.Builder.ComponentBuilder])
  val fb=Mockito.mock(classOf[li.cil.oc.api.detail.Builder.ComponentBuilder])
  val fc=Mockito.mock(classOf[li.cil.oc.api.detail.Builder.ComponentConnectorBuilder])
  Mockito.when(net.newNode(Matchers.any(classOf[Environment]),Matchers.eq(Visibility.Network))).thenReturn(nb)
  Mockito.when(nb.withComponent(Matchers.anyString())).thenReturn(cb)
  Mockito.when(cb.create()).thenReturn(driveNode)
  Mockito.when(nb.withComponent("filesystem",Visibility.Neighbors)).thenReturn(fb)
  Mockito.when(fb.withConnector()).thenReturn(fc)
  Mockito.when(fc.create()).thenReturn(fsNode)
  api.API.network=net
  class Drive extends CDDrive {
   var running=true;val actions=ArrayBuffer.empty[Int];var trayAction = -1
   override def isServer:Boolean=true
   override protected def motorPowered():Boolean=running
   override protected def emitMotor(a:Int):Unit={actions+=a}
   override protected def sendPlayback(a:Int):Unit={trayAction=a}
   def tick(t:Long):Unit=updateMotor(t,filesystemNode.nonEmpty)
   def stopped():Unit={running=false;val m=Mockito.mock(classOf[li.cil.oc.api.network.Message]);Mockito.when(m.name()).thenReturn("computer.stopped");onMessage(m)}
   def removeMedium():Unit=playEjectSound()
   def started():Unit={running=true;val m=Mockito.mock(classOf[li.cil.oc.api.network.Message]);Mockito.when(m.name()).thenReturn("computer.started");onMessage(m)}
  }
  val d=new Drive;val world=Mockito.mock(classOf[World]);d.setWorld(world)
  val root=Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")),"ocpp-ready-")
  val media=new Media(root,true,32768L,0)
  try {
   val h=media.open("kept",Mode.Write);media.getHandle(h).write(Array[Byte](1,2,3));media.getHandle(h).close()
   val fs=new li.cil.oc.server.component.OpticalFileSystem(media,null,d);d.components(0)=Some(fs)
   val old=media.open("kept",Mode.Read)
   d.tick(0);assert(media.getHandle(old)==null)
   for(t<-1L to 50L)d.tick(t)
   Mockito.verify(driveNode,Mockito.never()).connect(fsNode)
   d.tick(51);Mockito.verify(driveNode).connect(fsNode)
   val handle=media.open("kept",Mode.Read);val buf=new Array[Byte](3);assert(media.getHandle(handle).read(buf)==3);assert(buf.sameElements(Array[Byte](1,2,3)))
   d.stopped();d.tick(52);assert(media.getHandle(handle)==null);assert(d.actions.last==OpticalMotorState.FADE)
   d.removeMedium();assert(d.trayAction==OpticalDrivePlayback.OPEN)
   media.reopen();assert(media.size("kept")==3);media.close()
   d.started();d.tick(80);d.removeMedium()
   assert(d.trayAction==OpticalDrivePlayback.EJECT)
   d.stopped();d.tick(82)
   val other=new Media(root,true,32768L,0)
   try {
    d.started();d.tick(100);d.tick(151)
    Mockito.when(world.getTotalWorldTime()).thenReturn(151L)
    assert(d.getReadyState(null,null)(0)=="unavailable")
    Mockito.verify(driveNode,Mockito.times(1)).connect(fsNode)
   }finally other.close()
   d.tick(152);Mockito.verify(driveNode,Mockito.times(1)).connect(fsNode) // no retry storm
   d.stopped();d.tick(153);d.started();d.tick(154);d.tick(205)
   Mockito.verify(driveNode,Mockito.times(2)).connect(fsNode)
   assert(media.size("kept")==3)

  }finally media.close()
 }
}
