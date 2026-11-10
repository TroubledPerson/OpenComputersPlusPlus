package li.cil.oc.plusplus;

/** Production playback policy. The injected sink owns the two native sound handles. */
public final class OpticalDrivePlayback {
 public static final int POWER=0,STOP=1,CANCEL=2,OPEN=3,CLOSE=4,SEEK=5,EJECT=6;
 public static final int SAMPLE_RATE=44100,STARTUP_FRAMES=110691,SEEK_FRAMES=5440,STOP_FRAMES=99622;
 public static int ticksForFrames(int frames){if(frames<=0)throw new IllegalArgumentException("frames");return (int)(((long)frames*20+SAMPLE_RATE-1)/SAMPLE_RATE);}
 public static final int STARTUP_TICKS=ticksForFrames(STARTUP_FRAMES),SEEK_TICKS=ticksForFrames(SEEK_FRAMES),STOP_TICKS=ticksForFrames(STOP_FRAMES),TRAY_TICKS=42,WATCHDOG_TICKS=60,FADE_TICKS=STOP_TICKS;
 public enum Channel { MOTOR, TRANSIENT }
 public enum Sound { SPINUP, LOOP, SEEK, OPEN, CLOSE, SPINDOWN }
 public enum Phase { OFF, STARTUP, RUNNING, FADING }
 public enum Reason { COMPLETE, REPLACED, POWER_OFF, EJECT, INSERT, DISCONNECT, UNLOAD, RELOAD, MUTED, WATCHDOG, CLOCK, STARTUP_TIMEOUT }
 public interface Sink {
  void play(Channel channel,Sound sound,float gain,float pitch,boolean loop);
  void mix(Channel channel,float gain,float pitch);
  void stop(Channel channel,Reason reason);
  /** Optional software monotonic audio clock, not a hardware completion callback. */
  default long audioTimeMicros(){return -1;}
  /** -1 unavailable, 0 not tracked as playing, 1 tracked as playing; not a hardware cursor. */
  default int motorPlaying(){return -1;}
 }
 public static final Sink SILENT=new Sink(){public void play(Channel c,Sound s,float g,float p,boolean l){}public void mix(Channel c,float g,float p){}public void stop(Channel c,Reason r){}};
 private long epoch,generation=-1,sequence=-1,startedGeneration=-1,clock=-1,authority,spinUntil,transientUntil,fadeAt;
 private boolean motorVoice,transientVoice,pendingRead,removalPlayed,startupObserved,stopCuePlayed;
 private long startupRequestedAudio=-1,startupAudioUntil=-1;
 public static final long STARTUP_STATUS_WAIT_MICROS=2000000;
 public static long startupMicros(){return ((long)STARTUP_FRAMES*1000000+SAMPLE_RATE-1)/SAMPLE_RATE;}
 private long transientAudioUntil=-1,mixAt=-1,duckUntil,lastAudio=-1;
 public static final long DUCK_ATTACK_MICROS=50000,DUCK_HOLD_MICROS=100000,DUCK_RELEASE_MICROS=150000;
 public static final float MOTOR_GAIN=.3f,DUCK_GAIN=.105f,TRAY_GAIN=.90f;
 private Sound transientSound;private Phase phase=Phase.OFF;
 private float gain,pitch=1f; // FADING is the retained phase name for the recorded stop cue, not a synthetic ramp.
 public static int legacyEvent(int action){switch(action){case OpticalSoundSchedule.OPEN:return OPEN;case OpticalSoundSchedule.CLOSE:return CLOSE;case OpticalSoundSchedule.READ:return SEEK;default:return -1;}}
 public boolean accept(long expectedEpoch,long incomingEpoch,long incomingGeneration,long incomingSequence,int action,int age,long now,boolean enabled,Sink sink){
  if(expectedEpoch<=0||incomingEpoch!=expectedEpoch||incomingGeneration<0||incomingSequence<0||action<POWER||action>EJECT||age<0||age>STARTUP_TICKS||now<0||((action==POWER||action==SEEK)&&incomingGeneration==0))return false;
  if(epoch!=expectedEpoch){reset(Reason.REPLACED,now,sink);epoch=expectedEpoch;generation=-1;sequence=-1;startedGeneration=-1;removalPlayed=stopCuePlayed=false;}
  if(incomingSequence<=sequence||incomingGeneration<generation)return false;
  if(clock>now){reset(Reason.CLOCK,now,sink);sequence=incomingSequence;return false;}
  if(incomingGeneration>generation){reset(Reason.REPLACED,now,sink);generation=incomingGeneration;removalPlayed=stopCuePlayed=false;}
  sequence=incomingSequence;clock=now;
  if(!enabled){reset(Reason.MUTED,now,sink);return true;}
  if(action==CANCEL){reset(Reason.DISCONNECT,now,sink);return true;}
  if(action==EJECT){
   if(removalPlayed)return true;removalPlayed=true;pendingRead=false;
   stopTransient(Reason.EJECT,sink);
   if(!(phase==Phase.FADING&&motorVoice&&stopCuePlayed)){
    stopMotor(Reason.EJECT,sink);phase=Phase.OFF;
    if(!stopCuePlayed){phase=Phase.FADING;fadeAt=now;playMotor(Sound.SPINDOWN,false,sink);stopCuePlayed=true;}
   }
   playTransient(Sound.OPEN,now,TRAY_TICKS,sink);return true;
  }
  if(action==OPEN){
   if(removalPlayed)return true;removalPlayed=true;pendingRead=false;
   // Server classified removal as already stopped. Retain an existing stop, never invent one.
   stopTransient(Reason.EJECT,sink);
   if(phase!=Phase.FADING){stopMotor(Reason.EJECT,sink);phase=Phase.OFF;}
   playTransient(Sound.OPEN,now,TRAY_TICKS,sink);return true;
  }
  if(action==CLOSE){reset(Reason.INSERT,now,sink);removalPlayed=stopCuePlayed=false;playTransient(Sound.CLOSE,now,TRAY_TICKS,sink);return true;}
  if(action==STOP){
   mix(now,sink);
   pendingRead=false; // A normal motor stop must not cut off an already authorized seek cue.
   if(motorVoice&&phase!=Phase.FADING){float stopGain=gain;stopMotor(Reason.REPLACED,sink);fadeAt=now;phase=Phase.FADING;gain=stopGain;pitch=1f;motorVoice=true;sink.play(Channel.MOTOR,Sound.SPINDOWN,gain,pitch,false);stopCuePlayed=true;}
   if(!motorVoice)phase=Phase.OFF;
   advance(now,sink);return true;
  }
  if(action==POWER){
   authority=now;
   if(phase==Phase.OFF||phase==Phase.FADING){
    boolean cold=startedGeneration!=generation&&age<STARTUP_TICKS;startedGeneration=generation;
    if(cold){phase=Phase.STARTUP;spinUntil=now+STARTUP_TICKS;startupRequestedAudio=sink.audioTimeMicros();startupAudioUntil=-1;startupObserved=false;playMotor(Sound.SPINUP,false,sink);}
    else beginLoop(sink);
   }
   advance(now,sink);return true;
  }
  // A seek cannot power a stopped drive; one pre-POWER request may wait for initialization.
  if(phase==Phase.STARTUP||phase==Phase.RUNNING){authority=now;advance(now,sink);pendingRead=true;drainRead(now,sink);mix(now,sink);}
  else if(phase==Phase.OFF&&startedGeneration!=generation)pendingRead=true;
  return true;
 }
 public void tick(long now,boolean enabled,Sink sink){
  if(now<0||now<clock){reset(Reason.CLOCK,Math.max(0,now),sink);return;}clock=now;
  if(!enabled){reset(Reason.MUTED,now,sink);return;}
  advance(now,sink);
 }
 private void advance(long now,Sink sink){
  if((phase==Phase.STARTUP||phase==Phase.RUNNING)&&now-authority>=WATCHDOG_TICKS){reset(Reason.WATCHDOG,now,sink);return;}
  finishTransient(now,sink);
  advanceStartup(now,sink);
  if(phase==Phase.FADING&&now-fadeAt>=FADE_TICKS){stopMotor(Reason.POWER_OFF,sink);phase=Phase.OFF;pendingRead=false;}
  drainRead(now,sink);mix(now,sink);
 }
 /** Native bookkeeping is qualified as a status signal, never a sample-completion callback. */
 private void advanceStartup(long now,Sink sink){
  if(phase!=Phase.STARTUP)return;
  long time=sink.audioTimeMicros();
  if(time<0){if(now>=spinUntil)beginLoop(sink);return;} // deterministic legacy/offline sink only
  if(startupRequestedAudio<0)startupRequestedAudio=time;
  int playing=sink.motorPlaying();
  if(playing<0){ // duration-aware software fallback, explicitly without native completion evidence
   if(startupAudioUntil<0)startupAudioUntil=after(startupRequestedAudio,startupMicros());
   if(time>=startupAudioUntil)beginLoop(sink);return;
  }
  if(!startupObserved){
   if(playing==1){startupObserved=true;startupAudioUntil=after(time,startupMicros());}
   else if(time>=after(startupRequestedAudio,STARTUP_STATUS_WAIT_MICROS))reset(Reason.STARTUP_TIMEOUT,now,sink);
   return;
  }
  if(time>=startupAudioUntil&&playing==0){beginLoop(sink);return;}
  if(time>=after(startupAudioUntil,STARTUP_STATUS_WAIT_MICROS))reset(Reason.STARTUP_TIMEOUT,now,sink);
 }
 private static long after(long time,long duration){return Math.min(time,Long.MAX_VALUE-duration)+duration;}
 private long mixTime(Sink sink){long t=sink.audioTimeMicros();return t>=0?t:Math.min(Math.max(0,clock),Long.MAX_VALUE/50000)*50000;}
 private void finishTransient(long now,Sink sink){
  if(!transientVoice)return;long audio=sink.audioTimeMicros();
  if(transientSound==Sound.SEEK&&transientAudioUntil>=0&&audio>=0){if(audio>=transientAudioUntil)stopTransient(Reason.COMPLETE,sink);}
  else if(now>=transientUntil)stopTransient(Reason.COMPLETE,sink);
 }
 /** Frame-polled software deadlines reduce the deliberate tick-rounding gap; device timing remains unobserved. */
 public void audio(Sink sink){long time=sink.audioTimeMicros();if(time<0||!hasVoices())return;if(lastAudio>=0&&time<lastAudio){reset(Reason.CLOCK,Math.max(0,clock),sink);return;}lastAudio=time;advanceStartup(clock,sink);finishTransient(clock,sink);drainRead(clock,sink);mix(clock,sink);}
 private void beginLoop(Sink sink){phase=Phase.RUNNING;playMotor(Sound.LOOP,true,sink);}
 private void playMotor(Sound sound,boolean loop,Sink sink){
  if(motorVoice)stopMotor(Reason.REPLACED,sink);gain=MOTOR_GAIN;pitch=1f;mixAt=mixTime(sink);motorVoice=true;sink.play(Channel.MOTOR,sound,gain,pitch,loop);
 }
 private void playTransient(Sound sound,long now,int duration,Sink sink){
  stopTransient(Reason.REPLACED,sink);transientSound=sound;transientVoice=true;transientUntil=now+duration;
  if(sound==Sound.SEEK){long audio=sink.audioTimeMicros();long cueMicros=((long)SEEK_FRAMES*1000000+SAMPLE_RATE-1)/SAMPLE_RATE;transientAudioUntil=audio>=0?after(audio,cueMicros):-1;long time=mixTime(sink);duckUntil=after(time,cueMicros+DUCK_HOLD_MICROS);mixAt=time;}
  sink.play(Channel.TRANSIENT,sound,sound==Sound.SEEK?0.3f:TRAY_GAIN,1f,false);
 }
 private void drainRead(long now,Sink sink){if(phase==Phase.RUNNING&&pendingRead&&!transientVoice){pendingRead=false;playTransient(Sound.SEEK,now,SEEK_TICKS,sink);}}
 private void mix(long now,Sink sink){
  if(!motorVoice)return;
  if(phase==Phase.FADING){pitch=1f; /* The accepted recording supplies its own natural slowdown envelope. */}
  else {long time=mixTime(sink);if(mixAt<0||time<mixAt)mixAt=time;float target=(transientVoice&&transientSound==Sound.SEEK)||time<duckUntil?DUCK_GAIN:MOTOR_GAIN;long elapsed=target==MOTOR_GAIN?Math.max(0,time-Math.max(mixAt,duckUntil)):time-mixAt;float rate=(MOTOR_GAIN-DUCK_GAIN)/(target<gain?(float)DUCK_ATTACK_MICROS:(float)DUCK_RELEASE_MICROS);float step=rate*elapsed;gain=target<gain?Math.max(target,gain-step):Math.min(target,gain+step);mixAt=time;pitch=1f;}
  sink.mix(Channel.MOTOR,gain,pitch);
 }
 private void stopMotor(Reason reason,Sink sink){if(motorVoice)sink.stop(Channel.MOTOR,reason);motorVoice=false;gain=0;}
 private void stopTransient(Reason reason,Sink sink){if(transientVoice)sink.stop(Channel.TRANSIENT,reason);transientVoice=false;transientSound=null;transientAudioUntil=-1;}
 public void reset(Reason reason,long now,Sink sink){
  stopMotor(reason,sink);stopTransient(reason,sink);phase=Phase.OFF;pendingRead=false;clock=now;mixAt=-1;duckUntil=0;lastAudio=-1;startupRequestedAudio=startupAudioUntil=-1;startupObserved=false;
 }
 public boolean hasVoices(){return motorVoice||transientVoice;}
 public boolean pendingRead(){return pendingRead;}
 public Phase phase(){return phase;}
 public long sequence(){return sequence;}
 public long generation(){return generation;}
 public float gain(){return gain;}
 public float pitch(){return pitch;}
}
