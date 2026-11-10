package li.cil.oc.plusplus;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Loads the actual reobfuscated installation JAR with no game or service calls. */
public class PackagedClientTest {
    @Test public void packagedCaseFacesBindContinuousPanelsAndPreservedHardware()throws Exception {
        File root=new File(System.getProperty("ocpp.api2SourceRoot"));
        try(JarFile jar=new JarFile(installed(),true)){
            for(String face:new String[]{"front","side"}){
                java.awt.image.BufferedImage nativeImage=javax.imageio.ImageIO.read(new File(root,"src/main/resources/assets/opencomputers/textures/blocks/case_"+face+".png"));
                java.awt.image.BufferedImage expected=face.equals("front")?PreviewSprites.caseFrontSnow(nativeImage):PreviewSprites.caseSideSnow(nativeImage);
                JarEntry entry=jar.getJarEntry("assets/opencomputers/textures/blocks/ocpp_vm_case_"+face+".png");assertNotNull(entry);
                try(InputStream in=jar.getInputStream(entry)){
                    java.awt.image.BufferedImage actual=javax.imageio.ImageIO.read(in);assertEquals(16,actual.getWidth());assertEquals(16,actual.getHeight());
                    for(int y=0;y<16;y++)for(int x=0;x<16;x++)assertEquals(face+" packaged pixel "+x+","+y,expected.getRGB(x,y),actual.getRGB(x,y));
                }
            }
        }
    }
    private static final String SPI="META-INF/services/org.slf4j.spi.SLF4JServiceProvider";
    private static final String[] NAMES={"smbj-0.14.0.jar","commons-net-3.13.0.jar","bcprov-jdk18on-1.86.jar","slf4j-api-2.0.9.jar","mbassador-1.3.0.jar","asn-one-0.6.0.jar","commons-io-2.21.0.jar"};
    private File installed() { File file=new File(System.getProperty("ocpp.packagedJar")); assertTrue("Build the packaged JAR first",file.isFile()); return file; }
    private Path fixtureRoot() throws IOException { Path path=Paths.get(System.getProperty("ocpp.fixtureRoot")); Files.createDirectories(path);return path; }
    private byte[] hash(InputStream stream) throws Exception { try(InputStream input=stream) { MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[65536];int count;while((count=input.read(bytes))>=0)digest.update(bytes,0,count);return digest.digest(); } }
    @SuppressWarnings("unchecked")
    private Map<String,Object> defaults(URLClassLoader loader) throws Exception {
        return (Map<String,Object>)loader.loadClass("li.cil.oc.plusplus.IsolatedClients").getMethod("securityDefaults").invoke(null);
    }
    private URLClassLoader loader(File jar,ClassLoader parent) throws Exception { return new URLClassLoader(new URL[]{jar.toURI().toURL()},parent); }

    @Test public void storesEveryReviewedArchiveByteForByteIncludingSignaturesAndServices() throws Exception {
        try(JarFile jar=new JarFile(installed(),true)) {
            for(String name:NAMES) {
                JarEntry entry=jar.getJarEntry("META-INF/ocpp-runtime/"+name);assertNotNull(name,entry);
                File original=new File(System.getProperty("ocpp.originalLibraries"),name);
                assertArrayEquals(name,hash(new FileInputStream(original)),hash(jar.getInputStream(entry)));
            }
            assertNotNull(jar.getJarEntry("META-INF/ocpp-runtime/THIRD-PARTY-NOTICES.txt"));
            assertNull("Provider classes must not be flattened into the mod",jar.getJarEntry("org/bouncycastle/jce/provider/BouncyCastleProvider.class"));
            assertNull("No global Class-Path injection",jar.getManifest().getMainAttributes().getValue("Class-Path"));
        }
    }

    @Test public void loadsFromOnlyTheInstalledJarWithoutSidecarOrDevelopmentOverride() throws Exception {
        assertNull(System.getProperty("ocpp.libraryDirectory"));
        try(URLClassLoader loader=loader(installed(),null)) {
            Map<String,Object> values=defaults(loader);
            assertEquals("2.21.0",values.get("commonsIoVersion"));
            assertEquals(true,values.get("ftpsHostnameChecking"));
            assertEquals(true,values.get("smbSigning"));assertEquals(true,values.get("smbEncryption"));assertEquals(false,values.get("smbDfs"));
        }
    }

