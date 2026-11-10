package li.cil.oc.plusplus;

import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Pure bounded rectangle/input model. No world scan, chunk load, renderer or GPU. */
public final class BiosCanvasLayout {
    public static final int MAX_CELLS=256;
    public static final double NATIVE_BORDER=2.25/16.0;
    private BiosCanvasLayout(){}
    public static final class Axis {
        public final int x,y,z;
        public Axis(int x,int y,int z){if(Math.abs((long)x)+Math.abs((long)y)+Math.abs((long)z)!=1)throw new IllegalArgumentException("unit axis required");this.x=x;this.y=y;this.z=z;}
        long dot(int px,int py,int pz){return (long)x*px+(long)y*py+(long)z*pz;}
        double local(double hx,double hy,double hz){double d=x*hx+y*hy+z*hz;return x+y+z<0?1+d:d;}
        Axis cross(Axis b){return new Axis(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
        @Override public boolean equals(Object o){if(!(o instanceof Axis))return false;Axis a=(Axis)o;return x==a.x&&y==a.y&&z==a.z;}
        @Override public int hashCode(){return Objects.hash(x,y,z);}
    }
    public static final class Basis {
        public final Axis normal,up,right;
        public Basis(Axis normal,Axis up){if(normal==null||up==null||normal.dot(up.x,up.y,up.z)!=0)throw new IllegalArgumentException("orthogonal facing/up required");this.normal=normal;this.up=up;this.right=up.cross(normal);}
        @Override public boolean equals(Object o){if(!(o instanceof Basis))return false;Basis b=(Basis)o;return normal.equals(b.normal)&&up.equals(b.up);}
        @Override public int hashCode(){return Objects.hash(normal,up);}
    }
    public static final class Cell {
        public final int x,y,z,tier,color;
        public final Basis basis;
        public final UUID world,identity,binding;
        public final long u,v,plane;
        public Cell(UUID world,int x,int y,int z,Basis basis,int tier,int color,UUID identity,UUID binding){
            this.world=Objects.requireNonNull(world);this.x=x;this.y=y;this.z=z;this.basis=Objects.requireNonNull(basis);this.tier=tier;this.color=color;this.identity=Objects.requireNonNull(identity);this.binding=binding;
            u=basis.right.dot(x,y,z);v=basis.up.dot(x,y,z);plane=basis.normal.dot(x,y,z);
        }
        boolean compatible(Cell b){return world.equals(b.world)&&basis.equals(b.basis)&&plane==b.plane&&tier==b.tier&&color==b.color&&Objects.equals(binding,b.binding);}
        String location(){return world+"/"+x+","+y+","+z;}
        @Override public boolean equals(Object o){if(!(o instanceof Cell))return false;Cell b=(Cell)o;return x==b.x&&y==b.y&&z==b.z&&compatible(b)&&identity.equals(b.identity);}
        @Override public int hashCode(){return Objects.hash(world,x,y,z,basis,tier,color,identity,binding);}
    }
    private static final Comparator<Cell> ORDER=Comparator.comparing((Cell c)->c.world.toString()).thenComparingInt(c->c.x).thenComparingInt(c->c.y).thenComparingInt(c->c.z);
    public static final class Point {
        public final double x,y;
        Point(double x,double y){this.x=x;this.y=y;}
    }
    public static final class Group {
        public final long minU,minV;
        public final int width,height;
        public final Cell origin;
        public final List<Cell> cells;
        public final String fingerprint;
        private Group(Collection<Cell> members){
            List<Cell> list=new ArrayList<>(members);list.sort(ORDER);cells=Collections.unmodifiableList(list);
            long u=Long.MAX_VALUE,v=Long.MAX_VALUE,maxU=Long.MIN_VALUE,maxV=Long.MIN_VALUE;
            for(Cell c:list){u=Math.min(u,c.u);v=Math.min(v,c.v);maxU=Math.max(maxU,c.u);maxV=Math.max(maxV,c.v);}
            minU=u;minV=v;width=Math.toIntExact(maxU-u+1);height=Math.toIntExact(maxV-v+1);
            Cell o=null;for(Cell c:list)if(c.u==u&&c.v==v)o=c;if(o==null||(long)width*height!=list.size())throw new IllegalArgumentException("filled rectangle required");origin=o;
            fingerprint=hash(list);
        }
        public boolean isOrigin(Cell c){return origin.equals(c);}
        /** Shared outer border only: internal joins do not create dead click strips. */
        public Point hit(Cell c,Axis side,double hx,double hy,double hz){
            return hit(c,side,hx,hy,hz,(width-2*NATIVE_BORDER)/(height-2*NATIVE_BORDER));
        }
        /** Optional letterboxing uses the same viewport for rendering and input. */
        public Point hit(Cell c,Axis side,double hx,double hy,double hz,double canvasAspect){
            if(!cells.contains(c)||!c.basis.normal.equals(side)||!Double.isFinite(hx)||!Double.isFinite(hy)||!Double.isFinite(hz)||hx<0||hx>1||hy<0||hy>1||hz<0||hz>1)return null;
            if(!Double.isFinite(canvasAspect)||canvasAspect<=0)return null;
            if(Math.abs(c.basis.normal.local(hx,hy,hz)-1)>0.00001)return null;
            double ax=c.u-minU+c.basis.right.local(hx,hy,hz),ay=height-1-(c.v-minV)+1-c.basis.up.local(hx,hy,hz),b=NATIVE_BORDER;
            double iw=width-2*b,ih=height-2*b,viewW=iw,viewH=iw/canvasAspect;
            if(viewH>ih){viewH=ih;viewW=ih*canvasAspect;}
            double left=b+(iw-viewW)/2,top=b+(ih-viewH)/2;
            if(ax<=left||ay<=top||ax>=left+viewW||ay>=top+viewH)return null;
            return new Point((ax-left)/viewW,(ay-top)/viewH);
        }
    }
    private static final class Key {
        final Cell c;final long u,v;
        Key(Cell c,long u,long v){this.c=c;this.u=u;this.v=v;}
        @Override public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return u==k.u&&v==k.v&&c.compatible(k.c);}
        @Override public int hashCode(){return Objects.hash(c.world,c.basis,c.plane,c.tier,c.color,c.binding,u,v);}
    }
    public static List<Group> partition(Collection<Cell> input,int maxWidth,int maxHeight){
        if(input==null||input.size()>MAX_CELLS||maxWidth<1||maxHeight<1)throw new IllegalArgumentException("bounded cells/configured positive limits required");
        List<Cell> sorted=new ArrayList<>(input);if(sorted.contains(null))throw new IllegalArgumentException("null cell");sorted.sort(ORDER);Set<String> positions=new HashSet<>();Set<UUID> identities=new HashSet<>();Map<Key,Cell> index=new HashMap<>();Map<Cell,Group> groups=new HashMap<>();
        for(Cell c:sorted){if(!positions.add(c.location())||!identities.add(c.identity))throw new IllegalArgumentException("duplicate position/identity");index.put(new Key(c,c.u,c.v),c);groups.put(c,new Group(Collections.singleton(c)));}
        boolean changed;
        do{changed=false;
            for(Cell c:sorted){Group a=groups.get(c);long[][] candidates={{a.minU,a.minV+a.height},{a.minU,a.minV-1},{a.minU+a.width,a.minV},{a.minU-1,a.minV}};
                for(long[] pos:candidates){Cell neighbor=index.get(new Key(c,pos[0],pos[1]));if(neighbor==null)continue;Group b=groups.get(neighbor);if(a==b)continue;
                    boolean horizontal=a.minV==b.minV&&a.height==b.height&&(a.minU+a.width==b.minU||b.minU+b.width==a.minU)&&(long)a.width+b.width<=maxWidth;
                    boolean vertical=a.minU==b.minU&&a.width==b.width&&(a.minV+a.height==b.minV||b.minV+b.height==a.minV)&&(long)a.height+b.height<=maxHeight;
                    if(horizontal||vertical){List<Cell> union=new ArrayList<>(a.cells);union.addAll(b.cells);Group merged=new Group(union);for(Cell member:union)groups.put(member,merged);a=merged;changed=true;break;}
                }
            }
        }while(changed);
        List<Group> result=new ArrayList<>(new LinkedHashSet<>(groups.values()));result.sort((a,b)->ORDER.compare(a.origin,b.origin));return Collections.unmodifiableList(result);
    }
    private static String hash(List<Cell> list){
        try{MessageDigest h=MessageDigest.getInstance("SHA-256");for(Cell c:list){String s=c.location()+":"+c.basis.normal.x+","+c.basis.normal.y+","+c.basis.normal.z+":"+c.basis.up.x+","+c.basis.up.y+","+c.basis.up.z+":"+c.tier+":"+c.color+":"+c.identity+":"+c.binding+"\n";h.update(s.getBytes(StandardCharsets.UTF_8));}
            StringBuilder out=new StringBuilder();for(byte b:h.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
