package li.cil.oc.plusplus;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/** Extend the permitted native pixel family without rewriting original assets. */
public final class StageOneSprites {
    private static final int[] COLOURS={0x66FF66,0xEB8844,0xB3312C};
    private StageOneSprites() {}

    private static int tint(int value,int target){
        float[] c=Color.RGBtoHSB((value>>>16)&255,(value>>>8)&255,value&255,null);
        if(c[1]<=0.1f)return value; // Native silver pins and neutral outlines.
        float[] anchor=Color.RGBtoHSB(176,217,220,null),t=Color.RGBtoHSB((target>>>16)&255,(target>>>8)&255,target&255,null);
        float h=c[0]+t[0]-anchor[0];h-=Math.floor(h);
        return(value&0xFF000000)|(Color.HSBtoRGB(h,c[1],c[2])&0xFFFFFF);
    }

    public static BufferedImage chip(BufferedImage nativeChip,int tier){
        if(tier<4||tier>6||nativeChip.getWidth()!=16||nativeChip.getHeight()!=16)throw new IllegalArgumentException("native16x16 chip and tier4..6 required");
        BufferedImage out=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        int extra=tier-4,left=3-extra,top=4-extra,width=11+2*extra,height=8+2*extra;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int sx=3+x*11/width,sy=4+y*8/height;
            out.setRGB(left+x,top+y,tint(nativeChip.getRGB(sx,sy),COLOURS[tier-4]));
        }
        // One-pixel silver teeth grow with the package, preserving native shade.
        int teeth=(width+1)/2;
        for(int n=0;n<teeth;n++){
            int sx=3+2*Math.min(5,n*6/teeth),x=left+2*n;
            out.setRGB(x,top-1,nativeChip.getRGB(sx,3));
            out.setRGB(x,top+height,nativeChip.getRGB(sx,12));
        }
        return out;
    }

    public static BufferedImage halfRam(BufferedImage base,BufferedImage reference,int tier){
        if(tier<4||tier>6)throw new IllegalArgumentException("half RAM tier4..6 required");
        BufferedImage out=TierSpriteLayers.bake("ram_half",base,reference,COLOURS[tier-4]);
        // Three-chip sticks have a chip here, not the whole-stick exposed teeth.
        // Tint only its six neutral bevel pixels; retain the original dark body,
        // right/bottom outline, accepted markings, PCB and gold edge contacts.
        out.setRGB(5,6,TierSpriteLayers.ramContactTint(0xff858472,COLOURS[tier-4]));
        for(int[] p:new int[][]{{6,6},{7,6},{8,6},{5,7},{5,8}})
            out.setRGB(p[0],p[1],TierSpriteLayers.ramContactTint(0xff5c5b4f,COLOURS[tier-4]));
        return out;
    }

    public static void generate(File originals,File root,File preview)throws IOException{
        File output=new File(root,"assets/opencomputers/textures/items");Files.createDirectories(output.toPath());Files.createDirectories(preview.toPath());
        BufferedImage nativeChip=ImageIO.read(new File(originals,"chip3.png"));
        BufferedImage half=ImageIO.read(new File(originals,"ram4.png")),halfRef=ImageIO.read(new File(originals,"ram6.png"));
        BufferedImage sheet=new BufferedImage(768,344,BufferedImage.TYPE_INT_ARGB);Graphics2D g=sheet.createGraphics();
        g.setColor(new Color(0x777777));g.fillRect(0,0,768,344);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for(int tier=4;tier<=6;tier++){
            BufferedImage c=chip(nativeChip,tier),r=halfRam(half,halfRef,tier);
            ImageIO.write(c,"PNG",new File(output,"ocpp_chip"+tier+".png"));ImageIO.write(r,"PNG",new File(output,"ocpp_ram"+tier+"_5.png"));
            int x=(tier-4)*256+32;g.drawImage(c,x,32,x+128,160,0,0,16,16,null);g.drawImage(r,x,192,x+128,320,0,0,16,16,null);
            g.setColor(Color.WHITE);g.drawString("Chip T"+tier,x+136,96);g.drawString("RAM T"+tier+".5",x+136,256);
        }
        g.dispose();ImageIO.write(sheet,"PNG",new File(preview,"stage1-chips-half-ram.png"));
    }
}
