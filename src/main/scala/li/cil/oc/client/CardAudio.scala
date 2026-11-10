package li.cil.oc.client

import java.io.DataInputStream
import li.cil.oc.{OpenComputers,Settings}
import li.cil.oc.plusplus.SoundPrimitives
import li.cil.oc.plusplus.audio.PcmSession
import net.minecraft.client.Minecraft
import net.minecraft.util.SoundCategory
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.BufferUtils
import org.lwjgl.openal.{AL,AL10}
import scala.collection.mutable

/** Client-thread OpenAL ownership; the one decoder worker only produces PCM.
  * One active voice per client, four queued8KiB PCM blocks and four AL buffers. */
object CardAudio {
  private var active: PcmSession = null
  private var source=0
  private var rate=0
  private var channels=0
  private val buffers=mutable.Set.empty[Int]
  private val free=mutable.Queue.empty[Int]
  MinecraftForge.EVENT_BUS.register(this)

  private def clear(): Unit = {
    if(active!=null)active.cancel();active=null
    if(AL.isCreated)try{if(source!=0){AL10.alSourceStop(source);AL10.alDeleteSources(source)};buffers.foreach(AL10.alDeleteBuffers)}catch{case _:Throwable=>()}
    source=0;rate=0;channels=0;buffers.clear();free.clear()
  }
  def receive(p: DataInputStream,key: String,token: Long,action: Int,x: Float,y: Float,z: Float): Unit = {
    if(!Settings.get.soundCardsEnabled || Settings.get.soundVolume<=0)return
    try {action match {
      case 0 =>
        val format=SoundPrimitives.format(p.readUTF());val length=p.readInt();val seconds=p.readUnsignedByte();val volume=p.readFloat()
        require(format!="midi" && length>0 && length<=Settings.get.soundMaxEncodedBytes && seconds>0 && seconds<=Settings.get.soundMaxDurationSeconds)
        if(active==null)active=new PcmSession(key,token,x,y,z,volume,format,length,seconds)
      case 1 =>
        val offset=p.readInt();val length=p.readUnsignedShort();require(length>0 && length<=8192)
        val bytes=new Array[Byte](length);p.readFully(bytes)
        if(active!=null && active.key==key && active.token==token)active.write(offset,bytes)
      case 2 => if(active!=null && active.key==key && active.token==token)active.finish()
      case 3 => if(active!=null && active.key==key && active.token==token)clear()
      case 4 =>
        val wave=p.readUnsignedByte();val hz=p.readUnsignedShort();val ms=p.readUnsignedShort();val volume=p.readFloat()
        require(wave<=3 && hz>=20 && hz<=8000 && ms>=20 && ms<=2000)
        if(active==null || active.key==key){clear();active=new PcmSession(key,token,x,y,z,volume,"tone",0,2);active.tone(wave,hz,ms)}
      case _ => throw new IllegalArgumentException("unknown audio packet")
    }} catch {case scala.util.control.NonFatal(e)=>clear();OpenComputers.log.warn("Rejected sound-card packet: "+e.getMessage)}
  }
  @SubscribeEvent def onWorldUnload(event: WorldEvent.Unload): Unit = if(event.getWorld.isRemote)clear()
  @SubscribeEvent def onTick(event: TickEvent.ClientTickEvent): Unit = {
    if(event.phase!=TickEvent.Phase.END || active==null)return
    val mc=Minecraft.getMinecraft
    if(mc.world==null || mc.player==null || active.canceled() || !AL.isCreated){clear();return}
    if(active.error()!=null){OpenComputers.log.warn("Sound-card decode failed: "+active.error());clear();return}
    try {
      if(source==0 && !active.pcm.isEmpty){source=AL10.alGenSources();for(_<-0 until 4){val b=AL10.alGenBuffers();buffers+=b;free.enqueue(b)}}
      if(source!=0){
        var processed=AL10.alGetSourcei(source,AL10.AL_BUFFERS_PROCESSED)
        while(processed>0){free.enqueue(AL10.alSourceUnqueueBuffers(source));processed-=1}
        while(free.nonEmpty && !active.pcm.isEmpty){val chunk=active.pcm.poll()
          if(rate==0){rate=chunk.rate;channels=chunk.channels}else require(rate==chunk.rate && channels==chunk.channels)
          val b=free.dequeue();val data=BufferUtils.createByteBuffer(chunk.bytes.length);data.put(chunk.bytes);data.flip()
          AL10.alBufferData(b,if(channels==1)AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16,data,rate);AL10.alSourceQueueBuffers(source,b)
        }
        val radius=math.max(1,Settings.get.beepRadius);val distance=mc.player.getDistance(active.x,active.y,active.z)
        val gain=(math.max(0,1-distance/radius)*active.volume*Settings.get.soundVolume*mc.gameSettings.getSoundLevel(SoundCategory.BLOCKS)*0.3).toFloat
        AL10.alSource3f(source,AL10.AL_POSITION,active.x,active.y,active.z);AL10.alSourcef(source,AL10.AL_GAIN,gain)
        AL10.alSourcef(source,AL10.AL_REFERENCE_DISTANCE,radius.toFloat);AL10.alSourcef(source,AL10.AL_MAX_DISTANCE,radius.toFloat)
        val queued=AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED)
        if(queued>0 && AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE)!=AL10.AL_PLAYING)AL10.alSourcePlay(source)
        if(AL10.alGetError()!=AL10.AL_NO_ERROR)throw new IllegalStateException("OpenAL sound-card error")
        if(active.done() && active.pcm.isEmpty && queued==0)clear()
      }else if(active.done() && active.pcm.isEmpty)clear()
    }catch{case scala.util.control.NonFatal(e)=>clear();OpenComputers.log.warn("Sound-card playback failed: "+e.getMessage)}
  }
}
