package li.cil.oc.plusplus;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;

/** Shared direct-Java/JUnit checks; reads only original and staged resources. */
public final class TierSpriteStyleChecks {
    private static final int[] COLOURS={0x66FF66,0xEB8844,0xB3312C};
    private static long assertions;
    private static void check(boolean value,String reason) { assertions++;if(!value)throw new AssertionError(reason); }
    private static BufferedImage read(File file) throws Exception {
        BufferedImage value=ImageIO.read(file);check(value!=null,"unreadable image "+file);return value;
    }
    private static BufferedImage base(File originals,String family) throws Exception { return read(new File(originals,TierSpriteLayers.baseName(family)+".png")); }
    private static BufferedImage ref(File originals,String family) throws Exception { return read(new File(originals,TierSpriteLayers.referenceName(family)+".png")); }
    private static BufferedImage generated(File root,String family,int tier) throws Exception { return read(new File(root,"assets/opencomputers/textures/items/ocpp_"+family+tier+".png")); }
    private static int rgb(BufferedImage image,int x,int y){return image.getRGB(x,y)&0xFFFFFF;}
    private static int max(int p){return Math.max((p>>>16)&255,Math.max((p>>>8)&255,p&255));}
    private static int min(int p){return Math.min((p>>>16)&255,Math.min((p>>>8)&255,p&255));}
    private static float[] hsv(int p){return Color.RGBtoHSB((p>>>16)&255,(p>>>8)&255,p&255,null);}
    private static void hue(int actual,int expected,String reason){
        float[] a=hsv(actual),b=hsv(expected);float d=Math.abs(a[0]-b[0]);d=Math.min(d,1-d);
        check(a[1]>0.05f&&d<0.012f,reason);
    }

