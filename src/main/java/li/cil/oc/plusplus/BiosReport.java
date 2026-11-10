package li.cil.oc.plusplus;

/** Fixed numeric in-game snapshot. No strings, paths, commands or guest capability. */
public final class BiosReport {
    private BiosReport(){}
    public static final int NODES=0,COMPONENTS=1,FILESYSTEMS=2,CD_DRIVES=3,RAIDS=4,GPU=5,MODEM=6,INTERNET=7,DATA=8,SOUND=9,OTHER=10,SCREENS=11;
    public static final int MEDIA=12,READ_ONLY=13,CAPACITY_KIB=14,QUOTA_UNKNOWN=15,MEMORY_TEST=16,INPUT_TEST=17,OPTICAL_ENABLED=18,OPTICAL_BASE=19,STATUS=35,COLUMNS=36,ROWS=37,SIZE=38;
    public static final int SCRATCH_BYTES=65536,REPORT_FIRST=12,REPORT_COMMIT=REPORT_FIRST+SIZE*2;
    public static int[] empty(){return new int[SIZE];}
    public static int[] normalize(int[] source){
        int[] out=empty();if(source==null||source.length!=SIZE){out[STATUS]=2;return out;}
        for(int i=0;i<SIZE;i++){
            int max=i==CAPACITY_KIB?Integer.MAX_VALUE:i>=OPTICAL_BASE&&i<STATUS?1000000:i==OPTICAL_ENABLED?1:i==MEMORY_TEST||i==INPUT_TEST||i==STATUS?2:256;
            out[i]=Math.max(0,Math.min(max,source[i]));
        }
        return out;
    }
    /** Atomic bounded assembly over existing vanilla window-property shorts. */
    public static final class Words {
        private final int[] staged=empty();private final boolean[] seen=new boolean[SIZE*2];
        public int[] accept(int id,int value){
            if(id==REPORT_COMMIT){boolean complete=true;for(boolean part:seen)complete&=part;java.util.Arrays.fill(seen,false);return complete?normalize(staged):null;}
            int word=id-REPORT_FIRST;if(word<0||word>=seen.length)return null;
            int index=word/2,shift=(word%2)*16;staged[index]=(staged[index]&~(0xffff<<shift))|((value&0xffff)<<shift);seen[word]=true;return null;
        }
    }
    public static boolean matches(byte[] bytes,int pattern){
        if(bytes==null||bytes.length!=SCRATCH_BYTES)return false;
        for(int i=0;i<bytes.length;i++)if(bytes[i]!=(byte)(pattern^i))return false;
        return true;
    }
    /** A tiny simulated scratch test, never host/guest RAM discovery or certification. */
    public static int memorySelfTest(){
        try{byte[] bytes=new byte[SCRATCH_BYTES];for(int pattern:new int[]{0,255,85,170}){
            for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(pattern^i);
            if(!matches(bytes,pattern))return 2;
        }return 1;}catch(OutOfMemoryError unavailable){return 2;}
    }
    /** Tests the menu coordinate model; physical input and rendered bars remain owner checks. */
    public static int inputSelfTest(){return BiosBootState.actionAt(0,.5,.3)==10&&BiosBootState.actionAt(0,.5,.62)==12&&
        BiosBootState.actionAt(3,.5,.25)==20&&BiosBootState.actionAt(3,.5,.37)==21&&
        BiosBootState.actionAt(3,.5,.55)==0&&BiosBootState.actionAt(3,.5,.74)==22&&
        BiosBootState.actionAt(0,Double.NaN,.3)==-1&&BiosBootState.actionAt(0,-1,.3)==-1?1:2;}
}
