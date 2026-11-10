package li.cil.oc.plusplus;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

/** Build-time family palette variants. Existing coloured siblings identify
 * tier-specific pixels; their silhouettes, details and shade values are retained.
 * Original rasters are never rewritten. Baked resources use untinted layer zero.
 */
public final class TierSpriteLayers {
    public static final String[] FAMILIES = {"cpu", "hdd", "graphicscard", "ram", "apu"};
    private TierSpriteLayers() {}

    /** Six middle-chip teeth and four inter-tooth gaps, not markings/edge contacts. */
    public static boolean ramHardwarePixel(int x,int y){return (y==6||y==9)&&x>=5&&x<=9;}
    /** Contact metal only; the four green PCB gaps are not metal. */
    public static boolean ramContactPixel(int x,int y){return (y==6||y==9)&&(x==5||x==7||x==9);}

    /** Grey-based tier hue, retaining native alpha and the brightest shade channel. */
    public static int ramContactTint(int original,int target){
        float[] shade=Color.RGBtoHSB((original>>>16)&255,(original>>>8)&255,original&255,null);
        float[] tier=Color.RGBtoHSB((target>>>16)&255,(target>>>8)&255,target&255,null);
        return(original&0xff000000)|(Color.HSBtoRGB(tier[0],0.18f,shade[2])&0xffffff);
    }

    public static int layerTint(int layer, int tierColour) {
        return layer == 1 ? tierColour : 0xFFFFFF;
    }

    public static String baseName(String family) {
        switch(family) {
            case "cpu": return "cpu2"; // Existing patterned die, not the plain cpu1 die.
            case "apu": return "apu1";
            case "hdd": return "hdd2";
            case "graphicscard": return "graphicscard2";
            case "ram": return "ram3"; // Two-chip whole-stick layout; no half tiers added.
            default: throw new IllegalArgumentException("unknown sprite family");
        }
    }

    public static String referenceName(String family) {
        switch(family) {
            case "cpu": return "cpu3";
            case "apu": return "apu2";
            case "hdd": return "hdd3";
            case "graphicscard": return "graphicscard3";
            case "ram": return "ram5";
            default: throw new IllegalArgumentException("unknown sprite family");
        }
    }

    public static int[] anchor(String family) {
        switch(family) {
            case "cpu": case "apu": return new int[]{7,7};
            case "hdd": return new int[]{7,5};
            case "graphicscard": return new int[]{8,8};
            case "ram": return new int[]{5,6};
            case "ram_half": return new int[]{6,3};
            default: throw new IllegalArgumentException("unknown sprite family");
        }
    }

