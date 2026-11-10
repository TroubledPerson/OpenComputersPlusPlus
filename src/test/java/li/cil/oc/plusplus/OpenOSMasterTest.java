package li.cil.oc.plusplus;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.io.*;
import li.cil.oc.api.fs.*;

/** Synthetic task-local media only; no world or host optical device. */
public class OpenOSMasterTest {
 private Path fixture()throws Exception{Path root=Paths.get(System.getProperty("ocpp.sourceRoot","."),"build","openos-fixtures");Files.createDirectories(root);return Files.createTempDirectory(root,"master-");}
 private byte[] read(OpticalFileSystem f,String p)throws Exception{int h=f.open(p,Mode.Read);try{byte[] b=new byte[1024*1024];int n=f.getHandle(h).read(b);return java.util.Arrays.copyOf(b,Math.max(n,0));}finally{f.getHandle(h).close();}}
 @Test public void masterBootFilesSeedSealPersistAndNeverUpgradeExistingMedium()throws Exception{
  Path path=fixture();OpticalFileSystem f=new OpticalFileSystem(path,false,32L*1024*1024,512);String hash;
  try{hash=OpenOSMaster.seed(f,512,OpenOSMaster.BUNDLED);assertTrue(hash.matches("[a-f0-9]{64}"));assertTrue(f.isReadOnly());assertTrue(f.exists("init.lua"));assertTrue(f.exists("lib/core/boot.lua"));assertTrue(f.spaceUsed()<1024*1024);assertTrue(new String(read(f,"init.lua"),"UTF-8").length()>10);try{OpenOSMaster.seed(f,512,OpenOSMaster.BUNDLED);fail();}catch(IOException expected){}}finally{f.close();}
  OpticalFileSystem again=new OpticalFileSystem(path,false,128L*1024*1024,512);try{assertTrue(again.isReadOnly());assertEquals(32L*1024*1024,again.spaceTotal());assertTrue(again.exists(".prop"));try{again.open("new",Mode.Write);fail();}catch(IOException expected){}}finally{again.close();}
 }
 @Test public void badAssetAndTooSmallMediaFailBeforeAnyFileIsWritten()throws Exception{
  for(boolean small:new boolean[]{true,false}){OpticalFileSystem f=new OpticalFileSystem(fixture(),false,small?1024:32L*1024*1024,512);try{try{OpenOSMaster.seed(f,512,small?OpenOSMaster.BUNDLED:n->n.endsWith("init.lua")?new ByteArrayInputStream(new byte[]{0}):OpenOSMaster.BUNDLED.open(n));fail();}catch(IOException expected){}assertFalse(f.isReadOnly());assertEquals(0,f.list("/").length);}finally{f.close();}}
 }
 @Test public void existingWritableContentsAreNotReplacedOrSealed()throws Exception{
  OpticalFileSystem f=new OpticalFileSystem(fixture(),false,32L*1024*1024,512);try{int h=f.open("keep.txt",Mode.Write);f.getHandle(h).write(new byte[]{1,2,3});f.getHandle(h).close();try{OpenOSMaster.seed(f,512,OpenOSMaster.BUNDLED);fail();}catch(IOException expected){}assertArrayEquals(new byte[]{1,2,3},read(f,"keep.txt"));assertFalse(f.isReadOnly());}finally{f.close();}
 }
 @Test public void recoveryVerifiesSealedBytesWithoutChangingFiles()throws Exception{
  Path path=fixture();OpticalFileSystem f=new OpticalFileSystem(path,false,32L*1024*1024,512);
  try{String expected=OpenOSMaster.seed(f,512,OpenOSMaster.BUNDLED);long used=f.spaceUsed();
   byte[] before=Files.readAllBytes(path.resolve("data/init.lua"));long modified=Files.getLastModifiedTime(path.resolve("data/init.lua")).toMillis();
   assertEquals(expected,OpenOSMaster.verifySealed(f,OpenOSMaster.BUNDLED));assertTrue(f.isReadOnly());assertEquals(used,f.spaceUsed());
   assertArrayEquals(before,Files.readAllBytes(path.resolve("data/init.lua")));assertEquals(modified,Files.getLastModifiedTime(path.resolve("data/init.lua")).toMillis());
  }finally{f.close();}
 }
 @Test public void recoveryRejectsUnsealedAndModifiedContentsWithoutRepairingThem()throws Exception{
  Path path=fixture();OpticalFileSystem f=new OpticalFileSystem(path,false,32L*1024*1024,512);
  try{try{OpenOSMaster.verifySealed(f,OpenOSMaster.BUNDLED);fail();}catch(IOException expected){}assertEquals(0,f.list("/").length);
   OpenOSMaster.seed(f,512,OpenOSMaster.BUNDLED);
  }finally{f.close();}
  byte[] bad="damaged fixture".getBytes("UTF-8");Files.write(path.resolve("data/init.lua"),bad);
  f=new OpticalFileSystem(path,false,32L*1024*1024,512);
  try{try{OpenOSMaster.verifySealed(f,OpenOSMaster.BUNDLED);fail();}catch(IOException expected){}assertArrayEquals(bad,Files.readAllBytes(path.resolve("data/init.lua")));assertTrue(f.isReadOnly());}finally{f.close();}
 }
 @Test public void recoveryRejectsAdditionalFilesEvenWhenBootFilesMatch()throws Exception{
  Path path=fixture();OpticalFileSystem f=new OpticalFileSystem(path,false,32L*1024*1024,512);
  try{OpenOSMaster.seed(f,512,OpenOSMaster.BUNDLED);}finally{f.close();}
  Files.write(path.resolve("data/keep.txt"),new byte[]{7,8});f=new OpticalFileSystem(path,false,32L*1024*1024,512);
  try{try{OpenOSMaster.verifySealed(f,OpenOSMaster.BUNDLED);fail();}catch(IOException expected){}assertArrayEquals(new byte[]{7,8},Files.readAllBytes(path.resolve("data/keep.txt")));}finally{f.close();}
 }
}
