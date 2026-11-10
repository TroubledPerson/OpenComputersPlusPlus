package li.cil.oc.plusplus;

import java.io.*;import java.net.*;import java.util.*;import java.util.jar.*;import java.security.MessageDigest;
import com.google.gson.*;import net.minecraftforge.fml.common.Mod;import org.junit.Test;import static org.junit.Assert.*;

/** Inspect actual addon/main JARs. No Forge startup, process, IPC, guest, display or disk attachment. */
public class PackagedSimulatedAddonTest {
 private File addon(){return new File(System.getProperty("ocpp.simulatedAddon"));}
 private String hash(File p)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(p)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder out=new StringBuilder();for(byte x:d.digest())out.append(String.format("%02x",x&255));return out.toString();}
 @Test public void newAddonMetadataAndCompiledAnnotationAgree()throws Exception{
  File jar=addon();assertTrue(jar.isFile());try(JarFile z=new JarFile(jar);Reader r=new InputStreamReader(z.getInputStream(z.getJarEntry("mcmod.info")),"UTF-8")){
   JsonObject meta=new JsonParser().parse(r).getAsJsonArray().get(0).getAsJsonObject();assertEquals("0.3.0-dev.3",meta.get("version").getAsString());
   try(URLClassLoader l=loader()){
    Class<?> type=l.loadClass("li.cil.oc.vm.simulated.OCVMIntegrationSimulated");assertEquals(l,type.getClassLoader());Mod mod=type.getAnnotation(Mod.class);assertNotNull(mod);assertEquals(meta.get("version").getAsString(),mod.version());assertEquals("ocpp_vmintegration",mod.modid());assertEquals("required-after:opencomputers@[1.8.10-ocpp-dev.20,)",mod.dependencies());
   }
  }
 }
 private URLClassLoader loader()throws Exception{return new URLClassLoader(new URL[]{addon().toURI().toURL(),new File(System.getProperty("ocpp.packagedJar")).toURI().toURL()},getClass().getClassLoader()){
  protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException{if(!n.startsWith("li.cil.oc.vm.simulated.")&&!n.startsWith("li.cil.oc.api.vm."))return super.loadClass(n,resolve);synchronized(getClassLoadingLock(n)){Class<?> c=findLoadedClass(n);if(c==null)c=findClass(n);if(resolve)resolveClass(c);return c;}}
 };}
 @Test public void actualAddonProvidersStayOfflineAndMainBaselineIsUnchanged()throws Exception{
  assertEquals("256598a0b74e26354a158ce8e87784ccd18bd0c35d055b66ea02a1ef3c252d45",hash(new File(System.getProperty("ocpp.packagedJar"))));
  File source=new File(System.getProperty("ocpp.api2SourceRoot"));assertEquals("18d0bca754b7bdca314bd103eaca2552319815c5b6b5b538e0f395f5810ff8a3",hash(new File(source.getParentFile(),"deliverables/onejar-dev.33/OCVMIntegration-offline-dev.2.jar")));
  try(URLClassLoader l=loader()){
   Class<?> api=l.loadClass("li.cil.oc.api.vm.v2.ProtocolV2"),provider=l.loadClass("li.cil.oc.api.vm.v2.ProtocolV2$Provider"),session=l.loadClass("li.cil.oc.api.vm.v2.SessionV2");
   Object p=l.loadClass("li.cil.oc.vm.simulated.OCVMIntegrationSimulated$Provider2").newInstance();assertEquals(provider,p.getClass().getMethod("openOffline",session).getDeclaringClass());api.getMethod("register",provider).invoke(null,p);assertEquals("OFFLINE_PROVIDER",api.getMethod("status").invoke(null).toString());assertEquals(false,api.getMethod("mayExecuteGuest").invoke(null));api.getMethod("unregister",provider).invoke(null,p);
   Class<?> v1=l.loadClass("li.cil.oc.api.vm.SuperBios"),p1=l.loadClass("li.cil.oc.api.vm.SuperBios$Provider");Object old=l.loadClass("li.cil.oc.vm.simulated.OCVMIntegrationSimulated$Provider1").newInstance();v1.getMethod("register",p1).invoke(null,old);assertEquals(false,v1.getMethod("mayExecuteGuest").invoke(null));v1.getMethod("unregister",p1).invoke(null,old);
  }
 }
 @Test public void addonContainsNoNativePayloadOrHostExecutionClasses()throws Exception{
  int classes=0;try(JarFile z=new JarFile(addon())){Enumeration<JarEntry> entries=z.entries();while(entries.hasMoreElements()){JarEntry e=entries.nextElement();String n=e.getName();assertFalse(n.matches(".*\\.(exe|dll|so|dylib|qcow2|vdi|rom|fd|jar)$"));if(!n.endsWith(".class"))continue;classes++;assertTrue(n.startsWith("li/cil/oc/vm/simulated/"));try(InputStream in=z.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int k;while((k=in.read(b))!=-1)out.write(b,0,k);String constants=new String(out.toByteArray(),"ISO-8859-1");for(String forbidden:new String[]{"java/lang/ProcessBuilder","java/lang/Runtime","java/net/Socket","java/net/ServerSocket","java/io/FileInputStream","java/io/FileOutputStream","java/nio/file/Files","org/lwjgl"}){boolean found=forbidden.equals("java/lang/Runtime")?constants.matches("(?s).*java/lang/Runtime(?![A-Za-z0-9_$]).*"):constants.contains(forbidden);assertFalse(forbidden,found);}}}}assertTrue(classes>=10);
 }
}
