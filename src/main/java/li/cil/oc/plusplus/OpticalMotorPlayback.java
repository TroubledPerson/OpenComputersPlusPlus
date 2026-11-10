package li.cil.oc.plusplus;

/** Client control state, not an audio device. A read-idle timeout never stops it. */
public final class OpticalMotorPlayback {
 public static final int WATCHDOG_TICKS=60,FADE_TICKS=10,MAX_DRIVES=8;
 private long epoch,sequence=-1,heartbeat,fadeAt;private int state=OpticalMotorState.CANCEL;
 public boolean accept(long expectedEpoch,long incomingEpoch,long incomingSequence,int action,long tick){
  if(expectedEpoch<=0||incomingEpoch!=expectedEpoch||incomingSequence<0||action<0||action>OpticalMotorState.CANCEL)return false;
  if(epoch!=expectedEpoch){epoch=expectedEpoch;sequence=-1;state=OpticalMotorState.CANCEL;}
  if(incomingSequence<=sequence)return false;sequence=incomingSequence;
  if(action==OpticalMotorState.ACTIVE){state=action;heartbeat=tick;}
  else if(action==OpticalMotorState.FADE){if(state==OpticalMotorState.ACTIVE&&alive(tick)){state=action;fadeAt=tick;}}
  else state=OpticalMotorState.CANCEL;
  return true;
 }
 public boolean alive(long tick){return state==OpticalMotorState.ACTIVE?tick>=heartbeat&&tick-heartbeat<WATCHDOG_TICKS:state==OpticalMotorState.FADE&&tick>=fadeAt&&tick-fadeAt<FADE_TICKS;}
 public float gain(long tick){if(!alive(tick))return 0;return state==OpticalMotorState.FADE?1f-(tick-fadeAt)/(float)FADE_TICKS:1f;}
 public float pitch(long tick){return state==OpticalMotorState.FADE?0.65f+0.35f*gain(tick):1f;}
 public long epoch(){return epoch;}
 public long sequence(){return sequence;}
}
