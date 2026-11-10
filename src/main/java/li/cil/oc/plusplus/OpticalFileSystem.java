package li.cil.oc.plusplus;

import li.cil.oc.api.fs.FileSystem;
import li.cil.oc.api.fs.Handle;
import li.cil.oc.api.fs.Mode;
import net.minecraft.nbt.NBTTagCompound;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** World-save-only optical media. No public Lua path can select the host root. */
public final class OpticalFileSystem implements FileSystem {
    public static final long DEFAULT_CAPACITY = 32L * 1024 * 1024;
    private static final Set<Path> MOUNTS = Collections.newSetFromMap(new ConcurrentHashMap<Path, Boolean>());
    private final Path root, data, seal;
    private final boolean rewritable;
    private final long capacity;
    private final int cost;
    private long used;
    private int entries;
    private boolean active;
    private final Map<Integer, OpticalHandle> handles = new HashMap<Integer, OpticalHandle>();

    /** Called only with a UUID-derived directory under the current world save. */
    public OpticalFileSystem(Path directory, boolean rw, long newCapacity, int fileCost) throws IOException {
        if (newCapacity < 1024 || newCapacity > 128L*1024*1024 || fileCost < 0 || fileCost > 65536)
            throw new IOException("invalid optical media limits");
        root = directory.toAbsolutePath().normalize();
        safeAncestors(root);
        Files.createDirectories(root);
        data=root.resolve("data");seal=root.resolve("sealed");rewritable=rw;cost=fileCost;
        Path descriptor=root.resolve("media");
        if (!Files.exists(descriptor, LinkOption.NOFOLLOW_LINKS)) {
            if(Files.exists(data, LinkOption.NOFOLLOW_LINKS))throw new IOException("missing optical descriptor");
            byte[] bytes=((rw?"CDRW":"CDR")+"\n"+newCapacity+"\n").getBytes("US-ASCII");
            try(FileChannel out=FileChannel.open(descriptor,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                out.write(ByteBuffer.wrap(bytes));out.force(true);
            }
        }
        safeAncestors(descriptor);
        if(Files.size(descriptor)>64)throw new IOException("invalid optical descriptor");
        List<String> lines=Files.readAllLines(descriptor,java.nio.charset.StandardCharsets.US_ASCII);
        try {
            if(lines.size()!=2||!lines.get(0).equals(rw?"CDRW":"CDR"))throw new IOException("optical media type mismatch");
            capacity=Long.parseLong(lines.get(1));
            if(capacity<1024||capacity>128L*1024*1024)throw new IOException("invalid recorded capacity");
        }catch(NumberFormatException e){throw new IOException("invalid optical descriptor",e);}
        safeAncestors(data);Files.createDirectories(data);
        reopen();
    }
    private static void safeAncestors(Path path)throws IOException {
        for(Path p=path;p!=null;p=p.getParent())if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)){
            java.nio.file.attribute.BasicFileAttributes a=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(a.isSymbolicLink()||a.isOther())throw new IOException("optical link or special entry rejected");
        }
    }
    public synchronized void reopen()throws IOException {
        if(active)return;
        if(!MOUNTS.add(root))throw new IOException("optical media already mounted");
        try {used=cost;entries=1;scan(data,0);active=true;}
        catch(IOException e){MOUNTS.remove(root);throw e;}
    }
    private void scan(Path dir,int depth)throws IOException {
        if(depth>128)throw new IOException("optical directory depth limit");
        try(DirectoryStream<Path> children=Files.newDirectoryStream(dir)){
            for(Path p:children){safeAncestors(p);if(++entries>4096)throw new IOException("optical entry limit");
                used+=cost;
                if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))scan(p,depth+1);
                else if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))used+=Files.size(p);
                else throw new IOException("unsupported optical entry");
            }
        }
    }
    private void mounted()throws IOException{if(!active)throw new IOException("optical media removed");}
    private boolean sealed(){return Files.exists(seal,LinkOption.NOFOLLOW_LINKS);}
    private void writable()throws IOException{mounted();if(sealed())throw new IOException("CD-R is finalized (read-only)");}
    private Path path(String value)throws IOException {
        if(value==null||value.length()>1024||value.indexOf('\\')>=0||value.indexOf(':')>=0)throw new IOException("invalid optical path");
        String relative=value.startsWith("/")?value.substring(1):value;
        String[] parts=relative.split("/",-1);if(parts.length>128)throw new IOException("optical path depth limit");
        for(String part:parts){if(part.equals(".")||part.equals("..")||part.length()>255)throw new IOException("invalid optical path");
            String stem=part.split("\\.",-1)[0].toUpperCase(Locale.ROOT);
            if(part.endsWith(".")||part.endsWith(" ")||stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))throw new IOException("reserved optical filename");
            for(int i=0;i<part.length();i++)if(part.charAt(i)<32||"*?\"<>|".indexOf(part.charAt(i))>=0)throw new IOException("invalid optical path");}
        Path p=data.resolve(relative).normalize();if(!p.startsWith(data))throw new IOException("optical path escape");
        safeAncestors(p);return p;
    }
    private Path checked(String value)throws IOException{mounted();return path(value);}
    private void room(long delta)throws IOException{if(delta>capacity-used)throw new IOException("not enough space");}
    private void entryRoom()throws IOException{if(entries>=4096)throw new IOException("optical entry limit");room(cost);}
    public synchronized boolean isReadOnly(){return sealed();}
    public long spaceTotal(){return capacity;}
    public synchronized long spaceUsed(){return used;}
    public synchronized boolean exists(String p){try{return Files.exists(checked(p),LinkOption.NOFOLLOW_LINKS);}catch(IOException e){return false;}}
    public synchronized boolean isDirectory(String p){try{return Files.isDirectory(checked(p),LinkOption.NOFOLLOW_LINKS);}catch(IOException e){return false;}}
    public synchronized long size(String p){try{Path f=checked(p);return Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)?Files.size(f):0;}catch(IOException e){return 0;}}
    public synchronized long lastModified(String p){try{return Files.getLastModifiedTime(checked(p),LinkOption.NOFOLLOW_LINKS).toMillis();}catch(IOException e){return 0;}}
    public synchronized String[] list(String p){try{Path dir=checked(p);if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))return null;
        List<String> result=new ArrayList<String>();try(DirectoryStream<Path> children=Files.newDirectoryStream(dir)){
            for(Path f:children){safeAncestors(f);result.add(f.getFileName().toString()+(Files.isDirectory(f,LinkOption.NOFOLLOW_LINKS)?"/":""));}}
        Collections.sort(result);return result.toArray(new String[result.size()]);
    }catch(IOException e){return null;}}
    public synchronized boolean delete(String p){try{writable();Path f=path(p);if(f.equals(data)||!Files.exists(f,LinkOption.NOFOLLOW_LINKS)||isOpen(f))return false;
        long freed=cost+(Files.isDirectory(f,LinkOption.NOFOLLOW_LINKS)?0:Files.size(f));Files.delete(f);used-=freed;entries--;return true;
    }catch(IOException e){return false;}}
    public synchronized boolean makeDirectory(String p){try{writable();Path f=path(p);if(Files.exists(f,LinkOption.NOFOLLOW_LINKS))return false;entryRoom();Files.createDirectory(f);used+=cost;entries++;return true;}catch(IOException e){return false;}}
    public synchronized boolean rename(String from,String to)throws FileNotFoundException {try{writable();Path a=path(from),b=path(to);
        if(a.equals(data)||b.equals(data)||isOpen(a)||isOpen(b)||Files.exists(b,LinkOption.NOFOLLOW_LINKS))return false;
        Files.move(a,b);return true;
    }catch(IOException e){return false;}}
    private boolean isOpen(Path p){for(OpticalHandle h:handles.values())if(h.path.startsWith(p))return true;return false;}
    public synchronized boolean setLastModified(String p,long time){try{writable();Files.setLastModifiedTime(path(p),java.nio.file.attribute.FileTime.fromMillis(time));return true;}catch(IOException e){return false;}}
    public synchronized int open(String name,Mode mode)throws FileNotFoundException {try{mounted();if(mode!=Mode.Read)writable();Path p=path(name);
        if(handles.size()>=128)throw new IOException("too many optical handles");
        if(p.equals(data)||Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("not a file");
        boolean present=Files.exists(p,LinkOption.NOFOLLOW_LINKS);if(!present&&mode!=Mode.Read)entryRoom();
        if(mode!=Mode.Read&&isOpen(p))throw new IOException("file is already open");
        RandomAccessFile f=new RandomAccessFile(p.toFile(),mode==Mode.Read?"r":"rw");
        long old=present?f.length():0;
        if(mode!=Mode.Read&&!present){used+=cost;entries++;}
        if(mode==Mode.Write){f.setLength(0);f.getChannel().force(false);used-=old;}
        if(mode==Mode.Append)f.seek(f.length());
        int id;do{id=ThreadLocalRandom.current().nextInt(1,Integer.MAX_VALUE);}while(handles.containsKey(id));
        handles.put(id,new OpticalHandle(id,p,f,mode));return id;
    }catch(IOException e){FileNotFoundException wrapped=new FileNotFoundException(e.getMessage());wrapped.initCause(e);throw wrapped;}}
    public synchronized Handle getHandle(int id){return active?handles.get(id):null;}
    private void closeHandles(){for(OpticalHandle h:new ArrayList<OpticalHandle>(handles.values()))h.close();}
    public synchronized void close(){boolean owned=active;closeHandles();active=false;if(owned)MOUNTS.remove(root);}
    /** force() on each data write precedes durable CREATE_NEW seal publication. */
    public synchronized boolean finalizeRecording()throws IOException {
        mounted();if(rewritable)throw new IOException("CD-RW does not finalize");if(sealed())return false;
        closeHandles();
        try(FileChannel out=FileChannel.open(seal,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            out.write(ByteBuffer.wrap(new byte[]{1}));out.force(true);
        }
        return true;
    }
    public synchronized void load(NBTTagCompound nbt){/* Never restore stale handles or trust mutable item seal flags. */}
    public synchronized void save(NBTTagCompound nbt){nbt.setBoolean("optical.finalized",sealed());nbt.setLong("capacity.used",used);nbt.setLong("optical.capacity",capacity);}
    private final class OpticalHandle implements Handle {
        final int id;final Path path;final RandomAccessFile file;final Mode mode;boolean closed;
        OpticalHandle(int i,Path p,RandomAccessFile f,Mode m){id=i;path=p;file=f;mode=m;}
        private void check()throws IOException{mounted();if(closed||handles.get(id)!=this)throw new IOException("bad optical handle");safeAncestors(path);}
        public long position(){synchronized(OpticalFileSystem.this){try{check();return file.getFilePointer();}catch(IOException e){return -1;}}}
        public long length(){synchronized(OpticalFileSystem.this){try{check();return file.length();}catch(IOException e){return -1;}}}
        public void close(){synchronized(OpticalFileSystem.this){if(!closed){closed=true;handles.remove(id);try{file.close();}catch(IOException ignored){}}}}
        public int read(byte[] into)throws IOException{synchronized(OpticalFileSystem.this){check();if(mode!=Mode.Read)throw new IOException("not a read handle");return file.read(into);}}
        public long seek(long to)throws IOException{synchronized(OpticalFileSystem.this){check();if(to<0||to>capacity)throw new IOException("invalid optical offset");file.seek(to);return to;}}
        public void write(byte[] bytes)throws IOException{synchronized(OpticalFileSystem.this){check();writable();if(mode==Mode.Read)throw new IOException("not a write handle");
            if(bytes.length>65536)throw new IOException("optical write exceeds 64 KiB");
            long old=file.length(),end=file.getFilePointer()+bytes.length,growth=Math.max(old,end)-old;room(growth);
            try{file.write(bytes);file.getChannel().force(false);}finally{used+=Math.max(0,file.length()-old);}
        }}
    }
}
