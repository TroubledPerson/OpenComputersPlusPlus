package li.cil.oc.plusplus;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

/** Native platter contour/hole and plain foreground labels; no extra circular surround. */
public final class OpticalSprites {
    private OpticalSprites(){}
    public static BufferedImage disc(BufferedImage platter,boolean rw){
        BufferedImage out=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0,0,16,16,platter.getRGB(0,0,16,16,null,0,16),0,16);
        // Pale native platter and plain labels; edging is a separate RGBA layer.
        boolean[][] mask=badge(rw);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(mask[y][x])out.setRGB(x,y,0xff333333);
        return out;
    }
    private static boolean[][] badge(boolean rw){
        String[] r={"110","101","110","101","101"};
        String[] w={"10001","10001","10101","10101","01010"};
        boolean[][] mask=new boolean[16][16];
        glyph(mask,r,rw?6:12,10);if(rw)glyph(mask,w,10,10);
        return mask;
    }
    public static boolean boundary(BufferedImage platter,int x,int y){
        if((platter.getRGB(x,y)>>>24)==0)return false;
        for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){int nx=x+d[0],ny=y+d[1];
            if(nx<0||nx>=16||ny<0||ny>=16||(platter.getRGB(nx,ny)>>>24)==0)return true;
        }
        return false;
    }
    /** 31 percent dark edging, excluding every foreground badge pixel. */
    public static BufferedImage contour(BufferedImage platter,boolean rw){
        BufferedImage out=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        boolean[][] mask=badge(rw);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)
            if(!mask[y][x]&&boundary(platter,x,y))out.setRGB(x,y,0x50484a4c);
        return out;
    }
    public static BufferedImage composite(BufferedImage base,BufferedImage overlay){
        BufferedImage out=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0,0,16,16,base.getRGB(0,0,16,16,null,0,16),0,16);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            int b=base.getRGB(x,y),o=overlay.getRGB(x,y),a=o>>>24;
            if(a!=0){int v=b&0xff000000;
                for(int shift:new int[]{16,8,0})v|=((((o>>>shift)&255)*a+((b>>>shift)&255)*(255-a)+127)/255)<<shift;
                out.setRGB(x,y,v);
            }
        }
        return out;
    }
    /** Retain native pale platter shading; only 20 percent dark edge treatment. */
    public static int faintContour(int nativePixel){
        int value=nativePixel&0xff000000;
        for(int shift:new int[]{16,8,0})value|=((4*((nativePixel>>>shift)&255)+((0x484a4c>>>shift)&255)+2)/5)<<shift;
        return value;
    }
    private static void glyph(boolean[][] mask,String[] lines,int left,int top){for(int y=0;y<lines.length;y++)for(int x=0;x<lines[y].length();x++)if(lines[y].charAt(x)=='1')mask[top+y][left+x]=true;}
    public static void generate(File source,File target,File previews)throws IOException{
        File items=new File(target,"assets/opencomputers/textures/items");items.mkdirs();
        BufferedImage platter=ImageIO.read(new File(source,"disk.png"));
        BufferedImage r=disc(platter,false),rw=disc(platter,true);
        ImageIO.write(r,"PNG",new File(items,"ocpp_cdr.png"));ImageIO.write(rw,"PNG",new File(items,"ocpp_cdrw.png"));
        BufferedImage edgeR=contour(platter,false),edgeRw=contour(platter,true);
        ImageIO.write(edgeR,"PNG",new File(items,"ocpp_cdr_contour.png"));ImageIO.write(edgeRw,"PNG",new File(items,"ocpp_cdrw_contour.png"));
        BufferedImage front=ImageIO.read(new File(source.getParentFile(),"blocks/diskdrive_front.png"));
        BufferedImage tray=driveFront(front);
        File blocks=new File(target,"assets/opencomputers/textures/blocks");blocks.mkdirs();ImageIO.write(tray,"PNG",new File(blocks,"ocpp_cddrive_front.png"));
        BufferedImage sheet=new BufferedImage(480,160,BufferedImage.TYPE_INT_ARGB);Graphics2D g=sheet.createGraphics();
        g.setColor(new Color(0x777777));g.fillRect(0,0,480,160);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(composite(r,edgeR),16,16,128,128,null);g.drawImage(composite(rw,edgeRw),176,16,128,128,null);g.drawImage(tray,336,16,128,128,null);g.dispose();
        ImageIO.write(sheet,"PNG",new File(previews,"optical-media-drive.png"));
    }
    public static BufferedImage driveFront(BufferedImage front){
        BufferedImage out=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0,0,16,16,front.getRGB(0,0,16,16,null,0,16),0,16);
        // Replace the upper floppy recess itself. Preserve chassis/LED/lower panel.
        for(int y=2;y<=6;y++)for(int x=2;x<=13;x++){
            int l=front.getRGB(1,y)&255,r=front.getRGB(14,y)&255,v=(l*(13-x)+r*(x-2))/11;
            out.setRGB(x,y,0xff000000|(v<<16)|(v<<8)|v);
        }
        for(int x=2;x<=13;x++){out.setRGB(x,3,0xff777777);out.setRGB(x,5,0xff515151);out.setRGB(x,4,(x==2||x==13)?0xff5a5a5a:0xff202020);}
        return out;
    }
}
