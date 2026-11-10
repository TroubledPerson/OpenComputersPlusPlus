package li.cil.oc.plusplus;

import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.jar.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual reobfuscated JAR, actual embedded codecs; no device/game/real media. */
public class PackagedAudioTest {
    @Test public void embeddedCodecsLoadPrivatelyAndDecodeAllThreeSyntheticFormats()throws Exception {
        File jar=new File(System.getProperty("ocpp.packagedJar"));assertTrue(jar.isFile());
        try(JarFile contents=new JarFile(jar,true)){
            Properties props=new Properties();try(InputStream in=contents.getInputStream(contents.getJarEntry("META-INF/ocpp-audio/runtime.properties"))){props.load(in);}
            assertEquals(2,props.size());
            for(String name:new String[]{"jlayer-1.0.1.jar","nayuki-flac-c9b8727b.jar"}){
                MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=contents.getInputStream(contents.getJarEntry("META-INF/ocpp-audio/"+name))){byte[] b=new byte[4096];int n;while((n=in.read(b))>=0)hash.update(b,0,n);}
                StringBuilder hex=new StringBuilder();for(byte b:hash.digest())hex.append(String.format("%02x",b&255));assertEquals(props.getProperty(name),hex.toString());
            }
            for(String name:new String[]{"CODEC-NOTICES.txt","LICENSE-JLayer-LGPL2.1.txt","COPYING.txt","COPYING.LESSER.txt"})assertNotNull(contents.getJarEntry("META-INF/ocpp-audio/"+name));
        }
        ClassLoader poison=new ClassLoader(ClassLoader.getSystemClassLoader().getParent()){
            @Override protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException {if(n.startsWith("javazoom.")||n.startsWith("io.nayuki.flac."))throw new AssertionError("codec leaked to parent "+n);return super.loadClass(n,resolve);}
            @Override public URL getResource(String n){if(n.startsWith("javazoom/"))try{return new URL(null,"poison:codec",new URLStreamHandler(){protected URLConnection openConnection(URL u){return new URLConnection(u){public void connect(){}public InputStream getInputStream(){throw new AssertionError("codec resource leaked to parent");}};}});}catch(Exception e){throw new AssertionError(e);}return super.getResource(n);}
        };
        try(URLClassLoader loader=new URLClassLoader(new URL[]{jar.toURI().toURL()},poison)){
            Class<?> bridge=loader.loadClass("li.cil.oc.plusplus.audio.AudioCodecs"),sink=loader.loadClass("li.cil.oc.plusplus.audio.PcmSink");
            Method decode=bridge.getMethod("decode",byte[].class,String.class,int.class,sink,BooleanSupplier.class);
            byte[][] fixtures={AudioCodecTest.wav(16,1,8000,new byte[32]),AudioCodecTest.mp3(),AudioCodecTest.flac()};String[] formats={"wav","mp3","flac"};int[] rates={8000,44100,8000},sizes={32,2304,32};
            for(int i=0;i<3;i++){final int index=i;final int[] sum={0};Object receiver=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{sink},(proxy,method,args)->{if(method.getName().equals("accept")){assertEquals(rates[index],((Number)args[0]).intValue());assertEquals(1,((Number)args[1]).intValue());byte[] pcm=(byte[])args[2];assertTrue(pcm.length<=8192);sum[0]+=pcm.length;}return null;});
                decode.invoke(null,fixtures[i],formats[i],1,receiver,(BooleanSupplier)()->false);assertEquals(sizes[i],sum[0]);
            }
        }
    }
}
