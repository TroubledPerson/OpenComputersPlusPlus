package li.cil.oc.plusplus;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import java.util.*;

/** Qualified cyan/light-and-dark-blue detail only; original PNG and T3 unchanged. */
public final class NetworkSprites {
 private NetworkSprites(){}
 private static final Set<Integer> BLUE=new HashSet<Integer>(Arrays.asList(
  0x203434,0x2a3434,0x0d1212,0x303a3a,0x131919,0x181f1f,0x2c3636,0x9bb2b3,
  0x284142,0x020707,0xa5babb,0xb0cacc,0x2a4243,0x101616,0x161c1c,0x495252,
  0x161e1e,0x192222,0x91aaab,0x113031,0x020808,0x040c0c,0x1a2222,0x2e494a,0x262f2f));
 public static boolean isBlue(int argb){return (argb>>>24)!=0&&BLUE.contains(argb&0xffffff);}
 public static BufferedImage card(BufferedImage base,int tier){
  if(tier<1||tier>3)throw new IllegalArgumentException();BufferedImage out=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<16;y++)for(int x=0;x<16;x++){int p=base.getRGB(x,y);
   if(tier<3&&isBlue(p)){int v=Math.min(255,Math.round(Math.max((p>>>16)&255,Math.max((p>>>8)&255,p&255))*1.25f));
    p=(p&0xff000000)|(v<<16)|((tier==1?v:Math.round(v*0.875f))<<8)|(tier==1?v:Math.round(v*0.30f));
   }out.setRGB(x,y,p);
  }return out;
 }
 public static void generate(File source,File target)throws IOException{
  BufferedImage base=ImageIO.read(new File(source,"lancard.png"));File dir=new File(target,"assets/opencomputers/textures/items");dir.mkdirs();
  for(int tier=1;tier<=2;tier++)ImageIO.write(card(base,tier),"PNG",new File(dir,"ocpp_network"+tier+".png"));
 }
}
