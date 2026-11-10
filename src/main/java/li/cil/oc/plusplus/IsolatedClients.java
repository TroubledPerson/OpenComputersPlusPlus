package li.cil.oc.plusplus;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Fixed private libraries, not a Lua classloader or a permission bypass.
 * Java/OS permissions are unchanged. Existing Minecraft libraries stay in the parent.
 */
public final class IsolatedClients {
    private IsolatedClients() { }
    private static final String[] NAMES={"smbj-0.14.0.jar","commons-net-3.13.0.jar","bcprov-jdk18on-1.86.jar","slf4j-api-2.0.9.jar","mbassador-1.3.0.jar","asn-one-0.6.0.jar","commons-io-2.21.0.jar"};
    private static final String[] HASHES={"f5cd63af2f343bfa13d41dbf50cfd251d2b99c4118fbe82b0e94ee0f93d1cc14","9e88444afadc9467f0bf3d328f93b9538da35a9be05cdee430766e64fddbd6c5","2af190b300cbb0b35e248ccf5f4a06b6072030aeb3da7a98ec73abe5b4cb371f","0818930dc8d7debb403204611691da58e49d42c50b6ffcfdce02dadb7c3c2b6c","f065031a490c88c9b20d34f3c9c4eb6e0dffe329c090788c383574e1ac7c6bba","e4f70fd92849b52240048b8ebace0c9a17d3bb7b9c882b3c7778cec3458495f5","7d643a2afea8b058b762aa6fb90e5b256f6c729739f8b3784c3370ddc609e88d"};
    private static Class<?> backend;
    private static synchronized Class<?> backend() throws Exception {
        if(backend!=null) return backend;
        URL own=IsolatedClients.class.getProtectionDomain().getCodeSource().getLocation();
        String override=System.getProperty("ocpp.libraryDirectory"); // Trusted launch/test setting only; never supplied by a component.
        URL[] urls=new URL[NAMES.length+1]; urls[0]=own;
        File[] libraries=override==null?embeddedLibraries(new File(own.toURI())):developmentLibraries(new File(override));
        for(int i=0;i<NAMES.length;i++) {
            urls[i+1]=libraries[i].toURI().toURL();
        }
        URLClassLoader loader=new URLClassLoader(urls,IsolatedClients.class.getClassLoader()) {
            // Java 8 otherwise inherits a parent's Package object, including
            // its version/sealing metadata, even when class bytes are private.
            private final Map<String,Package> privatePackages=new HashMap<>();
            @Override protected Package getPackage(String name) {
                return privateClass(name+".")?privatePackages.get(name):super.getPackage(name);
            }
            @Override protected Package definePackage(String name,String specTitle,String specVersion,String specVendor,
                                                       String implTitle,String implVersion,String implVendor,URL sealBase) {
                Package value=super.definePackage(name,specTitle,specVersion,specVendor,implTitle,implVersion,implVendor,sealBase);
                if(privateClass(name+".")) privatePackages.put(name,value);
                return value;
            }
            @Override protected synchronized Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(!privateClass(name)) return super.loadClass(name,resolve);
                Class<?> type=findLoadedClass(name);
                if(type==null) type=findClass(name); // Fail closed; no fallback to incompatible global copies.
                if(resolve) resolveClass(type); return type;
            }
            @Override public URL getResource(String name) {
                return privateResource(name)?findResource(name):super.getResource(name);
            }
            @Override public Enumeration<URL> getResources(String name) throws IOException {
                return privateResource(name)?findResources(name):super.getResources(name);
            }
        };
        backend=loader.loadClass("li.cil.oc.plusplus.RemoteClients"); return backend;
    }
    private static boolean privateClass(String name) {
        return name.startsWith("li.cil.oc.plusplus.RemoteClients") || name.startsWith("com.hierynomus.") || name.startsWith("org.apache.commons.net.") || name.startsWith("org.apache.commons.io.") || name.startsWith("org.bouncycastle.") || name.startsWith("org.slf4j.") || name.startsWith("net.engio.");
    }
    private static boolean privateResource(String name) {
        if(name.startsWith("META-INF/services/")) return privateClass(name.substring("META-INF/services/".length()));
        return privateClass(name.replace('/','.'));
    }
    private static String sha256(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream stream=new FileInputStream(file)) {
            byte[] bytes=new byte[65536]; int count;
            while((count=stream.read(bytes))>=0) digest.update(bytes,0,count);
        }
        StringBuilder hex=new StringBuilder();
        for(byte value:digest.digest()) hex.append(String.format("%02x",value&255));
        return hex.toString();
    }
    private static void verify(File file,int index) throws Exception {
        if(!Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS) || !HASHES[index].equals(sha256(file)))
            throw new IOException("private dependency checksum mismatch: "+NAMES[index]);
    }
    private static File[] developmentLibraries(File directory) throws Exception {
        File[] result=new File[NAMES.length];
        for(int i=0;i<NAMES.length;i++) { result[i]=new File(directory,NAMES[i]);verify(result[i],i); }
        return result;
    }
    /** Fixed bundled files only. No URL, path or class name comes from Lua.
     * The trusted mod directory must be writable. Never overwrite/repair an
     * existing file, elevate, change permissions, or download a missing library.
     */
    private static File[] embeddedLibraries(File ownJar) throws Exception {
        if(!ownJar.isFile()) throw new IOException("packaged mod JAR required; development builds need the explicit library override");
        Path parent=ownJar.getCanonicalFile().toPath().getParent().toRealPath();
        Path root=parent.resolve(".ocpp-runtime");
        checkedDirectory(root);
        Path cache=root.resolve("sha256");
        checkedDirectory(cache);
        File[] result=new File[NAMES.length];
        long written=0;
        try(JarFile source=new JarFile(ownJar,true)) {
            for(int i=0;i<NAMES.length;i++) {
                Path target=cache.resolve(HASHES[i]+"-"+NAMES[i]);
                if(!cache.toRealPath().startsWith(parent)) throw new IOException("private cache escapes the mod directory");
                if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
                    JarEntry entry=source.getJarEntry("META-INF/ocpp-runtime/"+NAMES[i]);
                    if(entry==null || entry.getSize()<0 || entry.getSize()>16*1024*1024) throw new IOException("missing or oversized embedded dependency: "+NAMES[i]);
                    // CREATE_NEW prevents overwrite. A failed/partial extraction
                    // is preserved and rejected on subsequent attempts.
                    try(InputStream input=source.getInputStream(entry);
                        OutputStream output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                        byte[] bytes=new byte[65536]; int count; long current=0;
                        while((count=input.read(bytes))>=0) {
                            current+=count;written+=count;
                            if(current>16*1024*1024 || written>32*1024*1024) throw new IOException("private extraction limit exceeded");
                            output.write(bytes,0,count);
                        }
                    }
                }
                if(!target.toRealPath().startsWith(cache.toRealPath())) throw new IOException("private cache file escapes its directory");
                result[i]=target.toFile(); verify(result[i],i);
            }
        }
        return result;
    }
    private static void checkedDirectory(Path path) throws IOException {
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(path);
        if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path))
            throw new IOException("private cache directory must not be a link or redirection");
    }
    @SuppressWarnings("unchecked")
    public static Callable<Object> task(Map<String,Object> values,Function<String,InetAddress[]> resolver,BooleanSupplier authorized) throws Exception {
        return (Callable<Object>)backend().getMethod("task",Map.class,Function.class,BooleanSupplier.class).invoke(null,values,resolver,authorized);
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> securityDefaults() throws Exception {
        return (Map<String,Object>)backend().getMethod("securityDefaults").invoke(null);
    }
}
