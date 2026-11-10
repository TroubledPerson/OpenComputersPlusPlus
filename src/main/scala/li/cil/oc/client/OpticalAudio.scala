package li.cil.oc.client

import li.cil.oc.Settings
import li.cil.oc.common.tileentity.CDDrive
import li.cil.oc.plusplus.{OpticalDrivePlayback => Playback, OpticalMotorVoices, OpticalAudioDiagnostics => Diag}
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.MovingSound
import net.minecraft.util.{ResourceLocation,SoundCategory,SoundEvent}
import net.minecraft.util.math.BlockPos
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.client.event.sound.SoundLoadEvent
import net.minecraftforge.event.world.{WorldEvent,ChunkEvent}
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import scala.collection.mutable

/** Native adapter only. All ordering, lifetime and cancellation policy is in Playback. */
object OpticalAudio {
  private var tick=0L
  private val audioClock=new li.cil.oc.plusplus.OpticalAudioClock
  private def audioTimeMicros:Long=audioClock.sample(System.nanoTime(),Minecraft.getMinecraft.isGamePaused)
  private val entries=mutable.Map.empty[BlockPos,Entry]
  private val voices=new OpticalMotorVoices
  MinecraftForge.EVENT_BUS.register(this)

  private def resource(sound:Playback.Sound):String=sound match {
    case Playback.Sound.SPINUP=>"ocpp_cd_spinup"
    case Playback.Sound.LOOP=>"ocpp_cd_motor"
    case Playback.Sound.SEEK=>"ocpp_cd_seek"
    case Playback.Sound.OPEN=>"ocpp_cd_open"
    case Playback.Sound.CLOSE=>"ocpp_cd_close"
    case Playback.Sound.SPINDOWN=>"ocpp_cd_stop"
  }
  private def enabled:Boolean={
    val mc=Minecraft.getMinecraft
    Settings.get.soundVolume>0 && mc.gameSettings.getSoundLevel(SoundCategory.MASTER)>0 && mc.gameSettings.getSoundLevel(SoundCategory.BLOCKS)>0
  }
  private class Effect(pos:BlockPos,sound:Playback.Sound,initialGain:Float,initialPitch:Float,loop:Boolean)
    extends MovingSound(new SoundEvent(new ResourceLocation(Settings.resourceDomain,resource(sound))),SoundCategory.BLOCKS){
    private var level=initialGain;private var frequency=initialPitch
    xPosF=pos.getX+0.5f;yPosF=pos.getY+0.5f;zPosF=pos.getZ+0.5f
    repeat=loop
    def change(gain:Float,p:Float):Unit={level=gain;frequency=p;update()}
    def cancel():Unit={donePlaying=true}
    override def update():Unit={volume=(Settings.get.soundVolume*level) min 1f;pitch=frequency}
    update()
  }
  private class Entry(val pos:BlockPos,val drive:CDDrive) extends Playback.Sink {
    override def audioTimeMicros():Long=OpticalAudio.audioTimeMicros
    // SoundManager includes queue/cleanup bookkeeping; this is not a hardware completion cursor.
    override def motorPlaying():Int=effects(Playback.Channel.MOTOR.ordinal()).fold(0)(effect=>if(Minecraft.getMinecraft.getSoundHandler.isSoundPlaying(effect))1 else 0)
    val epoch=drive.motorEpoch
    val playback=drive.opticalPlayback
    private val effects=Array.fill[Option[Effect]](2)(None)
    override def play(channel:Playback.Channel,sound:Playback.Sound,gain:Float,pitch:Float,loop:Boolean):Unit={
      stop(channel,Playback.Reason.REPLACED)
      val effect=new Effect(pos,sound,gain,pitch,loop);effects(channel.ordinal())=Some(effect)
      Minecraft.getMinecraft.getSoundHandler.playSound(effect)
      if(sound==Playback.Sound.SEEK)Diag.record(Diag.Played)
    }
    override def mix(channel:Playback.Channel,gain:Float,pitch:Float):Unit=effects(channel.ordinal()).foreach(_.change(gain,pitch))
    override def stop(channel:Playback.Channel,reason:Playback.Reason):Unit={
      effects(channel.ordinal()).foreach { effect=>
        effect.cancel();Minecraft.getMinecraft.getSoundHandler.stopSound(effect)
        if(channel==Playback.Channel.TRANSIENT && reason!=Playback.Reason.COMPLETE)Diag.record(Diag.Cancelled)
      }
      effects(channel.ordinal())=None
    }
    def destroy(reason:Playback.Reason):Unit={
      playback.reset(reason,tick,this)
      Playback.Channel.values().foreach(c=>stop(c,reason))
    }
  }
  private def remove(pos:BlockPos,reason:Playback.Reason):Unit={
    entries.remove(pos).foreach(_.destroy(reason));voices.release(pos)
  }
  private def clear(reason:Playback.Reason):Unit={entries.keys.toList.foreach(p=>remove(p,reason));voices.clear()}
  def receiveMotor(pos:BlockPos,epoch:Long,generation:Long,sequence:Long,action:Int,age:Int):Unit={
    val mc=Minecraft.getMinecraft
    if(action==Playback.SEEK)Diag.record(Diag.Received)
    if(mc.world==null||mc.player==null){if(action==Playback.SEEK)Diag.record(Diag.NoWorld);return}
    if(!mc.world.isBlockLoaded(pos)){if(action==Playback.SEEK)Diag.record(Diag.NoDrive);return}
    mc.world.getTileEntity(pos) match {
      case drive:CDDrive=>
        entries.get(pos).filter(e=>(e.drive ne drive)||e.epoch!=drive.motorEpoch).foreach(_=>remove(pos,Playback.Reason.REPLACED))
        val audible=enabled && voices.admit(pos)
        val entry=entries.get(pos).orElse{
          if(audible){val e=new Entry(pos,drive);entries(pos)=e;Some(e)}else None
        }
        val sink:Playback.Sink=entry.getOrElse(Playback.SILENT)
        val accepted=drive.opticalPlayback.accept(drive.motorEpoch,epoch,generation,sequence,action,age,tick,audible,sink)
        if(!accepted)Diag.record(Diag.PacketRejected)
        if(!audible&&action==Playback.SEEK)Diag.record(if(enabled)Diag.VoiceLimit else Diag.Muted)
        if(!drive.opticalPlayback.hasVoices()){entries.remove(pos);voices.release(pos)}
      case _=>if(action==Playback.SEEK)Diag.record(Diag.NoDrive)
    }
  }
  /** Older unscoped audio packets cannot start a cue in this versioned protocol. */
  def receive(pos:BlockPos,action:Int):Unit=Diag.record(Diag.PacketRejected)
  @SubscribeEvent def onWorldUnload(e:WorldEvent.Unload):Unit=if(e.getWorld.isRemote)clear(Playback.Reason.UNLOAD)
  @SubscribeEvent def onSoundReload(e:SoundLoadEvent):Unit=clear(Playback.Reason.RELOAD)
  @SubscribeEvent def onChunkUnload(e:ChunkEvent.Unload):Unit=if(e.getWorld.isRemote){
    entries.keys.filter(p=>(p.getX>>4)==e.getChunk.x && (p.getZ>>4)==e.getChunk.z).toList.foreach(p=>remove(p,Playback.Reason.UNLOAD))
  }
  @SubscribeEvent def onAudioFrame(e:TickEvent.RenderTickEvent):Unit={
    if(e.phase!=TickEvent.Phase.END)return
    val mc=Minecraft.getMinecraft
    audioTimeMicros
    if(mc.world==null||mc.player==null){clear(Playback.Reason.DISCONNECT);return}
    if(!enabled){clear(Playback.Reason.MUTED);return}
    if(mc.isGamePaused)return
    entries.toList.foreach {case(pos,entry)=>
      val valid=mc.world.isBlockLoaded(pos) && (mc.world.getTileEntity(pos) eq entry.drive) && entry.epoch==entry.drive.motorEpoch
      if(!valid)remove(pos,Playback.Reason.UNLOAD) else entry.playback.audio(entry)
    }
  }
  @SubscribeEvent def onTick(e:TickEvent.ClientTickEvent):Unit={
    if(e.phase!=TickEvent.Phase.END)return
    val mc=Minecraft.getMinecraft
    if(mc.world==null||mc.player==null){clear(Playback.Reason.DISCONNECT);return}
    if(!enabled){clear(Playback.Reason.MUTED);return}
    audioTimeMicros // sample pause state even when logical playback is paused
    if(mc.isGamePaused)return
    tick+=1
    entries.toList.foreach { case(pos,entry)=>
      val valid=mc.world.isBlockLoaded(pos) && (mc.world.getTileEntity(pos) eq entry.drive) && entry.epoch==entry.drive.motorEpoch
      if(!valid)remove(pos,Playback.Reason.UNLOAD)
      else {entry.playback.tick(tick,true,entry);if(!entry.playback.hasVoices()){entries.remove(pos);voices.release(pos)}}
    }
  }
}
