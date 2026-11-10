package li.cil.oc.plusplus;
import java.nio.file.*;import java.io.*;import java.util.zip.*;import org.junit.Test;import static org.junit.Assert.*;
import li.cil.repack.org.luaj.vm2.lib.jse.JsePlatform;
public class OpticalBootWaitTest {
 private String source()throws Exception{return new String(Files.readAllBytes(new File(System.getProperty("ocpp.sourceRoot","."),"src/main/resources/assets/opencomputers/lua/machine.lua").toPath()),"UTF-8");}
 private String gate()throws Exception{String s=source();return s.substring(s.indexOf("-- OC++ optical readiness gate:"),s.indexOf("-- End OC++ optical readiness gate.")+"-- End OC++ optical readiness gate.".length());}
 private void run(String status,String checks)throws Exception{
  String fixture="local t,n=0,0; local computer={uptime=function() return t end}; local libcomponent={}; libcomponent.list=function() local done=false;return function() if not done then done=true;return 'drive' end end end;libcomponent.invoke=function() n=n+1; "+status+" end;\n".replace("\n","\n");
  fixture=fixture.replace("\n","\n");
  String code=fixture+"\n"+gate()+"\nlocal booted=false;local co=coroutine.create(function() waitForOpticalMedia();booted=true end);local ok,why=coroutine.resume(co);assert(ok,why);"+checks;
  code=code.replace("\n","\n");
  JsePlatform.standardGlobals().load(code.replace("\n",String.valueOf((char)10)),"optical-boot-fixture").call();
 }
 @Test public void coldCdWaitsBeforeTheSingleBootScan()throws Exception{run("return t < 2.55 and 'starting' or 'ready'","assert(not booted);while coroutine.status(co)~='dead' do t=t+0.05;local a,b=coroutine.resume(co);assert(a,b) end;assert(booted and t>=2.55 and t<2.7)");}
 @Test public void emptyAndOffDoNotWait()throws Exception{for(String s:new String[]{"empty","off","ready"})run("return '"+s+"'","assert(booted and t==0)");}
 @Test public void removalOrShutdownCancelsWait()throws Exception{for(String s:new String[]{"empty","off"})run("return t<0.1 and 'starting' or '"+s+"'","t=0.1;local a,b=coroutine.resume(co);assert(a,b);assert(booted)");}
 @Test public void unavailableCallbackDoesNotHang()throws Exception{run("error('removed')","assert(booted)");}
 @Test public void stuckStartingTimesOutBoundedly()throws Exception{run("return 'starting'","while coroutine.status(co)~='dead' do t=t+0.05;local a,b=coroutine.resume(co);assert(a,b) end;assert(booted and t>=6 and t<6.1 and n<123)");}
 @Test public void noDriveAndOversizedTopologyAreBounded()throws Exception{
  for(String iter:new String[]{"return function() return nil end","local i=0;return function() i=i+1;return tostring(i) end"}){String s="local computer={uptime=function() return 0 end};local libcomponent={list=function() "+iter+" end,invoke=function() return 'starting' end};"+gate()+";waitForOpticalMedia()";JsePlatform.standardGlobals().load(s,"bounded-optical-list").call();}
 }
 @Test public void onlyBootstrapWrapperChangesAndInstalledEepromBytesAreNeverWritten()throws Exception{
  String s=source(),original;File p=new File(System.getProperty("ocpp.sourceRoot","."));
  try(ZipFile z=new ZipFile(new File(p.getParentFile(),"deliverables/onejar-dev.32/OpenComputersPlusPlus-onejar-dev.32-source.zip"));InputStream in=z.getInputStream(z.getEntry("src/main/resources/assets/opencomputers/lua/machine.lua"));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);original=new String(out.toByteArray(),"UTF-8");}
  String inverse=s.replace(gate()+"\n\n","").replace("return coroutine.create(function() waitForOpticalMedia(); return bios() end), {n=0}","return coroutine.create(bios), {n=0}");assertEquals(original.replace("\r\n","\n"),inverse.replace("\r\n","\n"));JsePlatform.standardGlobals().load(s,"machine-syntax-only");
 }
}
