package li.cil.oc.plusplus;

import li.cil.oc.api.fs.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Bundled OpenOS for ordinary OC machines, never a PC/QEMU ISO. */
public final class OpenOSMaster {
    private static final String PREFIX="/assets/opencomputers/loot/openos/";
    public interface Source { InputStream open(String name)throws IOException; }
    public static final Source BUNDLED=name->{InputStream in=OpenOSMaster.class.getResourceAsStream(name);if(in==null)throw new FileNotFoundException(name);return in;};
    private OpenOSMaster(){}
    private static byte[] bytes(InputStream input,int limit)throws IOException{
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0){if(out.size()+n>limit)throw new IOException("OpenOS resource limit");out.write(b,0,n);}return out.toByteArray();}
    }
    private static String hash(byte[] b){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder s=new StringBuilder();for(byte v:d)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static final class Entry {final String path,sha;final int length;Entry(String p,String h,int n){path=p;sha=h;length=n;}}
    private static final class Plan {
        final byte[] manifest;final List<Entry> entries;final SortedSet<String> dirs;final long total;
        Plan(byte[] m,List<Entry> e,SortedSet<String> d,long t){manifest=m;entries=e;dirs=d;total=t;}
    }
    private static Plan plan(Source source)throws IOException{
        byte[] manifest=bytes(source.open("/assets/opencomputers/openos-master.manifest"),512*1024);
        String[] lines=new String(manifest,"UTF-8").split("\n");if(lines.length<2||!lines[0].equals("OC++ OpenOS master v1"))throw new IOException("invalid OpenOS manifest");
        List<Entry> entries=new ArrayList<>();Set<String> paths=new HashSet<>();SortedSet<String> dirs=new TreeSet<>();long total=0;
        for(int n=1;n<lines.length;n++)if(!lines[n].isEmpty()){
            String[] p=lines[n].split("\t",-1);if(p.length!=3||!p[0].matches("[a-f0-9]{64}"))throw new IOException("invalid OpenOS entry");
            String name;try{name=li.cil.oc.api.vm.VmManifest.relative(p[2]);}catch(IllegalArgumentException e){throw new IOException(e);}
            int length;try{length=Integer.parseInt(p[1]);}catch(NumberFormatException e){throw new IOException(e);}
            if(length<0||length>1024*1024||!paths.add(name)||entries.size()>=4096)throw new IOException("OpenOS entry limit");
            total+=length;entries.add(new Entry(name,p[0],length));for(int i=name.indexOf('/');i>=0;i=name.indexOf('/',i+1))dirs.add(name.substring(0,i));
        }
        if(!paths.contains("init.lua")||!paths.contains(".prop")||!paths.contains("lib/core/boot.lua"))throw new IOException("OpenOS boot files missing");
        if(entries.size()+dirs.size()+1>4096)throw new IOException("OpenOS entry limit");
        return new Plan(manifest,entries,dirs,total);
    }
    public static String seed(OpticalFileSystem fs,int fileCost,Source source)throws IOException{
        if(fs.isReadOnly()||fs.list("/")==null||fs.list("/").length!=0)throw new IOException("only a fresh empty master can be seeded");
        Plan p=plan(source);
        if(p.total+(long)fileCost*(p.entries.size()+p.dirs.size()+1)>fs.spaceTotal())throw new IOException("OpenOS does not fit CD quota");
        // Validate every bundled byte before creating content. A later failure
        // leaves its unsealed prefix preserved and unmounted by the caller.
        for(Entry e:p.entries)checked(source,e);
        for(String d:p.dirs)if(!fs.makeDirectory(d))throw new IOException("OpenOS directory creation failed");
        for(Entry e:p.entries){byte[] data=checked(source,e);int h=fs.open(e.path,Mode.Write);try{fs.getHandle(h).write(data);}finally{fs.getHandle(h).close();}}
        fs.finalizeRecording();return hash(p.manifest);
    }
    /** Recovery of the lost item marker only: never creates, seals or writes media. */
    public static String verifySealed(OpticalFileSystem fs,Source source)throws IOException{
        if(!fs.isReadOnly())throw new IOException("OpenOS marker recovery requires sealed media");
        Plan p=plan(source);Set<String> expected=new HashSet<>();
        for(String d:p.dirs)expected.add(d+"/");
        for(Entry e:p.entries)expected.add(e.path);
        Set<String> actual=new HashSet<>();collect(fs,"",actual,0);
        if(!actual.equals(expected))throw new IOException("OpenOS recovery contents differ from bundled master");
        for(Entry e:p.entries){
            checked(source,e); // A corrupt distribution cannot authorize recovery.
            if(fs.size(e.path)!=e.length)throw new IOException("OpenOS recovery length mismatch: "+e.path);
            int id=fs.open(e.path,Mode.Read);
            try{Handle h=fs.getHandle(id);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
                while((n=h.read(b))>=0){if(n==0||out.size()+n>e.length)throw new IOException("OpenOS recovery read limit");out.write(b,0,n);}
                if(out.size()!=e.length||!hash(out.toByteArray()).equals(e.sha))throw new IOException("OpenOS recovery hash mismatch: "+e.path);
            }finally{fs.getHandle(id).close();}
        }
        return hash(p.manifest);
    }
    private static void collect(OpticalFileSystem fs,String prefix,Set<String> found,int depth)throws IOException{
        if(depth>128)throw new IOException("OpenOS recovery depth limit");
        String[] children=fs.list(prefix.isEmpty()?"/":prefix);
        if(children==null)throw new IOException("OpenOS recovery directory unavailable");
        for(String child:children){String path=prefix+child;if(!found.add(path)||found.size()>4096)throw new IOException("OpenOS recovery entry limit");
            if(child.endsWith("/"))collect(fs,path,found,depth+1);}
    }
    private static byte[] checked(Source source,Entry e)throws IOException{byte[] b=bytes(source.open(PREFIX+e.path),1024*1024);if(b.length!=e.length||!hash(b).equals(e.sha))throw new IOException("OpenOS resource integrity failure: "+e.path);return b;}
    /** Build-time only: inputs are the selected repository resource tree. */
    public static void main(String[] args)throws Exception{
        Path source=Paths.get(args[0]),target=Paths.get(args[1]);List<Path> files=new ArrayList<>();
        try(java.util.stream.Stream<Path> stream=Files.walk(source)){stream.forEach(p->{if(Files.isSymbolicLink(p))throw new IllegalArgumentException("linked asset rejected");if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))files.add(p);});}
        files.sort(Comparator.comparing(p->source.relativize(p).toString()));StringBuilder text=new StringBuilder("OC++ OpenOS master v1\n");
        for(Path p:files){byte[] b=Files.readAllBytes(p);String relative=source.relativize(p).toString().replace('\\','/');li.cil.oc.api.vm.VmManifest.relative(relative);text.append(hash(b)).append('\t').append(b.length).append('\t').append(relative).append('\n');}
        Files.createDirectories(target.getParent());Files.write(target,text.toString().getBytes("UTF-8"));
    }
}
