package li.cil.oc.plusplus;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** Approved reference uses the native Internet Card's first frame, without numerals. */
public final class SoundSprites {
    private SoundSprites(){}
    public static BufferedImage card(BufferedImage base,int tier){
        if(tier<1||tier>3)throw new IllegalArgumentException();
        // Copy raw ARGB, including the RGB of transparent pixels. SrcOver drawing
        // normalizes those pixels and would change untouched native details.
        BufferedImage out=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0,0,16,16,base.getRGB(0,0,16,16,null,0,16),0,16);
        String[] speaker={"00100","01101","11101","01101","00100"};
        // Historical silver/gold/cyan tier fill, with a contrasting dark outline.
        // The white left bracket is hardware, never a tier numeral.
        for(int y=0;y<5;y++)for(int x=0;x<5;x++)if(speaker[y].charAt(x)=='1')
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                int px=10+x+dx,py=1+y+dy;if(px>=0&&px<16&&py>=0&&py<16)out.setRGB(px,py,0xff17191b);
            }
        int fill=new int[]{0xffc9c9c9,0xffdfd6a3,0xffb5e9e4}[tier-1];
        for(int y=0;y<5;y++)for(int x=0;x<5;x++)if(speaker[y].charAt(x)=='1')out.setRGB(10+x,1+y,fill);
        return out;
    }
    public static void generate(File source,File target,File previews)throws IOException{
        BufferedImage base=ImageIO.read(new File(source,"internetcard.png"));File out=new File(target,"assets/opencomputers/textures/items");out.mkdirs();
        BufferedImage sheet=new BufferedImage(480,160,BufferedImage.TYPE_INT_ARGB);Graphics2D g=sheet.createGraphics();g.setColor(new Color(0x777777));g.fillRect(0,0,480,160);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for(int tier=1;tier<=3;tier++){BufferedImage image=card(base,tier);ImageIO.write(image,"PNG",new File(out,"ocpp_sound"+tier+".png"));g.drawImage(image,16+(tier-1)*160,16,128,128,null);}g.dispose();ImageIO.write(sheet,"PNG",new File(previews,"sound-card-comparison.png"));
    }
}
