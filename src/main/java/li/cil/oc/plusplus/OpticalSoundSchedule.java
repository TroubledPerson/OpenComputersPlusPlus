package li.cil.oc.plusplus;

import java.util.concurrent.atomic.AtomicBoolean;

/** One pending-read bit, no audio data or queue on the server. Tick-thread policy. */
public final class OpticalSoundSchedule {
    public static final int OPEN=0, CLOSE=1, READ=2, STOP=3, MAX_CLIENT_DRIVES=8;
    public static final String[] EVENTS={"ocpp_cd_open","ocpp_cd_close","ocpp_cd_seek"};
    public static final int[] MAX_TICKS={42,42,740};
    private final AtomicBoolean pending=new AtomicBoolean();
    private long lastTick=Long.MIN_VALUE,lastRead=Long.MIN_VALUE,lastPulse=Long.MIN_VALUE,blockedUntil=Long.MIN_VALUE;
    private boolean reading,deferredRead;
    public void requestRead(){pending.set(true);}
    /** Server tick diagnostic observation only; does not consume or alter requests. */
    public boolean hasPendingRead(){return pending.get()||deferredRead;}
    public int tray(long tick,boolean inserted){reset();lastTick=tick;blockedUntil=tick+40;return inserted?CLOSE:OPEN;}
    public int tick(long tick,boolean mediaPresent){
        if(tick<lastTick){boolean was=reading;reset();lastTick=tick;return was?STOP:-1;}
        lastTick=tick;
        if(!mediaPresent){boolean was=reading;reset();lastTick=tick;return was?STOP:-1;}
        boolean hadRead=pending.getAndSet(false);
        // A verified access during the tray clip must not disappear merely
        // because OpenOS finishes its short boot/read burst before tick40.
        // Retain one coalesced request, never a queue or invented constant I/O.
        if(tick<blockedUntil){if(hadRead)deferredRead=true;return -1;}
        if(deferredRead){deferredRead=false;hadRead=true;}
        if(hadRead)lastRead=tick;
        boolean recent=lastRead!=Long.MIN_VALUE && tick-lastRead<=12;
        if(!recent){if(reading){reading=false;return STOP;}return -1;}
        if(!reading || (hadRead && tick-lastPulse>=10)){reading=true;lastPulse=tick;return READ;}
        return -1;
    }
    public void reset(){pending.set(false);reading=deferredRead=false;lastTick=lastRead=lastPulse=blockedUntil=Long.MIN_VALUE;}
    public boolean isReading(){return reading;}
}
