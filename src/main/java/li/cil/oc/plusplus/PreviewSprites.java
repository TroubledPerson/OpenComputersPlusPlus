package li.cil.oc.plusplus;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** T7-only snow-white panel albedo; native ports, vents, silhouette and shading. */
public final class PreviewSprites {
    private PreviewSprites(){}
    public static BufferedImage snow(BufferedImage base){
        BufferedImage out=new BufferedImage(base.getWidth(),base.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<base.getHeight();y++)for(int x=0;x<base.getWidth();x++){
            int p=base.getRGB(x,y),r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;
            if((p>>>24)!=0&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=3&&r>=32){
                int v=r>=64?240+(r-64)*15/191:210+(r-32)*29/32;
                p=(p&0xff000000)|(v<<16)|(v<<8)|v;
            }
            out.setRGB(x,y,p);
        }
        return out;
    }
    /** Native screen recess and slats are hardware, not snow-white case panels. */
    public static BufferedImage screenSnow(BufferedImage base){
        if(base.getWidth()!=16||base.getHeight()!=16)throw new IllegalArgumentException("native16x16 screen texture required");
        BufferedImage out=snow(base);
        for(int y=3;y<=12;y++)for(int x=3;x<=12;x++)out.setRGB(x,y,base.getRGB(x,y));
        return out;
    }
    /** Native connected recess masks extend to shared edges; they are not panels. */
    public static BufferedImage connectedScreenSnow(String name,BufferedImage base){
        if(base.getWidth()!=16||base.getHeight()!=16)throw new IllegalArgumentException("native16x16 screen texture required");
        BufferedImage out=snow(base);
        if(name.startsWith("f"))return out;
        String part=name.endsWith("2")?name.substring(0,name.length()-1):name;
        int left=3,right=12,top=3,bottom=12;
        switch(part){
            case "b": break;
            case "bht": right=15;break;
            case "bhb": left=0;break;
            case "bhm": left=0;right=15;break;
            case "bvt": bottom=15;break;
            case "bvb": top=0;break;
            case "bvm": top=0;bottom=15;break;
            case "btl": right=15;bottom=15;break;
            case "btr": left=0;bottom=15;break;
            case "btm": left=0;right=15;bottom=15;break;
            case "bbl": right=15;top=0;break;
            case "bbr": left=0;top=0;break;
            case "bbm": left=0;right=15;top=0;break;
            case "bml": right=15;top=0;bottom=15;break;
            case "bmr": left=0;top=0;bottom=15;break;
            case "bmm": left=0;right=15;top=0;bottom=15;break;
            default: throw new IllegalArgumentException("unknown native connected screen part: "+name);
        }
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++)out.setRGB(x,y,base.getRGB(x,y));
        return out;
    }
    /** Case panels only: continuous white shadows, without the old31/32 brightness cliff.
     * Screen shading and original raster assets deliberately use their existing paths. */
    private static BufferedImage casePanels(BufferedImage base){
        if(base.getWidth()!=16||base.getHeight()!=16)throw new IllegalArgumentException("native16x16 case texture required");
        BufferedImage out=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int p=base.getRGB(x,y),r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;
            if((p>>>24)!=0&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=3){
                int v=r<64?216+(r*24+32)/64:240+(r-64)*15/191;
                p=(p&0xff000000)|(v<<16)|(v<<8)|v;
            }
            out.setRGB(x,y,p);
        }
        return out;
    }
    /** The12x12 native fan/recess is hardware, not white panel paint. */
    public static BufferedImage caseSideSnow(BufferedImage base){
        BufferedImage out=casePanels(base);
        for(int y=2;y<=13;y++)for(int x=2;x<=13;x++)out.setRGB(x,y,base.getRGB(x,y));
        return out;
    }
    /** Keep the full-height stripe, indicator colours and three-pixel lower port. */
    public static BufferedImage caseFrontSnow(BufferedImage base){
        BufferedImage out=casePanels(base);
        for(int y=5;y<=14;y++)for(int x=6;x<=7;x++)out.setRGB(x,y,base.getRGB(x,y));
        for(int x=12;x<=14;x++)out.setRGB(x,14,base.getRGB(x,14));
        return out;
    }
    public static void generate(File items,File target)throws IOException{
        File blocks=new File(items.getParentFile(),"blocks"),out=new File(target,"assets/opencomputers/textures/blocks");out.mkdirs();
        for(String name:new String[]{"case_top","case_side","case_back","case_front"}){
            BufferedImage base=ImageIO.read(new File(blocks,name+".png"));
            BufferedImage styled=name.equals("case_front")?caseFrontSnow(base):name.equals("case_side")?caseSideSnow(base):snow(base);
            ImageIO.write(styled,"PNG",new File(out,"ocpp_vm_"+name+".png"));
        }
        ImageIO.write(snow(ImageIO.read(new File(blocks,"screen/f.png"))),"PNG",new File(out,"ocpp_vm_screen_front.png"));
        ImageIO.write(screenSnow(ImageIO.read(new File(blocks,"screen/b2.png"))),"PNG",new File(out,"ocpp_vm_screen_side.png"));
        ImageIO.write(screenSnow(ImageIO.read(new File(blocks,"screen/b.png"))),"PNG",new File(out,"ocpp_vm_screen_cap.png"));
        File connected=new File(out,"ocpp_vm_screen");connected.mkdirs();
        File[] parts=new File(blocks,"screen").listFiles((dir,name)->name.endsWith(".png"));
        if(parts==null)throw new IOException("native connected screen textures missing");
        for(File part:parts){
            String name=part.getName().substring(0,part.getName().length()-4);
            ImageIO.write(connectedScreenSnow(name,ImageIO.read(part)),"PNG",new File(connected,part.getName()));
        }
        BufferedImage background=ImageIO.read(new File(items.getParentFile(),"gui/background.png"));
        BufferedImage panel=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<224;y++)for(int x=0;x<176;x++)panel.setRGB(x,y,background.getRGB(x,y>=140?82+y-140:y<4?y:8));
        File gui=new File(target,"assets/opencomputers/textures/gui");gui.mkdirs();
        ImageIO.write(panel,"PNG",new File(gui,"ocpp_vm_case.png"));
    }
}
