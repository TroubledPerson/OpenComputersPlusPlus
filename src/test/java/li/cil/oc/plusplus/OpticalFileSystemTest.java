package li.cil.oc.plusplus;

import li.cil.oc.api.fs.Handle;
import li.cil.oc.api.fs.Mode;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** All fixtures stay in this project's build/ tree; no game/world/private files. */
public class OpticalFileSystemTest {
    private Path directory()throws IOException{return Files.createDirectories(Paths.get("build","optical-fixtures","dev10-"+UUID.randomUUID()).toAbsolutePath());}
    private void failure(IO action)throws Exception{try{action.run();fail("expected rejection");}catch(IOException expected){}}
    private interface IO{void run()throws Exception;}
    private void put(OpticalFileSystem fs,String name,byte[] bytes)throws Exception{int h=fs.open(name,Mode.Write);fs.getHandle(h).write(bytes);fs.getHandle(h).close();}
    private byte[] get(OpticalFileSystem fs,String name)throws Exception{int h=fs.open(name,Mode.Read);byte[] bytes=new byte[(int)fs.size(name)];assertEquals(bytes.length,fs.getHandle(h).read(bytes));fs.getHandle(h).close();return bytes;}

    @Test public void exact32MiBQuotaIncludesMetadataAndRejectsOverflowAtomically()throws Exception{
        try(Lease lease=new Lease(directory(),false,OpticalFileSystem.DEFAULT_CAPACITY,512)){
            OpticalFileSystem fs=lease.fs;assertEquals(33554432,fs.spaceTotal());assertEquals(512,fs.spaceUsed());
            int id=fs.open("full",Mode.Write);Handle h=fs.getHandle(id);byte[] chunk=new byte[65536];Arrays.fill(chunk,(byte)0x5a);
            long remaining=33554432L-1024;
            while(remaining>0){int n=(int)Math.min(chunk.length,remaining);h.write(n==chunk.length?chunk:Arrays.copyOf(chunk,n));remaining-=n;}
            assertEquals(33554432,fs.spaceUsed());long length=h.length();failure(()->h.write(new byte[]{1}));assertEquals(length,h.length());h.close();
            failure(()->fs.open("overflow",Mode.Write));assertFalse(fs.makeDirectory("overflow"));assertFalse(fs.exists("overflow"));
        }
    }
    @Test public void recordingSessionAllowsMultipleEditsThenSealRejectsAllMutations()throws Exception{
        try(Lease lease=new Lease(directory(),false,4096,16)){
            OpticalFileSystem fs=lease.fs;put(fs,"a",new byte[]{1,2});put(fs,"b",new byte[]{3});put(fs,"a",new byte[]{4});
            assertTrue(fs.makeDirectory("dir"));assertTrue(fs.rename("b","dir/b"));
            int writer=fs.open("a",Mode.Append);Handle stale=fs.getHandle(writer);stale.write(new byte[]{5});
            assertTrue(fs.finalizeRecording());assertFalse(fs.finalizeRecording());assertTrue(fs.isReadOnly());
            failure(()->stale.write(new byte[]{9}));assertNull(fs.getHandle(writer));
            failure(()->fs.open("a",Mode.Write));failure(()->fs.open("a",Mode.Append));failure(()->fs.open("new",Mode.Write));
            assertFalse(fs.delete("a"));assertFalse(fs.rename("a","x"));assertFalse(fs.makeDirectory("x"));assertFalse(fs.setLastModified("a",0));
            assertArrayEquals(new byte[]{4,5},get(fs,"a"));assertArrayEquals(new byte[]{3},get(fs,"dir/b"));assertFalse(fs.exists("sealed"));
        }
    }
    @Test public void finalizedStatePersistsWithoutTrustingItemNbtOrConfiguredCapacity()throws Exception{
        Path root=directory();OpticalFileSystem first=new OpticalFileSystem(root,false,4096,16);
        put(first,"data",new byte[]{9,8,7});first.finalizeRecording();NBTTagCompound saved=new NBTTagCompound();first.save(saved);first.close();
        assertTrue(saved.getBoolean("optical.finalized"));saved.setBoolean("optical.finalized",false);
        try(Lease lease=new Lease(root,false,8192,16)){OpticalFileSystem fs=lease.fs;fs.load(saved);assertTrue(fs.isReadOnly());assertEquals(4096,fs.spaceTotal());failure(()->fs.open("data",Mode.Write));assertArrayEquals(new byte[]{9,8,7},get(fs,"data"));}
    }
    @Test public void ejectAndReinsertResumeUnsealedSessionAndRevokeStaleHandles()throws Exception{
        Path root=directory();OpticalFileSystem first=new OpticalFileSystem(root,false,4096,16);put(first,"a",new byte[]{1});
        Handle stale=first.getHandle(first.open("a",Mode.Append));first.close();failure(()->stale.write(new byte[]{2}));failure(()->first.open("b",Mode.Write));
        try(Lease lease=new Lease(root,false,4096,16)){OpticalFileSystem fs=lease.fs;assertFalse(fs.isReadOnly());put(fs,"b",new byte[]{3});assertArrayEquals(new byte[]{1},get(fs,"a"));}
    }
    @Test public void copiedIdentityCannotMountConcurrentlyOrChangeMediaType()throws Exception{
        Path root=directory();try(Lease lease=new Lease(root,false,4096,16)){
            failure(()->new OpticalFileSystem(root,false,4096,16));failure(()->new OpticalFileSystem(root,true,4096,16));
            put(lease.fs,"a",new byte[]{1});assertTrue(lease.fs.finalizeRecording());
        }
        try(Lease copy=new Lease(root,false,4096,16)){assertTrue(copy.fs.isReadOnly());}
    }
    @Test public void partialSealFailsClosedAndInvalidDescriptorNeverStartsNewSession()throws Exception{
        Path root=directory();OpticalFileSystem fs=new OpticalFileSystem(root,false,4096,16);put(fs,"a",new byte[]{1});fs.close();
        Files.createFile(root.resolve("sealed")); // Simulate interruption after seal creation before contents/force.
        try(Lease copy=new Lease(root,false,4096,16)){assertTrue(copy.fs.isReadOnly());failure(()->copy.fs.open("a",Mode.Append));}
        Path corrupt=directory();Files.write(corrupt.resolve("media"),new byte[]{0});failure(()->new OpticalFileSystem(corrupt,false,4096,16));
    }
    @Test public void cdRwRemainsWritableAcrossRemountsAndReclaimsQuota()throws Exception{
        Path root=directory();try(Lease lease=new Lease(root,true,4096,16)){OpticalFileSystem fs=lease.fs;put(fs,"a",new byte[]{1,2,3});failure(()->fs.finalizeRecording());assertFalse(fs.isReadOnly());
            put(fs,"a",new byte[]{4});assertEquals(33,fs.spaceUsed());assertTrue(fs.rename("a","b"));assertTrue(fs.delete("b"));assertEquals(16,fs.spaceUsed());put(fs,"c",new byte[]{5});}
        try(Lease lease=new Lease(root,true,4096,16)){assertFalse(lease.fs.isReadOnly());put(lease.fs,"c",new byte[]{6});assertArrayEquals(new byte[]{6},get(lease.fs,"c"));}
    }
    @Test public void boundsHandlesChunksPathsAndDirectoryMutation()throws Exception{
        try(Lease lease=new Lease(directory(),true,1048576,0)){OpticalFileSystem fs=lease.fs;
            for(String path:new String[]{"../secret","/../../secret","C:/secret","a\\b","./x","a/../x","a:b","CON","NUL.txt","dir/COM1","alias.","alias "}){failure(()->fs.open(path,Mode.Write));assertFalse(fs.makeDirectory(path));}
            assertTrue(fs.makeDirectory("dir"));int id=fs.open("dir/a",Mode.Write);Handle h=fs.getHandle(id);failure(()->h.write(new byte[65537]));failure(()->h.seek(1048577));
            assertFalse(fs.delete("dir"));assertFalse(fs.rename("dir","renamed"));h.write(new byte[]{1});h.close();assertTrue(fs.rename("dir","renamed"));
            for(int n=0;n<128;n++)fs.open("renamed/a",Mode.Read);failure(()->fs.open("renamed/a",Mode.Read));
            fs.close();fs.reopen();assertArrayEquals(new byte[]{1},get(fs,"renamed/a"));
        }
    }
    private static final class Lease implements AutoCloseable {
        final OpticalFileSystem fs;
        Lease(Path p,boolean rw,long cap,int cost)throws IOException{fs=new OpticalFileSystem(p,rw,cap,cost);}
        public void close(){fs.close();}
    }
}
