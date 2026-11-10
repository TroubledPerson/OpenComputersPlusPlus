package li.cil.oc.plusplus.audio;

import java.io.IOException;
import java.util.concurrent.*;
import li.cil.oc.plusplus.SoundPrimitives;

/** One bounded client decode worker and four PCM chunks; no audio device calls. */
public final class PcmSession {
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(1),r->{Thread t=new Thread(r,"OC++-audio-decode");t.setDaemon(true);return t;});
    public static final class Chunk {public final int rate,channels;public final byte[] bytes;Chunk(int r,int c,byte[] b){rate=r;channels=c;bytes=b;}}
    public final String key;public final long token;public final float x,y,z,volume;
    public final ArrayBlockingQueue<Chunk> pcm=new ArrayBlockingQueue<Chunk>(4);
    private final SoundPrimitives.Upload upload;private final String format;private final int seconds;
    private final long deadline;private Future<?> task;private volatile boolean canceled,done;private volatile String error;
    public PcmSession(String key,long token,float x,float y,float z,float volume,String format,int length,int seconds){
        if(key==null||key.length()>64||seconds<1||seconds>60||Float.isNaN(volume)||volume<0||volume>1)throw new IllegalArgumentException("audio session bounds");
        this.key=key;this.token=token;this.x=x;this.y=y;this.z=z;this.volume=volume;this.format=format;this.seconds=seconds;
        upload=length==0?null:new SoundPrimitives.Upload(length,SoundPrimitives.MAX_ENCODED);deadline=System.nanoTime()+(seconds+30L)*1000000000L;
    }
    public synchronized void write(int offset,byte[] bytes){if(canceled||task!=null||upload==null||offset!=upload.received())throw new IllegalStateException("audio sequence");upload.write(bytes);}
    public synchronized void finish(){if(canceled||task!=null||upload==null)throw new IllegalStateException("audio state");byte[] bytes=upload.finish();start(()->AudioCodecs.decode(bytes,format,seconds,this::offer,this::canceled));}
    public synchronized void tone(int wave,int hz,int ms){if(upload!=null||task!=null)throw new IllegalStateException("audio state");start(()->{byte[] tone=SoundPrimitives.tone(wave,hz,ms);for(int p=0;p<tone.length;){int n=Math.min(4096,tone.length-p);byte[] pcm=new byte[n*2];for(int i=0;i<n;i++){int s=((tone[p+i]&255)-128)<<8;pcm[i*2]=(byte)s;pcm[i*2+1]=(byte)(s>>8);}p+=n;offer(22050,1,pcm);}});}
    private void start(Job job){task=WORKER.submit(()->{try{job.run();}catch(Exception e){if(!canceled)error=e.getClass().getSimpleName()+": "+e.getMessage();}finally{done=true;}});}
    private interface Job{void run()throws Exception;}
    private void offer(int rate,int channels,byte[] bytes)throws Exception{while(!canceled()){if(pcm.offer(new Chunk(rate,channels,bytes),100,TimeUnit.MILLISECONDS))return;}throw new IOException("audio canceled");}
    public boolean canceled(){return canceled||Thread.currentThread().isInterrupted()||System.nanoTime()>deadline;}
    public boolean done(){return done;}public String error(){return error;}
    public synchronized void cancel(){canceled=true;if(task!=null)task.cancel(true);pcm.clear();WORKER.purge();}
}
