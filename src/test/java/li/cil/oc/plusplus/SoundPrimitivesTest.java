package li.cil.oc.plusplus;

import li.cil.oc.plusplus.audio.PcmSession;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class SoundPrimitivesTest {
    static byte[] midi(byte[] track,int format,int ppq)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x4d546864);out.writeInt(6);out.writeShort(format);out.writeShort(1);out.writeShort(ppq);out.writeInt(0x4d54726b);out.writeInt(track.length);out.write(track);return bytes.toByteArray();}
    private void bad(byte[] b)throws Exception{try{SoundPrimitives.midi(b,30);fail("bad MIDI accepted");}catch(IOException expected){}}
    @Test public void generatesFourBoundedQuantizedWaveformsAndRejectsInvalidRequests(){
        Set<Integer> hashes=new HashSet<Integer>();for(int shape=0;shape<4;shape++){byte[] pcm=SoundPrimitives.tone(shape,440,100);assertEquals(2205,pcm.length);for(byte b:pcm)assertTrue((b&255)>=38&&(b&255)<=218);hashes.add(Arrays.hashCode(pcm));}assertEquals(4,hashes.size());
        try{SoundPrimitives.tone(4,440,100);fail();}catch(IllegalArgumentException expected){}
        try{SoundPrimitives.tone(0,0,100);fail();}catch(IllegalArgumentException expected){}
        try{SoundPrimitives.tone(0,440,2001);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void parsesTempoRunningStatusAndNoteBlockFallback()throws Exception {
        byte[] track={0,(byte)0xc0,0,0,(byte)0x90,60,100,96,64,90,0,(byte)0xff,47,0};
        List<SoundPrimitives.Note> notes=SoundPrimitives.midi(midi(track,0,96),30);assertEquals(2,notes.size());assertEquals(0,notes.get(0).tick);assertEquals(10,notes.get(1).tick);assertEquals(0,notes.get(0).instrument);assertEquals(6,notes.get(0).note);
        byte[] tempo={0,(byte)0xff,81,3,3,(byte)208,(byte)144,96,(byte)0x90,60,100,0,(byte)0xff,47,0};
        assertEquals(5,SoundPrimitives.midi(midi(tempo,1,96),30).get(0).tick);
    }
    @Test public void rejectsMalformedMidiSmpteOversizeDensityAndDuration()throws Exception {
        bad(new byte[4]);bad(new byte[32769]);bad(midi(new byte[]{0,(byte)255,47,0},0,0x8000));
        bad(midi(new byte[]{0,60,100},0,96));bad(midi(new byte[]{(byte)128,(byte)128,(byte)128,(byte)128,0},0,96));
        ByteArrayOutputStream track=new ByteArrayOutputStream();for(int n=0;n<9;n++)track.write(new byte[]{0,(byte)144,60,100});track.write(new byte[]{0,(byte)255,47,0});bad(midi(track.toByteArray(),0,96));
        bad(midi(new byte[]{(byte)0x83,0,(byte)144,60,100,0,(byte)255,47,0},0,1));
    }
    @Test public void uploadIsContiguousBoundedAndAtomicOnFailure(){
        SoundPrimitives.Upload u=new SoundPrimitives.Upload(10,10);u.write(new byte[6]);assertEquals(6,u.received());
        try{u.write(new byte[5]);fail();}catch(IllegalArgumentException expected){}assertEquals(6,u.received());
        try{u.finish();fail();}catch(IllegalStateException expected){}u.write(new byte[4]);assertEquals(10,u.finish().length);
    }
    @Test public void workerQueueStaysBoundedAndCancellationReleasesIt()throws Exception {
        PcmSession s=new PcmSession("synthetic",1,0,0,0,1,"tone",0,2);s.tone(0,440,2000);
        long until=System.currentTimeMillis()+2000;while(s.pcm.size()<4&&!s.done()&&System.currentTimeMillis()<until)Thread.sleep(5);
        assertEquals(4,s.pcm.size());s.cancel();assertTrue(s.pcm.isEmpty());assertTrue(s.canceled());
    }
}
