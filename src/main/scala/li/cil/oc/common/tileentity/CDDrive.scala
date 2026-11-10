package li.cil.oc.common.tileentity

import li.cil.oc.api
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{Component, Visibility}
import li.cil.oc.common.item.{Delegator, OpticalDisc}
import li.cil.oc.server.component.OpticalFileSystem
import net.minecraft.item.ItemStack
import li.cil.oc.plusplus.OpticalSoundSchedule
import li.cil.oc.plusplus.{OpticalMotorState,OpticalMotorPlayback}
import li.cil.oc.plusplus.OpticalDrivePlayback
import li.cil.oc.api.network.{Message,Node}
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.{Side,SideOnly}
import java.util.concurrent.atomic.AtomicBoolean

class CDDrive extends DiskDrive with traits.Tickable {
  private val soundSchedule = new OpticalSoundSchedule
  private val motor = new OpticalMotorState
  var motorEpoch: Long = OpticalMotorState.newEpoch()
  lazy val motorPlayback = new OpticalMotorPlayback // client state, never persisted
  lazy val opticalPlayback = new OpticalDrivePlayback // production coordinator, no native handles
  private val rescan = new AtomicBoolean(true)
  private val ownerLost = new AtomicBoolean(false)
  private val readiness = new li.cil.oc.plusplus.OpticalMediaReadiness
  private var nextScan=Long.MinValue
  private var powered=false
  private var unavailableGeneration = -1L
  protected def emitMotor(action:Int):Unit=sendPlayback(action)
  protected def sendPlayback(action:Int):Unit=li.cil.oc.server.OpticalSounds.sendMotor(this,motorEpoch,motor.generation(),motor.nextSequence(),action,motor.age(getWorld.getTotalWorldTime))
  private[oc] def routeOptical(action:Int):Unit={
    val event=OpticalDrivePlayback.legacyEvent(action)
    if(event==OpticalDrivePlayback.SEEK && motor.active()){
      li.cil.oc.plusplus.OpticalAudioDiagnostics.record(li.cil.oc.plusplus.OpticalAudioDiagnostics.ServerRead)
      sendPlayback(OpticalDrivePlayback.SEEK)
    }else if(event==OpticalDrivePlayback.OPEN||event==OpticalDrivePlayback.CLOSE)sendPlayback(event)
    // Read-idle STOP never cuts off a qualified seek cue.
  }
  protected def motorPowered():Boolean=OpticalMotorOwners.powered(node,filesystemNode,getWorld)
  override def onMessage(message:Message):Unit={
    super.onMessage(message)
    if(message.name=="computer.started"||message.name=="computer.stopped")rescan.set(true)
  }
  override def onConnect(other:Node):Unit={super.onConnect(other);rescan.set(true)}
  override def onDisconnect(other:Node):Unit={
    super.onDisconnect(other);rescan.set(true)
    if(other!=null && other.host().isInstanceOf[li.cil.oc.api.machine.Machine])ownerLost.set(true)
    // A departing /tmp or other component is not loss of the drive's own network.
    if(other==node&&getWorld!=null&&isServer){readiness.cancel();emitMotor(motor.cancel());syncMediaPublication(false)}
  }
  // Filesystem direct callbacks can run on a computer worker. Only enqueue one
  // bit there; world/network work is drained by the drive's server tick.
  def requestReadSound(): Unit = {
    li.cil.oc.plusplus.OpticalAudioDiagnostics.record(li.cil.oc.plusplus.OpticalAudioDiagnostics.Queued)
    soundSchedule.requestRead()
  }
  protected def emitOpticalSound(action: Int): Unit = li.cil.oc.server.OpticalSounds.send(this, action)
  override def updateEntity(): Unit = {
    super.updateEntity()
    if (getWorld != null && isServer) {
      val present=filesystemNode.nonEmpty
      if(!present&&soundSchedule.hasPendingRead())li.cil.oc.plusplus.OpticalAudioDiagnostics.record(li.cil.oc.plusplus.OpticalAudioDiagnostics.NoMedia)
      updateMotor(getWorld.getTotalWorldTime,present) // establish generation before any seek event
      val action = soundSchedule.tick(getWorld.getTotalWorldTime, present)
      if (action >= 0) emitOpticalSound(action)
    }
  }
  protected def updateMotor(now:Long,present:Boolean):Unit={
    if(rescan.getAndSet(false)||now>=nextScan||now<nextScan-20){powered=motorPowered();nextScan=now+20}
    if(!powered || !present)unavailableGeneration = -1L
    val lostOwner=ownerLost.getAndSet(false)
    val connected=node!=null && node.network!=null && !(lostOwner && !powered)
    val action=motor.tick(now,present,powered,connected)
    if(action>=0)emitMotor(action)
    syncMediaPublication(readiness.tick(now,motor.generation(),present,motor.active(),connected))
  }
  // Keep the environment and data; only its network publication/handles are withdrawn.
  override protected def connectItemNode(item:Node):Unit={
    if(!isServer || (readiness.ready() && unavailableGeneration!=motor.generation()))super.connectItemNode(item)
    else item match {case c:Component=>c.setVisibility(Visibility.None);case _=>}
  }
  protected def syncMediaPublication(ready:Boolean):Unit={
    components(0) match {
      case Some(fs:OpticalFileSystem) if ready =>
        if(unavailableGeneration!=motor.generation())try {
          fs.optical.reopen()
          fs.node.setVisibility(Visibility.Network)
          if(node!=null && node.network!=null && fs.node.network!=node.network)super.connectItemNode(fs.node)
        } catch {case _:java.io.IOException =>
          // Duplicate identity or inaccessible backing data: fail closed once per activation.
          unavailableGeneration=motor.generation()
          fs.node.setVisibility(Visibility.None)
          if(fs.node.network!=null)fs.node.remove()
          fs.optical.close()
        }
      case Some(fs:OpticalFileSystem) =>
        fs.node.setVisibility(Visibility.None)
        if(fs.node.network!=null)fs.node.remove()
        fs.optical.close() // closes outstanding handles, never deletes/seeds files
      case _=>
    }
  }
  @Callback(doc="function():string -- Returns empty, off, starting, unavailable or ready; server-simulated readiness, independent of audio.")
  def getReadyState(context:Context,args:Arguments):Array[AnyRef]={
    if(getWorld!=null&&isServer)updateMotor(getWorld.getTotalWorldTime,filesystemNode.nonEmpty)
    result(if(filesystemNode.isEmpty)"empty" else if(!powered)"off" else if(unavailableGeneration==motor.generation())"unavailable" else if(readiness.ready())"ready" else "starting")
  }
  override def dispose(): Unit = {
    readiness.cancel();syncMediaPublication(false)
    soundSchedule.reset()
    if(getWorld!=null&&isServer)emitMotor(motor.cancel())
    if (getWorld != null && isServer) emitOpticalSound(OpticalSoundSchedule.STOP)
    super.dispose()
  }
  override def getDeviceInfo: java.util.Map[String, String] = {
    val info = new java.util.HashMap[String, String](super.getDeviceInfo)
    info.put(li.cil.oc.api.driver.DeviceInfo.DeviceAttribute.Description, "Virtual CD-R/CD-RW drive")
    info.put(li.cil.oc.api.driver.DeviceInfo.DeviceAttribute.Product, "OC++ Optical 32")
    info
  }
  override val node: Component = api.Network.newNode(this, Visibility.Network).withComponent("cd_drive").create()
  override def isItemValidForSlot(slot: Int, stack: ItemStack): Boolean =
    slot == 0 && Delegator.subItem(stack).exists(_.isInstanceOf[OpticalDisc])
  override protected def playInsertSound(): Unit =
    if (getWorld != null && isServer) {unavailableGeneration = -1L;readiness.cancel();syncMediaPublication(false);emitMotor(motor.tray(getWorld.getTotalWorldTime));rescan.set(true);emitOpticalSound(soundSchedule.tray(getWorld.getTotalWorldTime, true))}
  override protected def playEjectSound(): Unit =
    if (getWorld != null && isServer) {val spinning=motor.active();motor.cancel();readiness.cancel();syncMediaPublication(false);rescan.set(true);soundSchedule.tray(getWorld.getTotalWorldTime, false);sendPlayback(if(spinning)OpticalDrivePlayback.EJECT else OpticalDrivePlayback.OPEN)} // server classifies actual prior motor state

