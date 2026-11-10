package li.cil.oc.plusplus.audio;
/** Parent-loader bridge: bounded signed16 little-endian PCM chunks only. */
public interface PcmSink { void accept(int rate,int channels,byte[] pcm)throws Exception; }
