package li.cil.oc.plusplus;

/** Server-owned BIOS/menu state; never executes a VM or persists a running session. */
public final class BiosBootState {
    public enum State { OFF, BIOS, ERROR }
    public enum Dependency { MISSING, INCOMPATIBLE, OFFLINE_BIOS, ENGINE_READY }
    public enum Error { NONE, MISSING_DEPENDENCY, INCOMPATIBLE_DEPENDENCY }
    public enum Page { MAIN, LIBRARY, SYSTEM, DIAGNOSTICS, AUDIO }
    public enum Key { UP, DOWN, ENTER, BACK }
    public static final int POWER_COOLDOWN_TICKS=40;
    private State state=State.OFF;
    private Dependency dependency=Dependency.MISSING;
    private Error error=Error.NONE;
    private Page page=Page.MAIN;
    private int selected;
    private long epoch,lastPowerTick=-1;
    public static final class Transition {
        public final boolean accepted,beep;
        public final State state;
        private Transition(boolean a,boolean b,State s){accepted=a;beep=b;state=s;}
    }
    private static boolean valid(Dependency d){return d==Dependency.OFFLINE_BIOS||d==Dependency.ENGINE_READY;}
    private void resetMenu(){page=Page.MAIN;selected=0;}
    public synchronized Transition power(Dependency d,boolean authorized,long serverTick){
        if(d==null)throw new IllegalArgumentException("dependency state required");
        if(!authorized||serverTick<0||lastPowerTick>=0&&(serverTick<lastPowerTick||serverTick-lastPowerTick<POWER_COOLDOWN_TICKS))return new Transition(false,false,state);
        lastPowerTick=serverTick;dependency=d;epoch++;resetMenu();
        if(state==State.BIOS){state=State.OFF;error=Error.NONE;return new Transition(true,false,state);}
        if(!valid(d)){state=State.ERROR;error=d==Dependency.MISSING?Error.MISSING_DEPENDENCY:Error.INCOMPATIBLE_DEPENDENCY;return new Transition(true,true,state);}
        state=State.BIOS;error=Error.NONE;return new Transition(true,false,state);
    }
    /** Dependency recovery never auto-boots; loss invalidates input without tick beeps. */
    public synchronized void dependencyChanged(Dependency d){
        if(d==null)throw new IllegalArgumentException("dependency state required");
        if(dependency!=d){dependency=d;epoch++;
            if(state==State.BIOS&&!valid(d)){state=State.ERROR;error=d==Dependency.MISSING?Error.MISSING_DEPENDENCY:Error.INCOMPATIBLE_DEPENDENCY;resetMenu();}}
    }
    /** Join/split/orientation changes preserve the menu but invalidate old input. */
    public synchronized void layoutChanged(){epoch++;}
    public synchronized void shutdown(){state=State.OFF;error=Error.NONE;resetMenu();epoch++;}
    private boolean accepts(long inputEpoch,boolean authorized){return authorized&&state==State.BIOS&&inputEpoch==epoch;}
    public synchronized boolean key(Key key,long inputEpoch,boolean authorized){
        if(key==null||!accepts(inputEpoch,authorized))return false;
        if(key==Key.BACK){page=page==Page.AUDIO?Page.DIAGNOSTICS:Page.MAIN;return true;}
        if(page!=Page.MAIN)return false;
        if(key==Key.UP)selected=(selected+2)%3;
        else if(key==Key.DOWN)selected=(selected+1)%3;
        else if(key==Key.ENTER)page=Page.values()[selected+1];
        return true;
    }
    /** Normalized shared-canvas coordinates, independent of any guest resolution. */
    public synchronized boolean click(double x,double y,long inputEpoch,boolean authorized){
        if(!accepts(inputEpoch,authorized)||!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>1||y<0||y>1)return false;
        if(page!=Page.MAIN){if(x>=0.1&&x<=0.9&&y>=0.84&&y<=0.96){page=page==Page.AUDIO?Page.DIAGNOSTICS:Page.MAIN;return true;}return false;}
        if(x<0.1||x>0.9)return false;
        for(int row=0;row<3;row++){double top=0.25+row*0.16;if(y>=top&&y<top+0.12){selected=row;page=Page.values()[row+1];return true;}}
        return false;
    }
    public synchronized State state(){return state;}
    public synchronized Error error(){return error;}
    public synchronized Page page(){return page;}
    public synchronized int selection(){return selected;}
    public synchronized long epoch(){return epoch;}
    public synchronized boolean mayProject(){return state==State.BIOS;}
    public synchronized boolean engineReady(){return dependency==Dependency.ENGINE_READY;}
    public synchronized boolean openAudio(long inputEpoch,boolean authorized){if(!accepts(inputEpoch,authorized)||page!=Page.DIAGNOSTICS)return false;page=Page.AUDIO;return true;}
    public boolean mayExecuteGuest(){return false;}
    /** Fixed menu actions shared by focused and in-world pointer input. */
    public static int actionAt(int page,double x,double y){
        if(!Double.isFinite(x)||!Double.isFinite(y)||x<0.1||x>0.9||y<0||y>1)return -1;
        if(page==Page.MAIN.ordinal()){
            for(int row=0;row<3;row++){double top=0.25+row*0.16;if(y>=top&&y<top+0.12)return 10+row;}
        }else if(page>=1&&page<=4){
            if(y>=0.84&&y<=0.96)return 4;
            if(page==Page.DIAGNOSTICS.ordinal()){
                if(y>=0.20&&y<=0.30)return 20;
                if(y>=0.32&&y<=0.42)return 21;
                if(y>=0.45&&y<=0.65)return 0;
                if(y>=0.68&&y<=0.80)return 22;
            }
        }
        return -1;
    }
}
