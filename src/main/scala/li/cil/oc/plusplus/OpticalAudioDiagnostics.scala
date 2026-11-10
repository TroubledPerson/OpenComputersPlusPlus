package li.cil.oc.plusplus

import java.util.concurrent.atomic.{AtomicInteger,AtomicLongArray}
import li.cil.oc.{OpenComputers,Settings}

/** Optional fixed-stage counters. No position, media identity, filename or audio data.
  * Saturating counters and at most 64 short reports per process; no packet changes. */
object OpticalAudioDiagnostics {
  val Seek=0;val Read=1;val NoMedia=2;val ServerRead=3;val ServerStop=4
  val Received=5;val NoWorld=6;val Muted=7;val NoDrive=8;val Played=9
  val Refreshed=10;val VoiceLimit=11;val Cancelled=12
  val Queued=13;val PacketRejected=14;val PacketDecoded=15
  private val names=Array("seek_authorized","read_nonempty","server_no_media","server_read_dispatch","server_stop_dispatch","client_received","client_no_world","client_muted","client_no_drive","client_play_requested","client_lease_refresh","client_voice_limit","client_cancelled","server_request_queued","client_packet_rejected","client_packet_decoded")
  private val counters=new AtomicLongArray(names.length)
  private val reports=new AtomicInteger(64)
  def enabled: Boolean = Settings.get!=null&&Settings.get.opticalAudioDiagnostics
  /** Read-only evidence; does not enable logging, reset counters or request playback. */
  def snapshot(): Array[Int] = if(!enabled)Array.fill(names.length)(0) else
    Array.tabulate(names.length)(i=>math.min(1000000L,counters.get(i)).toInt)
  def record(stage: Int): Unit = {
    val settings=Settings.get
    if(settings==null|| !settings.opticalAudioDiagnostics||stage<0||stage>=names.length)return
    var prior=counters.get(stage)
    while(prior<1000000L&& !counters.compareAndSet(stage,prior,prior+1))prior=counters.get(stage)
    val value=if(prior>=1000000L)1000000L else prior+1
    if(prior<1000000L&&(value&(value-1))==0){
      var left=reports.get
      while(left>0&& !reports.compareAndSet(left,left-1))left=reports.get
      if(left>0)OpenComputers.log.info("OC++ optical diagnostic "+names(stage)+"="+value+" (play request does not prove audible output)")
    }
  }
}
