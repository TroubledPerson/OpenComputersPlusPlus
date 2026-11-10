package li.cil.oc.plusplus;
import java.util.concurrent.atomic.AtomicLong;

/** Server-owned motor state; read-click scheduling is deliberately independent. */
public final class OpticalMotorState {
 public static final int ACTIVE=0,FADE=1,CANCEL=2,HEARTBEAT_TICKS=20,OWNER_SCAN_LIMIT=256;
 private static final AtomicLong EPOCHS=new AtomicLong(System.currentTimeMillis()*4096L);
 public static long newEpoch(){return EPOCHS.incrementAndGet();}
 private boolean active;private long last=Long.MIN_VALUE,pulse=Long.MIN_VALUE,closedUntil=Long.MIN_VALUE,sequence,generation,startedAt;
 public int tray(long tick){active=false;last=tick;closedUntil=tick+40;return CANCEL;}
 public int cancel(){active=false;pulse=Long.MIN_VALUE;return CANCEL;}
 public int tick(long tick,boolean mounted,boolean powered,boolean connected){
  if(tick<last){active=false;last=tick;pulse=closedUntil=Long.MIN_VALUE;return CANCEL;}last=tick;
  if(!mounted||!connected){boolean was=active;active=false;return was?CANCEL:-1;}
  if(!powered){boolean was=active;active=false;return was?FADE:-1;}
  if(tick<closedUntil)return -1;
  if(!active||pulse==Long.MIN_VALUE||tick-pulse>=HEARTBEAT_TICKS){if(!active){if(generation==Long.MAX_VALUE)throw new IllegalStateException("motor generation exhausted");generation++;startedAt=tick;}active=true;pulse=tick;return ACTIVE;}
  return -1;
 }
 public long nextSequence(){if(sequence==Long.MAX_VALUE)throw new IllegalStateException("motor sequence exhausted");return ++sequence;}
 public boolean active(){return active;}
 public long generation(){return generation;}
 public int age(long tick){return (int)Math.max(0L,Math.min(OpticalDrivePlayback.STARTUP_TICKS,tick-startedAt));}
}
