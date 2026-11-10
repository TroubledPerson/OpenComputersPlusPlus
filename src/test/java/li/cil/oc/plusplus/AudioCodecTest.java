package li.cil.oc.plusplus;

import li.cil.oc.plusplus.audio.codec.Decoders;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/** Own tiny synthetic PCM/verbatim-FLAC/silent-MP3 fixtures, no external media. */
public class AudioCodecTest {
    public static byte[] wav(int bits,int channels,int rate,byte[] payload)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write("RIFF".getBytes("US-ASCII"));le(out,36+payload.length+(payload.length&1),4);out.write("WAVEfmt ".getBytes("US-ASCII"));le(out,16,4);le(out,1,2);le(out,channels,2);le(out,rate,4);le(out,rate*channels*bits/8,4);le(out,channels*bits/8,2);le(out,bits,2);out.write("data".getBytes("US-ASCII"));le(out,payload.length,4);out.write(payload);if((payload.length&1)!=0)out.write(0);return out.toByteArray();
    }
    static void le(OutputStream out,int value,int n)throws IOException{for(int i=0;i<n;i++)out.write(value>>>(8*i));}
    public static byte[] flac()throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{'f','L','a','C',(byte)128,0,0,34});
        byte[] info=new byte[34];info[1]=16;info[3]=16;long packed=(8000L<<44)|(15L<<36)|16;for(int i=0;i<8;i++)info[10+i]=(byte)(packed>>>(56-i*8));out.write(info);
        ByteArrayOutputStream frame=new ByteArrayOutputStream();frame.write(new byte[]{(byte)255,(byte)248,0x60,0x08,0,15});frame.write(crc(frame.toByteArray(),8,0x07));frame.write(2);frame.write(new byte[32]);int crc=crc(frame.toByteArray(),16,0x8005);frame.write(crc>>>8);frame.write(crc);out.write(frame.toByteArray());return out.toByteArray();
    }
    static int crc(byte[] b,int bits,int polynomial){int crc=0,mask=(1<<bits)-1;for(byte value:b){crc^=(value&255)<<(bits-8);for(int n=0;n<8;n++)crc=((crc<<1)^((crc&(1<<(bits-1)))!=0?polynomial:0))&mask;}return crc;}
    public static byte[] mp3(){byte[] b=new byte[417];b[0]=(byte)255;b[1]=(byte)251;b[2]=(byte)144;b[3]=(byte)192;return b;}
    private byte[] decode(byte[] bytes,String format,int expectedRate,int expectedChannels)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();Decoders.decode(bytes,format,1,(rate,channels,pcm)->{assertEquals(expectedRate,rate);assertEquals(expectedChannels,channels);assertTrue(pcm.length<=8192);out.write(pcm);},()->false);return out.toByteArray();
    }
    private void rejected(byte[] b,String f)throws Exception{try{Decoders.decode(b,f,1,(r,c,p)->{},()->false);fail("accepted malformed audio");}catch(Exception expected){}}
    @Test public void decodesRealPcm8AndPcm16WavWithoutJavaSoundSpi()throws Exception {
        assertArrayEquals(new byte[]{0,(byte)128,0,0,0,127},decode(wav(8,1,8000,new byte[]{0,(byte)128,(byte)255}),"wav",8000,1));
        byte[] pcm={1,2,3,4,5,6,7,8};assertArrayEquals(pcm,decode(wav(16,2,44100,pcm),"wav",44100,2));
    }
    @Test public void decodesCrcCheckedVerbatimFlacSamples()throws Exception {byte[] pcm=decode(flac(),"flac",8000,1);assertEquals(32,pcm.length);assertArrayEquals(new byte[32],pcm);}
    @Test public void decodesActualMpegLayer3SilentFrame()throws Exception {byte[] pcm=decode(mp3(),"mp3",44100,1);assertEquals(2304,pcm.length);assertArrayEquals(new byte[2304],pcm);}
    @Test public void rejectsTruncationWrongCrcAndOversizedId3BeforeDecode()throws Exception {
        byte[] f=flac();f[f.length-1]^=1;rejected(f,"flac");rejected(Arrays.copyOf(flac(),50),"flac");
        byte[] id3={'I','D','3',4,0,0,127,127,127,127};rejected(id3,"mp3");rejected(new byte[417],"mp3");
        byte[] w=wav(16,1,8000,new byte[16]);w[4]=0;rejected(w,"wav");rejected(wav(24,1,8000,new byte[24]),"wav");
    }
    @Test public void enforcesDecodedDurationFormatBoundsAndCancellation()throws Exception {
        rejected(wav(16,1,8000,new byte[16002]),"wav");rejected(wav(16,3,8000,new byte[12]),"wav");
        try{Decoders.decode(flac(),"flac",1,(r,c,p)->fail("emitted after cancellation"),()->true);fail();}catch(IOException expected){}
    }
}
