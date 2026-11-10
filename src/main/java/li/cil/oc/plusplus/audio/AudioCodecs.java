package li.cil.oc.plusplus.audio;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.jar.*;

/** Fixed reviewed codec archives, isolated from other mods. Never exposed to Lua. */
public final class AudioCodecs {
    private AudioCodecs(){}
    private static Class<?> backend;
    public static void decode(byte[] data,String format,int seconds,PcmSink sink,BooleanSupplier canceled)throws Exception {
        try{backend().getMethod("decode",byte[].class,String.class,int.class,PcmSink.class,BooleanSupplier.class).invoke(null,data,format,seconds,sink,canceled);}
        catch(java.lang.reflect.InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw(Exception)cause;throw new IOException("codec failed",cause);}
    }
    private static synchronized Class<?> backend()throws Exception {
        if(backend!=null)return backend;
        Properties props=new Properties();try(InputStream in=AudioCodecs.class.getResourceAsStream("/META-INF/ocpp-audio/runtime.properties")){if(in==null)throw new IOException("missing audio manifest");props.load(in);}
        String[] names={"jlayer-1.0.1.jar","nayuki-flac-c9b8727b.jar"};
        URL own=AudioCodecs.class.getProtectionDomain().getCodeSource().getLocation();URL[] urls=new URL[3];urls[0]=own;
        File jar=new File(own.toURI());Path cache=null;
        for(int i=0;i<2;i++){
            String expected=props.getProperty(names[i]);if(expected==null||!expected.matches("[a-f0-9]{64}"))throw new IOException("invalid audio manifest");
            String dev=System.getProperty(i==0?"ocpp.audioJlayer":"ocpp.audioFlac");File file;
            if(dev!=null)file=new File(dev);
            else{
                if(!jar.isFile())throw new IOException("packaged audio runtime required");
                if(cache==null){Path parent=jar.getCanonicalFile().toPath().getParent().toRealPath();Path root=parent.resolve(".ocpp-runtime");directory(root);cache=root.resolve("audio-sha256");directory(cache);}
                Path target=cache.resolve(expected+"-"+names[i]);
                if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS))try(JarFile source=new JarFile(jar,true)){
                    JarEntry entry=source.getJarEntry("META-INF/ocpp-audio/"+names[i]);if(entry==null||entry.getSize()<1||entry.getSize()>2*1024*1024)throw new IOException("audio payload bound");
                    try(InputStream in=source.getInputStream(entry);OutputStream out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                        byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))>=0){total+=n;if(total>2*1024*1024)throw new IOException("audio extraction bound");out.write(b,0,n);}
                    }
                }
                if(!target.toRealPath().startsWith(cache.toRealPath()))throw new IOException("audio cache escape");file=target.toFile();
            }
            if(!Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)||!expected.equals(hash(file)))throw new IOException("audio payload checksum mismatch");urls[i+1]=file.toURI().toURL();
        }
        URLClassLoader loader=new URLClassLoader(urls,AudioCodecs.class.getClassLoader()){
            private boolean privateType(String n){return n.startsWith("li.cil.oc.plusplus.audio.codec.")||n.startsWith("javazoom.")||n.startsWith("io.nayuki.flac.");}
            private final Map<String,Package> packages=new HashMap<String,Package>();
            @Override protected Package getPackage(String n){return privateType(n+".")?packages.get(n):super.getPackage(n);}
            @Override protected Package definePackage(String n,String st,String sv,String vendor,String it,String iv,String v,URL seal){Package p=super.definePackage(n,st,sv,vendor,it,iv,v,seal);if(privateType(n+"."))packages.put(n,p);return p;}
            @Override protected synchronized Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException {
                if(!privateType(n))return super.loadClass(n,resolve);Class<?> c=findLoadedClass(n);if(c==null)c=findClass(n);if(resolve)resolveClass(c);return c;
            }
            private boolean ownResource(String n){return privateType(n.replace('/','.'));}
            @Override public URL getResource(String n){return ownResource(n)?findResource(n):super.getResource(n);}
            @Override public Enumeration<URL> getResources(String n)throws IOException{return ownResource(n)?findResources(n):super.getResources(n);}
        };
        backend=loader.loadClass("li.cil.oc.plusplus.audio.codec.Decoders");return backend;
    }
    private static void directory(Path p)throws IOException{if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(p);if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)||!p.toRealPath().equals(p))throw new IOException("audio cache link rejected");}
    private static String hash(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)d.update(b,0,n);}StringBuilder h=new StringBuilder();for(byte b:d.digest())h.append(String.format("%02x",b&255));return h.toString();}
}
