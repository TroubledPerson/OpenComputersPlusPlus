package li.cil.oc.plusplus;

import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import com.google.gson.*;
import net.minecraftforge.fml.common.versioning.*;
import net.minecraftforge.fml.common.Mod;
import java.nio.FloatBuffer;
import org.lwjgl.BufferChecks;
import org.lwjgl.LWJGLUtil;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual reobfuscated main plus old and new optional addons. No game or external process. */
public class PackagedApi2Test {
    @Test public void actualPackagedBiosReportEnforcesCapsAndRunsOnlyTinySyntheticDiagnostics()throws Exception {
        File file=new File(System.getProperty("ocpp.packagedJar"));
        try(URLClassLoader loader=new URLClassLoader(new URL[]{file.toURI().toURL()},null)){
            Class<?> type=loader.loadClass("li.cil.oc.plusplus.BiosReport");assertEquals(loader,type.getClassLoader());
            int size=type.getField("SIZE").getInt(null);assertEquals(38,size);int[] source=new int[size];source[0]=Integer.MAX_VALUE;source[14]=Integer.MAX_VALUE;
            int[] report=(int[])type.getMethod("normalize",int[].class).invoke(null,(Object)source);assertEquals(256,report[0]);assertEquals(Integer.MAX_VALUE,report[14]);assertNotSame(source,report);
            assertEquals(65536,type.getField("SCRATCH_BYTES").getInt(null));assertEquals(1,type.getMethod("memorySelfTest").invoke(null));assertEquals(1,type.getMethod("inputSelfTest").invoke(null));
            Class<?> words=loader.loadClass("li.cil.oc.plusplus.BiosReport$Words");Object assembly=words.newInstance();Method accept=words.getMethod("accept",int.class,int.class);assertNull(accept.invoke(assembly,type.getField("REPORT_COMMIT").getInt(null),1));
            for(int i=0;i<size;i++)for(int part=0;part<2;part++)assertNull(accept.invoke(assembly,12+i*2+part,(int)(short)(report[i]>>>(16*part))));
            assertArrayEquals(report,(int[])accept.invoke(assembly,type.getField("REPORT_COMMIT").getInt(null),2));
        }
    }
    @Test public void packagedPreviewAllocationSatisfiesLwjglWithoutAContext()throws Exception {
        File file=new File(System.getProperty("ocpp.packagedJar"));
        final String target="li.cil.oc.client.renderer.tileentity.PreviewScreenRenderState$";
        try(URLClassLoader loader=new URLClassLoader(new URL[]{file.toURI().toURL()},PackagedApi2Test.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(!name.equals(target))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;}
            }
        }){
            Class<?> factory=Class.forName(target,true,loader);assertEquals("actual packaged factory",loader,factory.getClassLoader());
            Object module=factory.getField("MODULE$").get(null);
            FloatBuffer buffer=(FloatBuffer)factory.getMethod("colorQueryBuffer").invoke(module);
            assertTrue("validation remains enabled",LWJGLUtil.CHECKS);assertTrue(buffer.isDirect());assertEquals(16,buffer.remaining());BufferChecks.checkBuffer(buffer,16);
        }
    }
    private static Map<String,Object> annotation(File file,String entryName)throws Exception {
        final String target=entryName.substring(0,entryName.length()-6).replace('/','.');
        try(JarFile jar=new JarFile(file,true)) {JarEntry entry=jar.getJarEntry(entryName);assertNotNull("Forge entrypoint "+entryName,entry);assertTrue(entry.getSize()>0&&entry.getSize()<1024*1024);}
        // Child-first ONLY for the inspected class, so compiled test outputs cannot mask stale JAR bytes.
        try(URLClassLoader loader=new URLClassLoader(new URL[]{file.toURI().toURL()},PackagedApi2Test.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(!name.equals(target))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){Class<?> found=findLoadedClass(name);if(found==null)found=findClass(name);if(resolve)resolveClass(found);return found;}
            }
        }){
            Class<?> type=Class.forName(target,false,loader);assertEquals("actual inspected JAR loader",loader,type.getClassLoader());
            Mod mod=type.getAnnotation(Mod.class);assertNotNull("actual Forge annotation",mod);
            Map<String,Object> values=new HashMap<>();values.put("modid",mod.modid());values.put("version",mod.version());values.put("dependencies",mod.dependencies());return values;
        }
    }
    private static JsonObject metadata(JarFile jar)throws Exception {
        JarEntry entry=jar.getJarEntry("mcmod.info");assertNotNull(entry);
        try(Reader in=new InputStreamReader(jar.getInputStream(entry),"UTF-8")){return new JsonParser().parse(in).getAsJsonArray().get(0).getAsJsonObject();}
    }
    private static String consistentForgeVersion(File file)throws Exception {
        try(JarFile jar=new JarFile(file,true)){
            JsonObject meta=metadata(jar);assertEquals("opencomputers",meta.get("modid").getAsString());String version=meta.get("version").getAsString();
            for(String name:new String[]{"li/cil/oc/OpenComputers.class","li/cil/oc/OpenComputers$.class"}){
                Map<String,Object> mod=annotation(file,name);assertEquals("opencomputers",mod.get("modid"));assertEquals("compiled Forge version "+name,version,mod.get("version"));
            }
            return version;
        }
    }
    @Test public void packagedForgeAnnotationsMetadataAndCompanionRangeAgree()throws Exception {
        File source=new File(System.getProperty("ocpp.api2SourceRoot"));Properties properties=new Properties();
        try(InputStream in=new FileInputStream(new File(source,"gradle.properties"))){properties.load(in);}
        String actual=consistentForgeVersion(new File(System.getProperty("ocpp.packagedJar")));
        assertEquals("packaged version equals declared build input",properties.getProperty("mod_version"),actual);
        try(JarFile addon=new JarFile(new File(source,"build/libs/OCVMIntegration-offline-dev.2.jar"),true)){
            Map<String,Object> mod=annotation(new File(source,"build/libs/OCVMIntegration-offline-dev.2.jar"),"li/cil/oc/vm/companion/OCVMIntegration.class");assertEquals(metadata(addon).get("version").getAsString(),mod.get("version"));
            String dependency=(String)mod.get("dependencies");assertEquals("required-after:opencomputers@[1.8.10-ocpp-dev.20,)",dependency);
            VersionRange range=VersionRange.createFromVersionSpec(dependency.substring(dependency.indexOf('@')+1));
            assertTrue("Forge accepts delivered main version",range.containsVersion(new DefaultArtifactVersion(actual)));
            assertFalse("Do not hide mismatch by lowering requirement",range.containsVersion(new DefaultArtifactVersion("1.8.10-ocpp-dev.19")));
        }
    }
    @Test public void preservedDev20ArtifactDemonstratesTheOriginalLoaderMismatch()throws Exception {
        File source=new File(System.getProperty("ocpp.api2SourceRoot"));File old=new File(source.getParentFile(),"deliverables/onejar-dev.20-r2/OpenComputersPlusPlus-onejar-dev.20.jar");assertTrue(old.isFile());
        boolean rejected=false;try{consistentForgeVersion(old);}catch(AssertionError mismatch){assertTrue(mismatch.getMessage().contains("compiled Forge version"));rejected=true;}
        assertTrue("known broken artifact must fail the same real-byte check",rejected);
    }
    @Test public void finalJarKeepsOldAddonAbiAndNewAddonCannotExecuteGuests()throws Exception {
        File jar=new File(System.getProperty("ocpp.packagedJar"));
        File source=new File(System.getProperty("ocpp.api2SourceRoot"));
        File old=new File(source.getParentFile(),"deliverables/onejar-dev.19/OCVMIntegration-offline-dev.1.jar");
        File next=new File(source,"build/libs/OCVMIntegration-offline-dev.2.jar");
        assertTrue(jar.isFile());assertTrue(old.isFile());assertTrue(next.isFile());
        URL gson=com.google.gson.Gson.class.getProtectionDomain().getCodeSource().getLocation();
        ClassLoader extensions=ClassLoader.getSystemClassLoader().getParent();
        try(URLClassLoader loader=new URLClassLoader(new URL[]{jar.toURI().toURL(),old.toURI().toURL(),gson},extensions)){
            Class<?> api=loader.loadClass("li.cil.oc.api.vm.SuperBios");assertEquals(loader,api.getClassLoader());
            Class<?> provider=loader.loadClass("li.cil.oc.api.vm.SuperBios$Provider");Object instance=loader.loadClass("li.cil.oc.vm.companion.OCVMIntegration$OfflineProvider").newInstance();
            api.getMethod("register",provider).invoke(null,instance);assertEquals(1,api.getMethod("statusCode").invoke(null));assertEquals(false,api.getMethod("mayExecuteGuest").invoke(null));api.getMethod("unregister",provider).invoke(null,instance);
        }
        try(URLClassLoader loader=new URLClassLoader(new URL[]{jar.toURI().toURL(),next.toURI().toURL(),gson},extensions)){
            Class<?> api=loader.loadClass("li.cil.oc.api.vm.v2.ProtocolV2"),provider=loader.loadClass("li.cil.oc.api.vm.v2.ProtocolV2$Provider");assertEquals(loader,api.getClassLoader());
            Object instance=loader.loadClass("li.cil.oc.vm.companion.OCVMIntegration$OfflineProvider2").newInstance();api.getMethod("register",provider).invoke(null,instance);assertEquals("OFFLINE_PROVIDER",api.getMethod("status").invoke(null).toString());assertEquals(false,api.getMethod("mayExecuteGuest").invoke(null));
            try{api.getMethod("decode",byte[].class).invoke(null,new byte[16385]);fail("wire cap required");}catch(InvocationTargetException e){assertTrue(e.getCause() instanceof IllegalArgumentException);}
            Class<?> lifecycle=loader.loadClass("li.cil.oc.api.vm.v2.LifecycleV2");Object runtime=lifecycle.getMethod("productionUnavailable").invoke(null);assertEquals("RUNTIME_DISABLED",lifecycle.getMethod("start").invoke(runtime).toString());
            api.getMethod("unregister",provider).invoke(null,instance);
        }
    }
}
