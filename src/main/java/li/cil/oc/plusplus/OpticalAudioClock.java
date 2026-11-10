package li.cil.oc.plusplus;
/** Monotonic software elapsed time, excluding sampled pause intervals. No device or native calls. */
public final class OpticalAudioClock {
 private boolean initialized,paused;private long last,elapsed;
 public long sample(long nanos,boolean pause){if(!initialized){initialized=true;last=nanos;paused=pause;return 0;}long delta=nanos-last;last=nanos;if(delta>0&&!paused&&!pause)elapsed=Math.min(elapsed,Long.MAX_VALUE-delta)+delta;paused=pause;return elapsed/1000;}
}