    public static void runGroup(int group,File originals,File output) throws Exception {
        long start=assertions;
        switch(group) {
            case 0: // Silhouette, shared native pixels, and neutral metal.
                for(String family:TierSpriteLayers.FAMILIES) {
                    BufferedImage b=base(originals,family),r=ref(originals,family);
                    for(int tier=4;tier<=6;tier++) {
                        BufferedImage out=generated(output,family,tier);
                        check(out.getWidth()==b.getWidth()&&out.getHeight()==b.getHeight(),"frame dimensions "+family);
                        for(int y=0;y<b.getHeight();y++)for(int x=0;x<16;x++){
                            int p=b.getRGB(x,y),q=r.getRGB(x,y),v=out.getRGB(x,y);
                            check((p>>>24)==(v>>>24),"silhouette "+family);
                            if(family.equals("ram")&&TierSpriteLayers.ramHardwarePixel(x,y))check(v==(TierSpriteLayers.ramContactPixel(x,y)?TierSpriteLayers.ramContactTint(p,COLOURS[tier-4]):p),"qualified tier-grey contacts and preserved PCB gaps");
                            else if(p==q || max(p)==min(p) || (p>>>24)==0)check(p==v,"shared/neutral pixel "+family+" "+x+","+y);
                        }
                    }
                }
                break;
            case 1: // Native patterned CPU die and correct warm-vs-tier frame roles.
                for(String family:new String[]{"cpu","apu"}) {
                    BufferedImage b=base(originals,family);
                    for(int tier=4;tier<=6;tier++) {
                        BufferedImage out=generated(output,family,tier);
                        check(rgb(out,1,1)==rgb(b,1,1),"shared warm upper frame "+family);
                        check(rgb(out,7,1)==rgb(b,7,1),"shared dark trace "+family);
                        check(rgb(out,1,13)!=rgb(b,1,13),"lower frame must follow tier "+family);
                        check(rgb(out,7,7)!=rgb(out,7,8),"patterned die remains visible "+family);
                        hue(rgb(out,7,7),COLOURS[tier-4],"die palette "+family);
                    }
                }
                break;
            case 2: // HDD colour covers the native body and hub, not only corners.
                for(int tier=4;tier<=6;tier++) {
                    BufferedImage b=base(originals,"hdd"),out=generated(output,"hdd",tier);
                    check(rgb(out,5,12)!=rgb(b,5,12),"lower HDD body still uses old palette");
                    check(hsv(rgb(out,5,12))[1]>0.05f,"lower HDD body lost colour");
                    hue(rgb(out,7,5),COLOURS[tier-4],"HDD hub palette");
                    check(max(rgb(out,7,3))==max(rgb(b,7,3)),"HDD highlight value changed");
                }
                break;
            case 3: // Restore actual GPU tier markers; keep metal/connector colours.
                for(int tier=4;tier<=6;tier++) {
                    BufferedImage gpu=generated(output,"graphicscard",tier),ram=generated(output,"ram",tier);
                    BufferedImage gb=base(originals,"graphicscard"),rb=base(originals,"ram");
                    check(rgb(gpu,2,4)==rgb(gb,2,4),"GPU metal bracket recoloured");
                    check(rgb(gpu,5,12)==rgb(gb,5,12),"GPU contact recoloured");
                    check(rgb(ram,11,2)==0xBCBC71,"RAM gold contacts recoloured");
                    check(rgb(ram,11,2)==rgb(rb,11,2),"RAM contact/source mismatch");
                    hue(rgb(gpu,8,8),COLOURS[tier-4],"GPU tier marker");
                    hue(rgb(ram,6,3),COLOURS[tier-4],"whole RAM chip marking");
                    for(int y:new int[]{6,9})for(int x=5;x<=9;x++)check(ram.getRGB(x,y)==(TierSpriteLayers.ramContactPixel(x,y)?TierSpriteLayers.ramContactTint(rb.getRGB(x,y),COLOURS[tier-4]):rb.getRGB(x,y)),"qualified contact tint or preserved gap");
                    if(tier>=5) {
                        check(hsv(rgb(ram,6,3))[1]>=0.60f,"T5/T6 RAM marking too faint");
                        BufferedImage t4=generated(output,"ram",4);
                        for(int y:new int[]{6,9})for(int x:new int[]{5,7,9})
                                check(max(ram.getRGB(x,y))==max(t4.getRGB(x,y))&&ram.getRGB(x,y)!=t4.getRGB(x,y),"tier-specific hardware hue with consistent brightness");
                    }
                }
                break;
            case 4: // Preserve shade diversity/value and all APU frames; strengthen T5/T6 RAM only.
                for(String family:TierSpriteLayers.FAMILIES) {
                    BufferedImage b=base(originals,family),r=ref(originals,family);
                    for(int tier=4;tier<=6;tier++) {
                        BufferedImage out=generated(output,family,tier);
                        Set<Integer> before=new HashSet<>(),after=new HashSet<>();
                        for(int y=0;y<b.getHeight();y++)for(int x=0;x<16;x++) {
                            int p=b.getRGB(x,y),q=r.getRGB(x,y),v=out.getRGB(x,y);
                            if(family.equals("ram")&&TierSpriteLayers.ramHardwarePixel(x,y))check(v==(TierSpriteLayers.ramContactPixel(x,y)?TierSpriteLayers.ramContactTint(p,COLOURS[tier-4]):p),"exact tier-grey hardware and unchanged gap palette");
                            else if((p>>>24)>0 && p!=q && max(p)!=min(p)) {
                                before.add(p);after.add(v);
                                check(max(p)==max(v),"native value changed "+family);
                                if(family.equals("ram") && tier>=5)
                                    check(hsv(v)[1]>=hsv(p)[1]-0.01f,"RAM tier colour lost saturation");
                                else check(Math.abs(min(p)-min(v))<=1,"native saturation changed "+family);
                            }
                        }
                        check(after.size()==before.size(),"native palette distinctions collapsed "+family+" "+before.size()+"->"+after.size());
                    }
                }
                byte[] metadata=Files.readAllBytes(new File(originals,"apu1.png.mcmeta").toPath());
                for(int tier=4;tier<=6;tier++) {
                    check(generated(output,"apu",tier).getHeight()==96,"APU lost frames");
                    check(java.util.Arrays.equals(metadata,Files.readAllBytes(new File(output,"assets/opencomputers/textures/items/ocpp_apu"+tier+".png.mcmeta").toPath())),"APU animation timing changed");
                }
                break;
            case 5: // Current source models select baked resources without a tint overlay.
                File models=new File(originals.getParentFile().getParentFile(),"models/item");
                for(String family:TierSpriteLayers.FAMILIES)for(int tier=4;tier<=6;tier++) {
                    String stem=family.equals("graphicscard")?"gpu":family;
                    String json=new String(Files.readAllBytes(new File(models,"ocpp_"+stem+tier+".json").toPath()),StandardCharsets.UTF_8);
                    check(json.contains("\"layer0\":\"opencomputers:items/ocpp_"+family+tier+"\""),"baked resource binding "+stem);
                    check(!json.contains("layer1"),"double tint overlay "+stem);
                    check(TierSpriteLayers.layerTint(0,COLOURS[tier-4])==0xFFFFFF,"baked resource must not be multiplied again");
                    BufferedImage expected=TierSpriteLayers.bake(family,base(originals,family),ref(originals,family),COLOURS[tier-4]);
                    BufferedImage actual=generated(output,family,tier);
                    for(int y=0;y<actual.getHeight();y++)for(int x=0;x<16;x++)check(actual.getRGB(x,y)==expected.getRGB(x,y),"nondeterministic resource "+family);
                }
                break;
            default:throw new IllegalArgumentException("unknown check group");
        }
        System.out.println("PASS sprite group "+group+": "+(assertions-start)+" pixel/resource assertions");
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("original-items generated-resource-root");
        for(int group=0;group<6;group++)runGroup(group,new File(args[0]),new File(args[1]));
    }
}