    @Test public void reusesVerifiedCachedArchivesWithoutOverwritingThem() throws Exception {
        try(URLClassLoader first=loader(installed(),null)) { defaults(first); }
        File cache=new File(installed().getParentFile(),".ocpp-runtime/sha256");
        File[] files=cache.listFiles((dir,name)->name.endsWith(".jar"));assertNotNull(files);assertEquals(7,files.length);
        Map<String,Long> times=new HashMap<>();for(File file:files)times.put(file.getName(),file.lastModified());
        try(URLClassLoader second=loader(installed(),null)) { defaults(second); }
        for(File file:files)assertEquals(times.get(file.getName()).longValue(),file.lastModified());
    }

    @Test public void isolatesMinecraftCommonsIoAndRejectsForeignPrivateServiceResources() throws Exception {
        final URL fake=Files.write(Files.createTempFile(fixtureRoot(),"foreign-service-",".txt"),"invalid.foreign.Provider\n".getBytes(StandardCharsets.UTF_8)).toUri().toURL();
        File legacy=new File(System.getProperty("ocpp.legacyCommonsIo"));assertTrue(legacy.isFile());
        try(URLClassLoader parent=new URLClassLoader(new URL[]{legacy.toURI().toURL()},null) {
            @Override public Enumeration<URL> getResources(String name) throws IOException { return SPI.equals(name)?Collections.enumeration(Collections.singletonList(fake)):super.getResources(name); }
            @Override public URL getResource(String name) { return SPI.equals(name)?fake:super.getResource(name); }
        }) {
            Class<?> old=parent.loadClass("org.apache.commons.io.IOUtils");assertEquals("2.5",old.getPackage().getImplementationVersion());
            assertTrue(parent.getResources(SPI).hasMoreElements());
            try(URLClassLoader loader=loader(installed(),parent)) {
                Map<String,Object> values=defaults(loader);
                assertEquals("2.21.0",values.get("commonsIoVersion"));
                assertNotEquals(parent.toString(),values.get("commonsIoLoader"));
                assertEquals(0,values.get("privateSlf4jServiceResources"));
                assertSame(old,parent.loadClass("org.apache.commons.io.IOUtils"));
            }
        }
    }

    /** Tiny fixtures copy only our loader classes; never alter originals or cache files. */
    private File malformed(boolean corrupt) throws Exception {
        Path directory=Files.createTempDirectory(fixtureRoot(),corrupt?"corrupt-":"missing-");
        File result=directory.resolve("fixture.jar").toFile();
        try(JarFile original=new JarFile(installed());JarOutputStream output=new JarOutputStream(new FileOutputStream(result))) {
            Enumeration<JarEntry> entries=original.entries();byte[] bytes=new byte[4096];
            while(entries.hasMoreElements()) {
                JarEntry entry=entries.nextElement();
                if(entry.getName().startsWith("li/cil/oc/plusplus/IsolatedClients") && entry.getName().endsWith(".class")) {
                    output.putNextEntry(new JarEntry(entry.getName()));
                    try(InputStream input=original.getInputStream(entry)) {int count;while((count=input.read(bytes))>=0)output.write(bytes,0,count);}
                    output.closeEntry();
                }
            }
            if(corrupt) {output.putNextEntry(new JarEntry("META-INF/ocpp-runtime/"+NAMES[0]));output.write(0);output.closeEntry();}
        }
        return result;
    }
    private void rejects(File jar,String expected) throws Exception {
        try(URLClassLoader loader=loader(jar,null)) {
            try {defaults(loader);fail("Invalid packaged dependency must not load");}
            catch(java.lang.reflect.InvocationTargetException failure) {
                Throwable cause=failure.getCause();assertTrue(cause instanceof IOException);assertTrue(cause.toString(),cause.getMessage().contains(expected));
            }
        }
    }
    @Test public void missingEmbeddedLibraryFailsClosedWithoutDownloading() throws Exception {rejects(malformed(false),"missing or oversized embedded dependency");}
    @Test public void corruptedEmbeddedLibraryFailsClosedAndPreservesTheFailedFile() throws Exception {rejects(malformed(true),"checksum mismatch");}
}
