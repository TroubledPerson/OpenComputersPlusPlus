package li.cil.oc.common.tileentity

import java.util.Arrays
import li.cil.oc.{Settings,api}
import li.cil.oc.api.detail.{Builder,NetworkAPI}
import li.cil.oc.api.network.{Component,ComponentConnector,Environment,Network,Node,Visibility}
import li.cil.oc.api.machine.{Machine,MachineHost}
import li.cil.oc.plusplus.OpticalMotorState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.junit.Test
import org.mockito.{Matchers,Mockito}
import scala.collection.mutable.ArrayBuffer

class OpticalMotorIntegrationTest {
 net.minecraft.init.Bootstrap.register()
 private def fixture(test:(Node,Component,World,Node,Machine,Machine)=>Unit):Unit={
  val drive=Mockito.mock(classOf[Component]);val medium=Mockito.mock(classOf[Component]);val world=Mockito.mock(classOf[World]);val network=Mockito.mock(classOf[Network])
  Mockito.when(drive.network()).thenReturn(network);Mockito.when(world.isBlockLoaded(Matchers.any(classOf[BlockPos]))).thenReturn(true)
  Mockito.when(drive.asInstanceOf[Component].canBeSeenFrom(Matchers.any(classOf[Node]))).thenReturn(true)
  def machine():(Node,Machine)={val n=Mockito.mock(classOf[Node]);val m=Mockito.mock(classOf[Machine]);val h=Mockito.mock(classOf[MachineHost]);Mockito.when(n.host()).thenReturn(m);Mockito.when(m.host()).thenReturn(h);Mockito.when(h.world()).thenReturn(world);Mockito.when(m.isRunning()).thenReturn(true);(n,m)}
  val a=machine();val b=machine();Mockito.when(drive.reachableNodes()).thenReturn(Arrays.asList(a._1,b._1));test(drive,medium,world,a._1,a._2,b._2)
 }
 @Test def mountedMediaNeedsAtLeastOneVisibleLoadedRunningComputer():Unit=fixture{(drive,medium,world,n,a,b)=>
  assert(OpticalMotorOwners.powered(drive,Some(medium),world));Mockito.when(a.isRunning()).thenReturn(false)
  assert(OpticalMotorOwners.powered(drive,Some(medium),world));Mockito.when(b.isRunning()).thenReturn(false)
  assert(!OpticalMotorOwners.powered(drive,Some(medium),world));assert(!OpticalMotorOwners.powered(drive,None,world))
 }
 @Test def invisibleUnloadedAndDisconnectedTopologiesDoNotSupplyPower():Unit=fixture{(drive,medium,world,n,a,b)=>
  Mockito.when(drive.asInstanceOf[Component].canBeSeenFrom(Matchers.any(classOf[Node]))).thenReturn(false);assert(!OpticalMotorOwners.powered(drive,Some(medium),world))
  Mockito.when(drive.asInstanceOf[Component].canBeSeenFrom(Matchers.any(classOf[Node]))).thenReturn(true);Mockito.when(world.isBlockLoaded(Matchers.any(classOf[BlockPos]))).thenReturn(false);assert(!OpticalMotorOwners.powered(drive,Some(medium),world))
  Mockito.when(drive.network()).thenReturn(null);assert(!OpticalMotorOwners.powered(drive,Some(medium),world))
 }
 @Test def oversizedTopologyFailsClosedWithinBound():Unit=fixture{(drive,medium,world,n,a,b)=>
  Mockito.when(drive.reachableNodes()).thenReturn(java.util.Collections.nCopies(257,n));assert(!OpticalMotorOwners.powered(drive,Some(medium),world))
  Mockito.verify(n,Mockito.atMost(256)).host()
 }
 @Test def actualDriveMotorPathWaitsForTrayAndKeepsReadSoundIndependent():Unit={
  val field=Settings.getClass.getDeclaredFields.find(_.getType==classOf[Settings]).get;field.setAccessible(true);val old=field.get(Settings);val previous=api.API.network
  val settings=Mockito.mock(classOf[Settings]);field.set(Settings,settings)
  val net=Mockito.mock(classOf[NetworkAPI]);val builder=Mockito.mock(classOf[Builder.NodeBuilder]);val components=Mockito.mock(classOf[Builder.ComponentBuilder]);val node=Mockito.mock(classOf[ComponentConnector]);val network=Mockito.mock(classOf[Network])
  Mockito.when(net.newNode(Matchers.any(classOf[Environment]),Matchers.eq(Visibility.Network))).thenReturn(builder);Mockito.when(builder.withComponent(Matchers.anyString())).thenReturn(components);Mockito.when(components.create()).thenReturn(node);Mockito.when(node.network()).thenReturn(network);api.API.network=net
  val world=Mockito.mock(classOf[World]);Mockito.when(world.getTotalWorldTime()).thenReturn(0L)
  class Harness extends CDDrive {
   val motorEvents=ArrayBuffer.empty[Int];val transientEvents=ArrayBuffer.empty[Int];val playbackEvents=ArrayBuffer.empty[Int];var running=true
   override def isServer:Boolean=true
   override protected def motorPowered():Boolean=running
   override protected def syncMediaPublication(ready:Boolean):Unit={}
   def unrelatedDisconnect():Unit=onDisconnect(Mockito.mock(classOf[Node]))
   override protected def emitMotor(a:Int):Unit={motorEvents+=a}
   override protected def emitOpticalSound(a:Int):Unit={transientEvents+=a}
   override protected def sendPlayback(a:Int):Unit={playbackEvents+=a}
   def powerOff():Unit={running=false;val message=Mockito.mock(classOf[li.cil.oc.api.network.Message]);Mockito.when(message.name()).thenReturn("computer.stopped");onMessage(message)}
   def insert():Unit=playInsertSound();def ejectDisc():Unit=playEjectSound();def tickMotor(t:Long):Unit=updateMotor(t,true)
  }
  try{val d=new Harness;d.setWorld(world)
   val empty=d.eject(null,Mockito.mock(classOf[li.cil.oc.api.machine.Arguments]));assert(empty(0)==java.lang.Boolean.FALSE)
   assert(d.motorEvents.isEmpty&&d.transientEvents.isEmpty&&d.playbackEvents.isEmpty)
   d.insert();assert(d.motorEvents.toList==List(OpticalMotorState.CANCEL));d.motorEvents.clear()
   d.tickMotor(39);assert(d.motorEvents.isEmpty);d.tickMotor(40);assert(d.motorEvents.toList==List(OpticalMotorState.ACTIVE));d.motorEvents.clear()
   d.routeOptical(li.cil.oc.plusplus.OpticalSoundSchedule.READ);d.routeOptical(li.cil.oc.plusplus.OpticalSoundSchedule.STOP);assert(d.playbackEvents.toList==List(li.cil.oc.plusplus.OpticalDrivePlayback.SEEK))
   for(t<-41L to 59L)d.tickMotor(t);assert(d.motorEvents.isEmpty);d.powerOff();d.unrelatedDisconnect();d.tickMotor(60);assert(d.motorEvents.toList==List(OpticalMotorState.FADE));d.powerOff();d.tickMotor(61);d.tickMotor(62);assert(d.motorEvents.toList==List(OpticalMotorState.FADE));d.motorEvents.clear()
   d.ejectDisc();assert(d.motorEvents.isEmpty);assert(d.transientEvents.toList==List(1))
   assert(d.playbackEvents.toList==List(li.cil.oc.plusplus.OpticalDrivePlayback.SEEK,li.cil.oc.plusplus.OpticalDrivePlayback.OPEN))
   val policy=new li.cil.oc.plusplus.OpticalDrivePlayback
   val sounds=ArrayBuffer.empty[li.cil.oc.plusplus.OpticalDrivePlayback.Sound]
   val sink=new li.cil.oc.plusplus.OpticalDrivePlayback.Sink {
    override def play(c:li.cil.oc.plusplus.OpticalDrivePlayback.Channel,s:li.cil.oc.plusplus.OpticalDrivePlayback.Sound,g:Float,p:Float,l:Boolean):Unit={sounds+=s}
    override def mix(c:li.cil.oc.plusplus.OpticalDrivePlayback.Channel,g:Float,p:Float):Unit={}
    override def stop(c:li.cil.oc.plusplus.OpticalDrivePlayback.Channel,r:li.cil.oc.plusplus.OpticalDrivePlayback.Reason):Unit={}
   }
   assert(policy.accept(7,7,1,0,d.playbackEvents.last,0,0,true,sink))
   assert(policy.accept(7,7,1,1,d.playbackEvents.last,0,1,true,sink))
   assert(sounds.toList==List(li.cil.oc.plusplus.OpticalDrivePlayback.Sound.OPEN))
  }finally{api.API.network=previous;field.set(Settings,old)}
 }
}
