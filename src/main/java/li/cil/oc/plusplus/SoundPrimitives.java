package li.cil.oc.plusplus;

import java.io.*;
import java.util.*;

/** Pure bounded audio operations: no devices, host files, URLs or game state. */
public final class SoundPrimitives {
    private SoundPrimitives(){}
    public static final int CHUNK=8192, MAX_ENCODED=8*1024*1024, MAX_MIDI=32768;
    public static final String[] INSTRUMENTS={"harp","bass","basedrum","snare","hat","guitar","flute","bell","chime","xylophone","pling"};
    public static int instrument(String name){for(int i=0;i<INSTRUMENTS.length;i++)if(INSTRUMENTS[i].equals(name))return i;throw new IllegalArgumentException("unknown note-block instrument");}
    public static String format(String name){if(!Arrays.asList("wav","mp3","flac","midi").contains(name))throw new IllegalArgumentException("unsupported audio format");return name;}
    public static byte[] tone(int shape,int hz,int ms){
        if(shape<0||shape>3||hz<20||hz>8000||ms<20||ms>2000)throw new IllegalArgumentException("tone bounds: shape0..3,20..8000Hz,20..2000ms");
        byte[] out=new byte[22050*ms/1000];int noise=0x1234567;
        for(int i=0;i<out.length;i++){
            double phase=((long)i*hz%22050)/22050.0;double v;
            switch(shape){case 0:v=phase<0.5?1:-1;break;case 1:v=1-4*Math.abs(phase-0.5);break;case 2:v=2*phase-1;break;default:noise^=noise<<13;noise^=noise>>>17;noise^=noise<<5;v=(noise&255)/127.5-1;}
            out[i]=(byte)(128+(int)(v*90));
        }return out;
    }
    public static final class Note {
        public final int tick,instrument,note,velocity;
        public Note(int t,int i,int n,int v){tick=t;instrument=i;note=n;velocity=v;}
    }
    private static final class Event {
        long tick;int order,status,a,b,tempo;
        Event(long t,int o,int s,int a,int b,int tempo){tick=t;order=o;status=s;this.a=a;this.b=b;this.tempo=tempo;}
    }
    /** SMF0/1 PPQ only. One-shot vanilla sample mapping, not a GM synthesizer. */
    public static List<Note> midi(byte[] bytes,int maxSeconds)throws IOException {
        if(bytes==null||bytes.length>MAX_MIDI||maxSeconds<1||maxSeconds>60)throw new IOException("MIDI size/duration bound");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt()!=0x4d546864||in.readInt()!=6)throw new IOException("invalid MIDI header");
        int type=in.readUnsignedShort(),tracks=in.readUnsignedShort(),ppq=in.readUnsignedShort();
        if(type>1||tracks<1||tracks>8||(type==0&&tracks!=1)||ppq<1||ppq>960)throw new IOException("unsupported MIDI type/tracks/PPQ");
        List<Event> all=new ArrayList<Event>();int order=0;
        for(int track=0;track<tracks;track++){
            if(in.readInt()!=0x4d54726b)throw new IOException("invalid MIDI track");int length=in.readInt();
            if(length<0||length>in.available())throw new IOException("truncated MIDI track");
            byte[] data=new byte[length];in.readFully(data);DataInputStream tr=new DataInputStream(new ByteArrayInputStream(data));
            long ticks=0;int running=-1;boolean ended=false;
            while(tr.available()>0){if(++order>2048)throw new IOException("MIDI event bound");ticks+=vlq(tr);if(ticks>1000000)throw new IOException("MIDI tick bound");
                int first=tr.readUnsignedByte(),status=first,a=-1;
                if(first<128){if(running<0)throw new IOException("invalid MIDI running status");status=running;a=first;}
                if(status==255){running=-1;int meta=tr.readUnsignedByte(),n=vlq(tr);if(n>tr.available())throw new IOException("truncated MIDI metadata");
                    if(meta==0x51){if(n!=3)throw new IOException("invalid MIDI tempo");int tempo=(tr.readUnsignedByte()<<16)|(tr.readUnsignedByte()<<8)|tr.readUnsignedByte();if(tempo<1||tempo>60000000)throw new IOException("MIDI tempo bound");all.add(new Event(ticks,order,255,0,0,tempo));}
                    else {tr.skipBytes(n);if(meta==0x2f){if(n!=0)throw new IOException("invalid MIDI end");ended=true;all.add(new Event(ticks,order,254,0,0,0));break;}}
                }else if(status==0xf0||status==0xf7){running=-1;int n=vlq(tr);if(n>tr.available())throw new IOException("truncated MIDI sysex");tr.skipBytes(n);
                }else if(status>=0x80&&status<=0xef){running=status;int kind=status&0xf0;if(a<0)a=tr.readUnsignedByte();int b=(kind==0xc0||kind==0xd0)?0:tr.readUnsignedByte();if(a>127||b>127)throw new IOException("invalid MIDI data");all.add(new Event(ticks,order,status,a,b,0));
                }else throw new IOException("unsupported MIDI event");
            }if(!ended)throw new IOException("missing MIDI end-of-track");
        }if(in.available()!=0)throw new IOException("trailing MIDI bytes");
        Collections.sort(all,(a,b)->a.tick==b.tick?Integer.compare(a.order,b.order):Long.compare(a.tick,b.tick));
        List<Note> notes=new ArrayList<Note>();int[] programs=new int[16];int tempo=500000;long tick=0;double micros=0;Map<Integer,Integer> density=new HashMap<Integer,Integer>();
        for(Event e:all){micros+=(e.tick-tick)*(double)tempo/ppq;tick=e.tick;if(micros>maxSeconds*1000000.0)throw new IOException("MIDI duration bound");
            if(e.status==255)tempo=e.tempo;
            else if((e.status&0xf0)==0xc0)programs[e.status&15]=e.a;
            else if((e.status&0xf0)==0x90&&e.b>0){
                int when=(int)Math.round(micros/50000),note=e.a;while(note<54)note+=12;while(note>78)note-=12;
                int p=programs[e.status&15],instrument=p<8?0:p<16?7:p<24?6:p<32?5:p<40?1:p<80?6:p<96?10:p<112?8:7;
                if((e.status&15)==9)instrument=e.a==35||e.a==36?2:e.a==38||e.a==40?3:4;
                int count=density.containsKey(when)?density.get(when):0;if(count>=8||notes.size()>=512)throw new IOException("MIDI note density/count bound");density.put(when,count+1);
                notes.add(new Note(when,instrument,note-54,e.b));
            }
        }return notes;
    }
    private static int vlq(DataInputStream in)throws IOException {int value=0;for(int i=0;i<4;i++){int b=in.readUnsignedByte();value=(value<<7)|(b&127);if((b&128)==0)return value;}throw new IOException("MIDI variable integer bound");}
    /** Strict contiguous upload; a failed write never changes the offset. */
    public static final class Upload {
        private final byte[] bytes;private int offset;
        public Upload(int length,int limit){if(length<1||length>limit||limit>MAX_ENCODED)throw new IllegalArgumentException("encoded size bound");bytes=new byte[length];}
        public int size(){return bytes.length;}public int received(){return offset;}
        public void write(byte[] chunk){if(chunk==null||chunk.length<1||chunk.length>CHUNK||chunk.length>bytes.length-offset)throw new IllegalArgumentException("audio chunk bound");System.arraycopy(chunk,0,bytes,offset,chunk.length);offset+=chunk.length;}
        public byte[] finish(){if(offset!=bytes.length)throw new IllegalStateException("incomplete audio upload");return bytes;}
    }
}
