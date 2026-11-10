package li.cil.oc.server

import li.cil.oc.Settings
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.{PacketType,SimplePacketBuilder}
import li.cil.oc.plusplus.OpticalSoundSchedule

/** Fixed event ID and loaded drive position only; no filenames/URLs or audio upload. */
object OpticalSounds {
  def sendMotor(host:EnvironmentHost,epoch:Long,generation:Long,sequence:Long,action:Int,age:Int):Unit={
    require(epoch>0&&generation>=0&&sequence>=0&&action>=0&&action<=li.cil.oc.plusplus.OpticalDrivePlayback.EJECT&&age>=0&&age<=li.cil.oc.plusplus.OpticalDrivePlayback.STARTUP_TICKS)
    if(host.world==null||host.world.isRemote)return
    if(action==li.cil.oc.plusplus.OpticalDrivePlayback.STOP||action==li.cil.oc.plusplus.OpticalDrivePlayback.CANCEL)
      li.cil.oc.plusplus.OpticalAudioDiagnostics.record(li.cil.oc.plusplus.OpticalAudioDiagnostics.ServerStop)
    val p=new SimplePacketBuilder(PacketType.OcppOpticalMotor)
    p.writeInt(host.world.provider.getDimension)
    p.writeInt(math.floor(host.xPosition).toInt);p.writeInt(math.floor(host.yPosition).toInt);p.writeInt(math.floor(host.zPosition).toInt)
    p.writeLong(epoch);p.writeLong(generation);p.writeLong(sequence);p.writeByte(action);p.writeInt(age)
    p.sendToNearbyPlayers(host.world,host.xPosition,host.yPosition,host.zPosition,Option(Settings.get.maxNetworkClientSoundPacketDistance))
  }
  def send(host: EnvironmentHost, action: Int): Unit = {
    require(action >= OpticalSoundSchedule.OPEN && action <= OpticalSoundSchedule.STOP)
    if(host.world == null || host.world.isRemote)return
    host match {case drive:li.cil.oc.common.tileentity.CDDrive=>drive.routeOptical(action);return;case _=>}
    if(action==OpticalSoundSchedule.READ)li.cil.oc.plusplus.OpticalAudioDiagnostics.record(li.cil.oc.plusplus.OpticalAudioDiagnostics.ServerRead)
    if(action==OpticalSoundSchedule.STOP)li.cil.oc.plusplus.OpticalAudioDiagnostics.record(li.cil.oc.plusplus.OpticalAudioDiagnostics.ServerStop)
    val p = new SimplePacketBuilder(PacketType.OcppOpticalSound)
    p.writeInt(host.world.provider.getDimension)
    p.writeInt(math.floor(host.xPosition).toInt);p.writeInt(math.floor(host.yPosition).toInt);p.writeInt(math.floor(host.zPosition).toInt)
    p.writeByte(action)
    p.sendToNearbyPlayers(host.world,host.xPosition,host.yPosition,host.zPosition,Option(Settings.get.maxNetworkClientSoundPacketDistance))
  }
}