  @SideOnly(Side.CLIENT) override def readFromNBTForClient(nbt:NBTTagCompound):Unit={
    super.readFromNBTForClient(nbt);motorEpoch=math.max(0L,nbt.getLong("ocppMotorEpoch"))
  }
  override def writeToNBTForClient(nbt:NBTTagCompound):Unit={super.writeToNBTForClient(nbt);nbt.setLong("ocppMotorEpoch",motorEpoch)}

  @Callback(value = "finalize", doc = "function():boolean -- Permanently finalize the inserted CD-R recording session. Closes its handles. CD-RW cannot finalize.")
  def finalizeRecording(context: Context, args: Arguments): Array[AnyRef] = components(0) match {
    case Some(fs: OpticalFileSystem) =>
      val changed = fs.optical.finalizeRecording()
      saveComponents()
      markDirty()
      result(changed)
    case _ => result(Unit, "drive is empty")
  }

  @Callback(doc = "function():boolean -- Returns whether inserted media is finalized; false for CD-RW.")
  def isFinalized(context: Context, args: Arguments): Array[AnyRef] = components(0) match {
    case Some(fs: OpticalFileSystem) => result(fs.optical.isReadOnly)
    case _ => result(Unit, "drive is empty")
  }
}

/** Loaded-network metadata only; a mounted disc alone does not supply power. */
private[oc] object OpticalMotorOwners {
  def powered(drive:Node,medium:Option[Node],world:net.minecraft.world.World):Boolean={
    if(drive==null||drive.network==null||world==null||world.isRemote||medium.isEmpty)return false
    val nodes=drive.reachableNodes().iterator();var count=0;var running=false
    while(nodes.hasNext && count<OpticalMotorState.OWNER_SCAN_LIMIT){val n=nodes.next();count+=1
      n.host() match {
        case machine:li.cil.oc.api.machine.Machine if machine.isRunning && machine.host()!=null && machine.host().world()==world =>
          val h=machine.host();val p=new net.minecraft.util.math.BlockPos(h.xPosition(),h.yPosition(),h.zPosition())
          if(world.isBlockLoaded(p)&&(drive match {case c:Component=>c.canBeSeenFrom(n);case _=>false}))running=true
        case _=>
      }
    }
    if(nodes.hasNext)false else running // fail closed on an oversized topology
  }
}
