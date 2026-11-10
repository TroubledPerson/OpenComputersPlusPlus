import li.cil.oc.Settings
import li.cil.oc.api.event.FileSystemAccessEvent
import li.cil.oc.common.event.FileSystemAccessHandler
import li.cil.oc.common.tileentity.{CDDrive,DiskDrive}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.{SoundCategory,SoundEvent}
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.junit.Test
import org.mockito.{ArgumentCaptor,Matchers,Mockito}

/** Real event handler with mock host/world; no Minecraft game loop or OpenAL. */
class OpticalLegacyEventTest {
  private def drive[T <: DiskDrive](kind:Class[T]):(T,World)={
    val d=Mockito.mock(kind);val w=Mockito.mock(classOf[World])
    Mockito.when(d.getWorld).thenReturn(w);Mockito.when(d.getPos).thenReturn(new BlockPos(2,3,4))
    (d,w)
  }
  @Test def classificationIsOpticalAndLegacySpecific():Unit={
    val(cd,_)=drive(classOf[CDDrive]);val(floppy,_)=drive(classOf[DiskDrive])
    for(s<-Seq("opencomputers:floppy_access","opencomputers:hdd_access")){
      assert(FileSystemAccessHandler.isLegacyOpticalSound(cd,s))
      assert(!FileSystemAccessHandler.isLegacyOpticalSound(floppy,s))
      assert(!FileSystemAccessHandler.isLegacyOpticalSound(null,s))
    }
    assert(!FileSystemAccessHandler.isLegacyOpticalSound(cd,"other:custom_activity"))
  }
  @Test def serverLegacyOpticalEventIsSuppressedWithoutInventingRead():Unit={
    val(cd,world)=drive(classOf[CDDrive])
    val e=new FileSystemAccessEvent.Server("opencomputers:floppy_access",cd,null)
    FileSystemAccessHandler.onFileSystemAccess(e)
    if(e.isCancelable)assert(e.isCanceled)
    li.cil.oc.server.PacketSender.sendFileSystemActivity(null,cd,"opencomputers:floppy_access")
    Mockito.verify(cd,Mockito.never()).requestReadSound()
    Mockito.verifyZeroInteractions(world)
  }
  @Test def clientLegacyOpticalEventCannotPlaySecondFloppyVoice():Unit={
    val(cd,world)=drive(classOf[CDDrive])
    val e=new FileSystemAccessEvent.Client("opencomputers:floppy_access",cd,new NBTTagCompound)
    FileSystemAccessHandler.onFileSystemAccess(e)
    if(e.isCancelable)assert(e.isCanceled)
    Mockito.verifyZeroInteractions(world)
  }
  @Test def normalFloppyClientEventKeepsItsOriginalSound():Unit=ExtendedHardwareFixtures.withSettings {
    Mockito.when(Settings.get.soundVolume).thenReturn(0.5f)
    val(floppy,world)=drive(classOf[DiskDrive])
    val e=new FileSystemAccessEvent.Client("opencomputers:floppy_access",floppy,new NBTTagCompound)
    FileSystemAccessHandler.onFileSystemAccess(e)
    assert(!e.isCanceled)
    val sound=ArgumentCaptor.forClass(classOf[SoundEvent])
    Mockito.verify(world).playSound(Matchers.anyDouble(),Matchers.anyDouble(),Matchers.anyDouble(),sound.capture(),
      Matchers.eq(SoundCategory.BLOCKS),Matchers.eq(0.5f),Matchers.eq(1f),Matchers.eq(false))
    assert(sound.getValue.getSoundName.toString=="opencomputers:floppy_access")
  }
}
