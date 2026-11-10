package li.cil.oc.server.component

import java.util.concurrent.ThreadLocalRandom
import li.cil.oc.{Settings, api}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{Component, EnvironmentHost, Message, Node, Visibility}
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.plusplus.SoundPrimitives
import li.cil.oc.server.PacketSender
import net.minecraft.util.{ResourceLocation, SoundCategory, SoundEvent}
import scala.collection.mutable
import scala.collection.JavaConverters._

object SoundCard {
  private val active=mutable.Set.empty[SoundCard]
  def acquire(card: SoundCard): Boolean = synchronized {
    if(active(card))true else if(active.size < Settings.get.soundMaxServerStreams){active+=card;true}else false
  }
  def release(card: SoundCard): Unit = synchronized {active-=card}
}

/** No filesystem is opened here. OpenOS reads virtual media under its normal
  * permissions, uploading bounded bytes with a visible source node for revocation. */
class SoundCard(val host: EnvironmentHost, val tier: Int) extends AbstractManagedEnvironment {
  override val node: Component = api.Network.newNode(this,Visibility.Neighbors).withComponent("sound_card",Visibility.Neighbors).create()
  override def canUpdate = true
  @volatile private var owner: String = null
  private var source: Node = null
  private var token=0L
  private var deadline=0L
  private var lastTick=Long.MinValue
  private var format=""
  @volatile private var upload: SoundPrimitives.Upload = null
  private var nextTone=0L
  private var encoded: Array[Byte] = null
  private var offset=0
  private var volume=1f
  private val tones=mutable.Queue.empty[(Long,Int,Int,Int,Float)]
  private val notes=mutable.Queue.empty[(Long,SoundPrimitives.Note)]
  private def time = host.world.getTotalWorldTime
  private def enabled(): Unit = if(!Settings.get.soundCardsEnabled)throw new IllegalStateException("sound cards disabled")
  private def authorized(context: Context): Unit = {
    enabled()
    if(context.node == null || node.network == null || context.node.network != node.network || !node.canBeSeenFrom(context.node))throw new IllegalStateException("computer cannot access sound card")
    if(owner!=null && owner!=context.node.address)throw new IllegalStateException("sound card busy for another computer")
    if(source!=null && (node.network.node(source.address)!=source || !source.asInstanceOf[Component].canBeSeenFrom(context.node))){stopRequest();throw new IllegalStateException("audio source revoked")}
  }
  private def claim(context: Context): Unit = {
    authorized(context)
    if(owner==null){if(!SoundCard.acquire(this))throw new IllegalStateException("server audio stream limit");owner=context.node.address;token=ThreadLocalRandom.current.nextLong()}
  }
  private def stopRequest(): Unit = {
    if(owner!=null && host.world!=null)PacketSender.sendCardAudio(host,node.address,token,3)
    owner=null;source=null;upload=null;encoded=null;format="";tones.clear();notes.clear();offset=0;deadline=0;nextTone=0;SoundCard.release(this)
  }
  @Callback(doc="function():number -- Sound card tier1..3. Builtin computer.beep remains independent.")
  def getTier(context: Context,args: Arguments): Array[AnyRef] = result(tier+1)
  @Callback(doc="function(wave:string,hz:number,seconds:number[,volume:number]):boolean -- Queue square/triangle/saw/noise;20..8000Hz,0.02..2s, one8-bit voice.")
  def tone(context: Context,args: Arguments): Array[AnyRef] = {
    val wave=Seq("square","triangle","saw","noise").indexOf(args.checkString(0));val hz=args.checkInteger(1);val seconds=args.checkDouble(2);val v=args.optDouble(3,1)
    require(wave>=0 && hz>=20 && hz<=8000 && !seconds.isNaN && seconds>=0.02 && seconds<=2 && !v.isNaN && v>=0 && v<=1,"tone bounds")
    claim(context)
    if(source!=null || upload!=null || encoded!=null || notes.nonEmpty)throw new IllegalStateException("another audio request active")
    if(tones.size>=Settings.get.soundMaxQueuedNotes)throw new IllegalStateException("tone queue bound")
    val when=math.max(time,nextTone)
    if(when-time+math.ceil(seconds*20)>200)throw new IllegalStateException("tone queue duration bound")
    tones.enqueue((when,wave,hz,math.round(seconds*1000).toInt,v.toFloat));nextTone=when+math.ceil(seconds*20).toLong;deadline=nextTone+2;result(true)
  }
  @Callback(doc="function(instrument:string,note:number[,volume:number]):boolean -- T2+: play a vanilla note-block one-shot, note0..24; natural sample decay.")
  def note(context: Context,args: Arguments): Array[AnyRef] = {
    require(tier>=1,"requires T2 sound card");val instrument=SoundPrimitives.instrument(args.checkString(0));val n=args.checkInteger(1);val v=args.optDouble(2,1)
    require(n>=0 && n<=24 && !v.isNaN && v>=0 && v<=1,"note bounds");claim(context)
    if(source!=null || upload!=null || encoded!=null || tones.nonEmpty || notes.size>=Settings.get.soundMaxQueuedNotes)throw new IllegalStateException("audio queue busy/full")
    notes.enqueue((time,new SoundPrimitives.Note(0,instrument,n,math.round(v*127).toInt)));deadline=time+2;result(true)
  }
  @Callback(doc="function(format:string,length:number,sourceAddress:string[,volume:number]):boolean -- Begin bounded virtual-file upload. T2 MIDI; T3 WAV/MP3/FLAC. Source must be a visible filesystem.")
  def begin(context: Context,args: Arguments): Array[AnyRef] = {
    val f=SoundPrimitives.format(args.checkString(0));require(if(f=="midi")tier>=1 else tier>=2,"sound card tier does not support format")
    val size=args.checkInteger(1);val address=args.checkString(2);val v=args.optDouble(3,1);require(address.length<=64 && !v.isNaN && v>=0 && v<=1,"audio source/volume bound")
    authorized(context);if(owner!=null)throw new IllegalStateException("stop current audio first")
    val fs=node.network.node(address)
    if(fs==null || fs.network!=node.network || !fs.isInstanceOf[Component] || !fs.asInstanceOf[Component].canBeSeenFrom(context.node) || !fs.host.isInstanceOf[FileSystem])throw new IllegalArgumentException("source is not a visible virtual filesystem")
    val pending=new SoundPrimitives.Upload(size,if(f=="midi")SoundPrimitives.MAX_MIDI else Settings.get.soundMaxEncodedBytes)
    claim(context);source=fs;format=f;upload=pending;volume=v.toFloat;deadline=time+300;result(true)
  }
  @Callback(doc="function(bytes:string):number -- Append at most8192 bytes; returns total received. Does not read a host path or URL.")
  def write(context: Context,args: Arguments): Array[AnyRef] = {
    authorized(context);if(upload==null)throw new IllegalStateException("no upload");upload.write(args.checkByteArray(0));result(upload.received())
  }
  @Callback(doc="function():boolean -- Finish upload and request client playback. Acceptance is not an acknowledgement of audible client playback.")
  def finish(context: Context,args: Arguments): Array[AnyRef] = {
    authorized(context);if(upload==null)throw new IllegalStateException("no upload");val bytes=upload.finish()
    if(format=="midi"){
      val parsed=SoundPrimitives.midi(bytes,Settings.get.soundMaxDurationSeconds).asScala
      parsed.foreach(n=>notes.enqueue((time+n.tick,n)));deadline=time+Settings.get.soundMaxDurationSeconds*20+2
    }else{
      encoded=bytes;offset=0;deadline=time+Settings.get.soundMaxDurationSeconds*20+300
      PacketSender.sendCardAudio(host,node.address,token,0,format,encoded.length,Settings.get.soundMaxDurationSeconds,volume)
    }
    upload=null;result(true)
  }
  @Callback(doc="function():boolean -- Cancel this computer's queued/streaming request. Already-triggered vanilla notes finish their natural decay.")
  def stop(context: Context,args: Arguments): Array[AnyRef] = {authorized(context);val had=owner!=null;stopRequest();result(had)}
  @Callback(direct=true,doc="function():string -- idle/uploading/requested. Client audibility is unacknowledged.")
  def status(context: Context,args: Arguments): Array[AnyRef] = result(if(owner==null)"idle" else if(upload!=null)"uploading" else "requested")
  override def update(): Unit = {
    if(host.world==null || time==lastTick)return;lastTick=time
    if(owner!=null && (!Settings.get.soundCardsEnabled || node.network==null || node.network.node(owner)==null || (source!=null && (node.network.node(source.address)!=source || !source.asInstanceOf[Component].canBeSeenFrom(node.network.node(owner)))) || time>deadline)){stopRequest();return}
    if(encoded!=null){val n=math.min(SoundPrimitives.CHUNK,encoded.length-offset);PacketSender.sendCardAudioData(host,node.address,token,offset,java.util.Arrays.copyOfRange(encoded,offset,offset+n));offset+=n
      if(offset==encoded.length){PacketSender.sendCardAudio(host,node.address,token,2);encoded=null;deadline=time+Settings.get.soundMaxDurationSeconds*20+20}}
    if(tones.nonEmpty && tones.front._1<=time){val(_,wave,hz,ms,v)=tones.dequeue();PacketSender.sendCardTone(host,node.address,token,wave,hz,ms,v)}
    var played=0
    while(notes.nonEmpty && notes.front._1<=time && played<8){val n=notes.dequeue()._2;val sound=SoundEvent.REGISTRY.getObject(new ResourceLocation("minecraft:block.note."+SoundPrimitives.INSTRUMENTS(n.instrument)))
      if(sound!=null)host.world.playSound(null,host.xPosition,host.yPosition,host.zPosition,sound,SoundCategory.BLOCKS,(n.velocity/127f*Settings.get.soundVolume).toFloat,math.pow(2,(n.note-12)/12.0).toFloat);played+=1}
  }
  override def onDisconnect(other: Node): Unit = {super.onDisconnect(other);if(other==node || (owner!=null && other.address==owner) || other==source)stopRequest()}
  override def onMessage(message: Message): Unit = {super.onMessage(message);if(owner!=null && message.source.address==owner && (message.name=="computer.stopped" || message.name=="computer.started"))stopRequest()}
}
