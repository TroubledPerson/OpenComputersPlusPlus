package li.cil.oc.plusplus;
import java.nio.file.*;import java.io.*;import org.junit.Test;import static org.junit.Assert.*;
import li.cil.oc.api.fs.Mode;import li.cil.oc.api.fs.Handle;
/** Scratch-only tests of the actual production mount registry and data/handle lifecycle. */
public class OpticalReservationTest {
 private Path root()throws Exception{return Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")),"ocpp-ownership-");}
 private void blocked(Path p)throws Exception{try{OpticalFileSystem unexpected=new OpticalFileSystem(p,true,32768,0);unexpected.close();fail("duplicate identity admitted");}catch(IOException expected){}}
 @Test public void inactiveRepeatedCloseCannotReleaseAnotherMount()throws Exception{
  Path p=root();OpticalFileSystem a=new OpticalFileSystem(p,true,32768,0);a.close();OpticalFileSystem b=new OpticalFileSystem(p,true,32768,0);
  try{for(int i=0;i<100;i++)a.close();blocked(p);int h=b.open("kept",Mode.Write);b.getHandle(h).write(new byte[]{1,2,3});b.getHandle(h).close();assertEquals(3,b.size("kept"));}finally{b.close();a.close();}
  a.reopen();assertEquals(3,a.size("kept"));a.close();
 }
 @Test public void failedReopenAndCleanupDoNotReleaseTheSuccessfulOwner()throws Exception{
  Path p=root();OpticalFileSystem a=new OpticalFileSystem(p,true,32768,0);a.close();OpticalFileSystem b=new OpticalFileSystem(p,true,32768,0);
  try{try{a.reopen();fail("duplicate reopen admitted");}catch(IOException expected){}a.close();a.close();blocked(p);assertTrue(b.makeDirectory("still-owned"));}finally{b.close();a.close();}
 }
 @Test public void suspensionInvalidatesHandlesAndReopenPreservesData()throws Exception{
  OpticalFileSystem a=new OpticalFileSystem(root(),true,32768,0);int h=a.open("kept",Mode.Write);Handle stale=a.getHandle(h);stale.write(new byte[]{4,5});a.close();a.close();assertNull(a.getHandle(h));
  try{stale.write(new byte[]{6});fail("stale handle accepted");}catch(IOException expected){}
  a.reopen();assertEquals(2,a.size("kept"));int r=a.open("kept",Mode.Read);byte[] bytes=new byte[2];assertEquals(2,a.getHandle(r).read(bytes));assertArrayEquals(new byte[]{4,5},bytes);a.close();
 }
}