    /** Rotate the family's native hue, retaining per-pixel saturation/value and
     * relative hue variation. T5/T6 RAM gets stronger tier-mark saturation;
     * the explicit whole-stick contact mask instead uses grey-based tier hues.
     * Shared frame highlights, outlines, contacts and achromatic metal are unchanged.
     */
    public static BufferedImage bake(String family, BufferedImage base, BufferedImage reference, int target) {
        if(base.getWidth()!=16 || base.getHeight()<16 || base.getHeight()%16!=0 ||
           base.getWidth()!=reference.getWidth() || base.getHeight()!=reference.getHeight())
            throw new IllegalArgumentException("expected matching original 16-pixel frames");
        int[] p=anchor(family);
        int marker=base.getRGB(p[0],p[1]);
        float[] nativeHue=Color.RGBtoHSB((marker>>>16)&255,(marker>>>8)&255,marker&255,null);
        int referenceMarker=reference.getRGB(p[0],p[1]);
        float[] referenceHue=Color.RGBtoHSB((referenceMarker>>>16)&255,(referenceMarker>>>8)&255,referenceMarker&255,null);
        float[] tierHue=Color.RGBtoHSB((target>>>16)&255,(target>>>8)&255,target&255,null);
        if(nativeHue[1]<=0) throw new IllegalArgumentException("native palette anchor must be coloured");
        float rotation=tierHue[0]-nativeHue[0];
        // Preserve the approved dark PCB gaps. Only the six contact texels gain
        // the latest owner-requested low-saturation green/orange/red-grey hue.
        BufferedImage result=new BufferedImage(16,base.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<base.getHeight();y++) for(int x=0;x<16;x++) {
            int original=base.getRGB(x,y), peer=reference.getRGB(x,y), value=original;
            if((original>>>24)!=(peer>>>24)) throw new IllegalArgumentException("native variant silhouette differs");
            if((original>>>24)>0 && (original&0xFFFFFF)!=(peer&0xFFFFFF)) {
                float[] colour=Color.RGBtoHSB((original>>>16)&255,(original>>>8)&255,original&255,null);
                float[] other=Color.RGBtoHSB((peer>>>16)&255,(peer>>>8)&255,peer&255,null);
                float nativeDistance=Math.abs(colour[0]-nativeHue[0]);
                nativeDistance=Math.min(nativeDistance,1-nativeDistance);
                float referenceDistance=Math.abs(other[0]-referenceHue[0]);
                referenceDistance=Math.min(referenceDistance,1-referenceDistance);
                if(colour[1]>0 && other[1]>0 && nativeDistance<0.14f && referenceDistance<0.14f && Math.abs(rotation)>0.000001f) {
                    float h=colour[0]+rotation; h=h-(float)Math.floor(h);
                    float saturation=colour[1];
                    if((family.equals("ram") || family.equals("ram_half")) && (target==0xEB8844 || target==0xB3312C)) {
                        saturation+=0.85f*Math.max(0,tierHue[1]-saturation);
                    }
                    value=(original&0xFF000000)|(Color.HSBtoRGB(h,saturation,colour[2])&0xFFFFFF);
                }
            }
            if(family.equals("ram") && ramHardwarePixel(x,y))
                value=ramContactPixel(x,y)?ramContactTint(original,target):original;
            result.setRGB(x,y,value);
        }
        return result;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) throw new IllegalArgumentException("source-items generated-resource-root preview-root");
        File source = new File(args[0]);
        File output = new File(args[1],"assets/opencomputers/textures/items");
        File previews = new File(args[2]);
        Files.createDirectories(output.toPath()); Files.createDirectories(previews.toPath());
        BufferedImage sheet = new BufferedImage(960, 5*152+24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x777777));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        String[] headings={"Base variant 1","Base variant 2","Base variant 3","Candidate T4","Candidate T5","Candidate T6"};
        int[] colours={0x66FF66,0xEB8844,0xB3312C};
        for(int col=0;col<6;col++){g.setColor(Color.WHITE);g.drawString(headings[col],col*160+8,16);}
        for (int row=0;row<FAMILIES.length;row++) {
            String family=FAMILIES[row];
            BufferedImage base=ImageIO.read(new File(source,baseName(family)+".png"));
            BufferedImage reference=ImageIO.read(new File(source,referenceName(family)+".png"));
            File metadata=new File(source,baseName(family)+".png.mcmeta");
            for(int col=0;col<6;col++) {
                BufferedImage item;
                if(col<3) {
                    String suffix=family.equals("apu")?(col==2?"creative":Integer.toString(col+1)):
                        family.equals("ram")?Integer.toString(1+col*2):Integer.toString(col+1);
                    item=ImageIO.read(new File(source,family+suffix+".png"));
                } else {
                    item=bake(family,base,reference,colours[col-3]);
                    String name="ocpp_"+family+(col+1)+".png";
                    ImageIO.write(item,"PNG",new File(output,name));
                    if(metadata.isFile()) Files.copy(metadata.toPath(),new File(output,name+".mcmeta").toPath(),StandardCopyOption.REPLACE_EXISTING);
                    ImageIO.write(item,"PNG",new File(previews,family+"-t"+(col+1)+".png"));
                }
                int px=col*160+16, py=row*152+32;
                g.drawImage(item,px,py,px+128,py+128,0,0,16,16,null);
                g.setColor(Color.WHITE);g.drawString(family,col*160+16,py+142);
            }
        }
        g.dispose(); ImageIO.write(sheet,"PNG",new File(previews,"tier-sprite-comparison.png"));
        StageOneSprites.generate(source,new File(args[1]),previews);
        OpticalSprites.generate(source,new File(args[1]),previews);
        SoundSprites.generate(source,new File(args[1]),previews);
        NetworkSprites.generate(source,new File(args[1]));
        PreviewSprites.generate(source,new File(args[1]));
    }
}
