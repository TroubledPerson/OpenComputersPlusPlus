package li.cil.oc.plusplus.audio.codec;

import java.io.*;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import li.cil.oc.plusplus.audio.PcmSink;
import javazoom.jl.decoder.*;
import io.nayuki.flac.common.*;
import io.nayuki.flac.decode.*;

/** Uses reviewed libraries via bytes only; never their player/file/URL APIs. */
public final class Decoders {
    private Decoders(){}
    public static void decode(byte[] input,String format,int seconds,PcmSink sink,BooleanSupplier canceled)throws Exception {
        if(input==null||input.length<4||input.length>8*1024*1024||seconds<1||seconds>60)throw new IOException("audio input bound");
        Limit limit=new Limit(seconds,sink,canceled);
        if(format.equals("wav"))wav(input,limit);else if(format.equals("mp3"))mp3(input,limit);else if(format.equals("flac"))flac(input,limit);else throw new IOException("unsupported codec");
    }
    private static final class Limit {
        final PcmSink sink;final BooleanSupplier canceled;final long deadline;final int seconds;long frames;int rate,channels;
        Limit(int s,PcmSink p,BooleanSupplier c){seconds=s;sink=p;canceled=c;deadline=System.nanoTime()+Math.max(10,s+10)*1000000000L;}
        void check()throws IOException{if(Thread.currentThread().isInterrupted()||canceled.getAsBoolean()||System.nanoTime()>deadline)throw new IOException("audio canceled/deadline");}
        void emit(int r,int ch,byte[] pcm)throws Exception {
            check();if(r<8000||r>48000||ch<1||ch>2||pcm.length<1||pcm.length>8192||pcm.length%(ch*2)!=0)throw new IOException("PCM bound");
            if(rate!=0&&(rate!=r||channels!=ch))throw new IOException("changing audio format");rate=r;channels=ch;frames+=pcm.length/(ch*2);
            if(frames>(long)r*seconds)throw new IOException("decoded duration bound");sink.accept(r,ch,pcm);
        }
    }
    private static int le16(byte[] b,int p)throws IOException{if(p<0||p>b.length-2)throw new EOFException();return(b[p]&255)|((b[p+1]&255)<<8);}
    private static long le32(byte[] b,int p)throws IOException{return(le16(b,p)&65535L)|((long)le16(b,p+2)<<16);}
    private static boolean tag(byte[] b,int p,String s){if(p<0||p>b.length-s.length())return false;for(int i=0;i<s.length();i++)if(b[p+i]!=(byte)s.charAt(i))return false;return true;}
    private static void wav(byte[] b,Limit limit)throws Exception {
        if(!tag(b,0,"RIFF")||!tag(b,8,"WAVE")||le32(b,4)!=b.length-8)throw new IOException("invalid RIFF/WAVE");
        int rate=0,ch=0,bits=0,start=-1,length=0,metadata=0;
        for(int p=12;p<b.length;){limit.check();if(p>b.length-8)throw new EOFException();long size=le32(b,p+4);if(size>b.length-p-8)throw new EOFException();int n=(int)size,q=p+8;
            if(tag(b,p,"fmt ")){if(rate!=0||n!=16||le16(b,q)!=1)throw new IOException("only standard PCM WAV supported");ch=le16(b,q+2);rate=(int)le32(b,q+4);bits=le16(b,q+14);
                if(ch<1||ch>2||rate<8000||rate>48000||(bits!=8&&bits!=16)||le16(b,q+12)!=ch*bits/8||le32(b,q+8)!=(long)rate*ch*bits/8)throw new IOException("unsupported PCM WAV format");
            }else if(tag(b,p,"data")){if(start>=0)throw new IOException("duplicate WAV data");start=q;length=n;}
            else{metadata+=n;if(metadata>65536)throw new IOException("WAV metadata bound");}
            p=q+n+(n&1);if(p>b.length)throw new EOFException();
        }
        if(rate==0||start<0||length%(ch*bits/8)!=0)throw new IOException("missing/aligned WAV data");
        for(int p=start;p<start+length;){int samples=Math.min(4096,(start+length-p)/(bits/8));samples-=samples%ch;byte[] pcm=new byte[samples*2];
            for(int i=0;i<samples;i++){int value;if(bits==8){value=((b[p++]&255)-128)<<8;}else{value=le16(b,p);p+=2;}pcm[i*2]=(byte)value;pcm[i*2+1]=(byte)(value>>8);}
            limit.emit(rate,ch,pcm);
        }
    }
    private static void mp3(byte[] b,Limit limit)throws Exception {
        int offset=0;
        if(tag(b,0,"ID3")){if(b.length<10)throw new EOFException();int n=0;for(int i=6;i<10;i++){if(b[i]<0)throw new IOException("invalid ID3 size");n=(n<<7)|b[i];}
            if(n>65536||n>b.length-10)throw new IOException("ID3 size bound");offset=10+n;if((b[5]&16)!=0){offset+=10;if(offset>b.length)throw new EOFException();}}
        final int start=offset;
        InputStream bounded=new InputStream(){final ByteArrayInputStream bytes=new ByteArrayInputStream(b,start,b.length-start);public int read()throws IOException{limit.check();return bytes.read();}public int read(byte[] a,int o,int n)throws IOException{limit.check();return bytes.read(a,o,n);}};
        Bitstream stream=new Bitstream(bounded);Decoder decoder=new Decoder();int count=0;
        try{Header h;while((h=stream.readFrame())!=null){limit.check();SampleBuffer decoded=(SampleBuffer)decoder.decodeFrame(h,stream);short[] samples=decoded.getBuffer();int n=decoded.getBufferLength();
            if(n<1||n>4608)throw new IOException("MP3 frame bound");byte[] pcm=new byte[n*2];for(int i=0;i<n;i++){pcm[2*i]=(byte)samples[i];pcm[2*i+1]=(byte)(samples[i]>>8);}
            for(int p=0;p<pcm.length;p+=8192){int len=Math.min(8192,pcm.length-p);limit.emit(decoded.getSampleFrequency(),decoded.getChannelCount(),Arrays.copyOfRange(pcm,p,p+len));}
            stream.closeFrame();count++;
        }}finally{stream.close();}if(count==0)throw new IOException("no MP3 audio frames");
    }
    private static void flac(byte[] b,Limit limit)throws Exception {
        if(!tag(b,0,"fLaC"))throw new IOException("invalid FLAC header");int p=4,blocks=0,metadata=0;StreamInfo info=null;boolean last=false;
        while(!last){limit.check();if(p>b.length-4||++blocks>32)throw new IOException("FLAC metadata bound");int type=b[p]&127;last=b[p]<0;int n=((b[p+1]&255)<<16)|((b[p+2]&255)<<8)|(b[p+3]&255);p+=4;metadata+=n;
            if(n>b.length-p||metadata>65536)throw new IOException("FLAC metadata bound");if(blocks==1){if(type!=0||n!=34)throw new IOException("missing FLAC STREAMINFO");info=new StreamInfo(Arrays.copyOfRange(b,p,p+n));}
            else if(type==0)throw new IOException("duplicate FLAC STREAMINFO");p+=n;
        }
        if(info==null||info.numChannels<1||info.numChannels>2||info.sampleRate<8000||info.sampleRate>48000||(info.sampleDepth!=8&&info.sampleDepth!=16)||info.numSamples>(long)info.sampleRate*limit.seconds)throw new IOException("unsupported FLAC format/duration");
        final int end=b.length;
        AbstractFlacLowLevelInput input=new AbstractFlacLowLevelInput(){int pos;public long getLength(){return end;}public void seekTo(long n){if(n<0||n>end)throw new IllegalArgumentException();pos=(int)n;positionChanged(n);}protected int readUnderlying(byte[] dst,int off,int n)throws IOException{limit.check();if(pos==end)return -1;int take=Math.min(n,end-pos);System.arraycopy(b,pos,dst,off,take);pos+=take;return take;}};
        input.seekTo(p);io.nayuki.flac.decode.FrameDecoder decoder=new io.nayuki.flac.decode.FrameDecoder(input,info.sampleDepth);int[][] samples=new int[info.numChannels][65536];long frames=0;
        try{FrameInfo frame;while((frame=decoder.readFrame(samples,0))!=null){limit.check();info.checkFrame(frame);frames+=frame.blockSize;
            for(int at=0;at<frame.blockSize;){int count=Math.min(4096/info.numChannels,frame.blockSize-at);byte[] pcm=new byte[count*info.numChannels*2];int j=0;
                for(int i=0;i<count;i++)for(int ch=0;ch<info.numChannels;ch++){int value=samples[ch][at+i]<<(16-info.sampleDepth);pcm[j++]=(byte)value;pcm[j++]=(byte)(value>>8);}at+=count;limit.emit(info.sampleRate,info.numChannels,pcm);
            }
        }}finally{input.close();}if(frames==0||(info.numSamples!=0&&frames!=info.numSamples))throw new IOException("truncated/empty FLAC");
    }
}
