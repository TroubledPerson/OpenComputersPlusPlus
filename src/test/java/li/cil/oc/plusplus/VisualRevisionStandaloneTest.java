package li.cil.oc.plusplus;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Focused comparison against an explicitly supplied preserved release JAR.
 * No game initialization or host/private files are accessed. */
public final class VisualRevisionStandaloneTest {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static float[] hsv(int p){return Color.RGBtoHSB((p>>>16)&255,(p>>>8)&255,p&255,null);}
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("generated-root preserved-dev7-jar preview-directory");
        File generated=new File(args[0]),preview=new File(args[2]);
        BufferedImage sheet=new BufferedImage(768,304,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=sheet.createGraphics();g.setColor(new Color(0x777777));g.fillRect(0,0,768,304);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        try(ZipFile old=new ZipFile(args[1])) {
            BufferedImage t4=ImageIO.read(old.getInputStream(old.getEntry("assets/opencomputers/textures/items/ocpp_ram4.png")));
            for(String family:TierSpriteLayers.FAMILIES)for(int tier=4;tier<=6;tier++) {
                String path="assets/opencomputers/textures/items/ocpp_"+family+tier+".png";
                BufferedImage before=ImageIO.read(old.getInputStream(old.getEntry(path))),after=ImageIO.read(new File(generated,path));
                check(before.getWidth()==after.getWidth()&&before.getHeight()==after.getHeight(),"geometry changed");
                int changed=0;
                for(int y=0;y<before.getHeight();y++)for(int x=0;x<before.getWidth();x++) {
                    int p=before.getRGB(x,y),q=after.getRGB(x,y);
                    check((p>>>24)==(q>>>24),"alpha changed");
                    if(!family.equals("ram")||tier==4)check(p==q,"unrequested resource changed: "+family+tier);
                    else if(p!=q){changed++;
                        if((y==6||y==9)&&(x==5||x==7||x==9))check(q==t4.getRGB(x,y),"RAM pin differs from preserved T4");
                        else {float[] a=hsv(p),b=hsv(q);check(b[1]>a[1],"RAM saturation did not increase");check(Math.abs(b[2]-a[2])<0.0001f,"RAM value shading changed");float d=Math.abs(a[0]-b[0]);check(Math.min(d,1-d)<0.012f,"RAM tier hue drift");}
                    }
                }
                if(family.equals("ram")) {
                    if(tier>=5)check(changed>0,"RAM colours unchanged");else check(changed==0,"T4 RAM changed");
                    check(before.getRGB(11,2)==after.getRGB(11,2),"gold contacts changed");
                    int col=tier-4;g.drawImage(before,col*256+32,24,col*256+160,152,0,0,16,16,null);
                    g.drawImage(after,col*256+32,168,col*256+160,296,0,0,16,16,null);
                    g.setColor(Color.WHITE);g.drawString("T"+tier+" dev.7",col*256+168,80);g.drawString("T"+tier+" source fix",col*256+168,224);
                    System.out.println("RAM T"+tier+": "+changed+" changed pixels");
                }
                if(family.equals("apu")) {
                    byte[] a;try(java.io.InputStream s=old.getInputStream(old.getEntry(path+".mcmeta"))){java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();byte[] v=new byte[1024];int n;while((n=s.read(v))>=0)b.write(v,0,n);a=b.toByteArray();}
                    check(Arrays.equals(a,Files.readAllBytes(new File(generated,path+".mcmeta").toPath())),"APU timing changed");
                }
            }
        }finally{g.dispose();}
        Files.createDirectories(preview.toPath());ImageIO.write(sheet,"PNG",new File(preview,"ram-before-after.png"));
        System.out.println("PASS dev.7 resource preservation and RAM colour checks: "+checks+" assertions");
    }
}
