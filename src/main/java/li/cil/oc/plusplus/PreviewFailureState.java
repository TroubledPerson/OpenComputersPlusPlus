package li.cil.oc.plusplus;

/** Transient simulation only: no machine, host service or persisted running state. */
public final class PreviewFailureState {
    public static final int DURATION_TICKS=40;
    private int remaining;
    public boolean tryStart(){if(remaining!=0)return false;remaining=DURATION_TICKS;return true;}
    public boolean tick(){if(remaining==0)return false;return --remaining==0;}
    public boolean active(){return remaining>0;}
    public int remaining(){return remaining;}
    public void synchronize(int ticks){remaining=Math.max(0,Math.min(DURATION_TICKS,ticks));}
    public void clear(){remaining=0;}
}
