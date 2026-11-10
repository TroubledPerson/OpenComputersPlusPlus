package li.cil.oc.plusplus;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Narrow preservation check against the explicitly supplied dev.8 test JAR. */
public final class RamPinRevisionStandaloneTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static boolean pin(int x,int y){return(y==6||y==9)&&(x==5||x==7||x==9);}
    private static byte[] bytes(InputStream s)throws Exception{try(InputStream in=s;java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] a=new byte[1024];int n;while((n=in.read(a))>=0)out.write(a,0,n);return out.toByteArray();}}
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("generated-root preserved-dev8-jar preview-directory");
        File generated=new File(args[0]),preview=new File(args[2]);
        BufferedImage sheet=new BufferedImage(768,304,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=sheet.createGraphics();g.setColor(new Color(0x777777));g.fillRect(0,0,768,304);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        try(ZipFile old=new ZipFile(args[1])){
            BufferedImage t4=ImageIO.read(old.getInputStream(old.getEntry("assets/opencomputers/textures/items/ocpp_ram4.png")));
            for(String family:TierSpriteLayers.FAMILIES)for(int tier=4;tier<=6;tier++){
                String path="assets/opencomputers/textures/items/ocpp_"+family+tier+".png";
                BufferedImage before=ImageIO.read(old.getInputStream(old.getEntry(path))),after=ImageIO.read(new File(generated,path));
                check(before.getWidth()==after.getWidth()&&before.getHeight()==after.getHeight(),"geometry changed");
                int changed=0;
                for(int y=0;y<before.getHeight();y++)for(int x=0;x<before.getWidth();x++){
                    int p=before.getRGB(x,y),q=after.getRGB(x,y);
                    check((p>>>24)==(q>>>24),"alpha changed");
                    if(family.equals("ram")&&tier>=5&&pin(x,y)){
                        check(q==t4.getRGB(x,y),"pin differs from preserved dev.8 T4");
                        check(q!=p,"expected pin correction absent");
                    }else check(p==q,"unrequested pixel changed: "+family+tier+" "+x+","+y);
                    if(p!=q)changed++;
                }
                if(family.equals("ram")){
                    check(changed==(tier==4?0:6),"unexpected changed RAM pixel count");
                    for(int[] p:new int[][]{{6,3},{7,3},{6,11},{7,11}})check(before.getRGB(p[0],p[1])==after.getRGB(p[0],p[1]),"approved chip marking changed");
                    int col=tier-4;g.drawImage(before,col*256+32,24,col*256+160,152,0,0,16,16,null);g.drawImage(after,col*256+32,168,col*256+160,296,0,0,16,16,null);
                    g.setColor(Color.WHITE);g.drawString("T"+tier+" dev.8",col*256+168,80);g.drawString("T"+tier+" pin fix",col*256+168,224);
                    System.out.println("RAM T"+tier+": "+changed+" changed pixels; approved markings unchanged");
                }
                if(family.equals("apu"))check(Arrays.equals(bytes(old.getInputStream(old.getEntry(path+".mcmeta"))),Files.readAllBytes(new File(generated,path+".mcmeta").toPath())),"APU animation timing changed");
            }
        }finally{g.dispose();}
        Files.createDirectories(preview.toPath());ImageIO.write(sheet,"PNG",new File(preview,"ram-pin-before-after.png"));
        System.out.println("PASS dev.8 pixel preservation and T4-reference pin checks: "+checks+" assertions");
    }
}
