package li.cil.oc.plusplus;
/** Server tick simulation only. No client sound acknowledgements or persisted deadlines. */
public final class OpticalMediaReadiness {
 public static final int SPINUP_TICKS=51;
 private long generation=-1,started=-1,last=-1;
 private boolean ready;
 public boolean tick(long now,long mediaGeneration,boolean present,boolean spinning,boolean connected){
  if(now<0||now<last){cancel();last=now;return false;}
  last=now;
  if(!present||!spinning||!connected){cancel();last=now;return false;}
  if(generation!=mediaGeneration){generation=mediaGeneration;started=now;ready=false;}
  ready=now-started>=SPINUP_TICKS;
  return ready;
 }
 public boolean ready(){return ready;}
 public void cancel(){ready=false;generation=started=-1;}
}
