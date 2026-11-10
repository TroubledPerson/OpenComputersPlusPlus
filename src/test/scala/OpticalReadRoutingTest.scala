import java.nio.file.{Files, Paths}
import li.cil.oc.{Settings, api}
import li.cil.oc.api.detail.NetworkAPI
import li.cil.oc.api.fs.Mode
import li.cil.oc.api.machine.{Arguments, Context}
import li.cil.oc.api.network.{ComponentConnector, Environment, Node, Visibility}
import li.cil.oc.common.tileentity.CDDrive
import li.cil.oc.plusplus.{OpticalFileSystem => Media, OpticalSoundSchedule}
import li.cil.oc.server.component.{FileSystem, OpticalFileSystem}
import org.junit.Test
import org.mockito.{Matchers, Mockito}

/** Real component callback with synthetic disc and mocked network; no world/audio device. */
class OpticalReadRoutingTest {
  private def fixture(test: (OpticalFileSystem,CDDrive,Context,Arguments,OpticalSoundSchedule) => Unit,finalized: Boolean=false,recordable: Boolean=false): Unit =
    ExtendedHardwareFixtures.withSettings {
      Mockito.when(Settings.get.maxReadBuffer).thenReturn(1024)
      Mockito.when(Settings.get.maxHandles).thenReturn(8)
      val previous=api.API.network
      val net=Mockito.mock(classOf[NetworkAPI],Mockito.RETURNS_DEEP_STUBS)
      val fsNode=Mockito.mock(classOf[ComponentConnector])
      Mockito.when(fsNode.address()).thenReturn("synthetic-optical")
      Mockito.when(fsNode.tryChangeBuffer(Matchers.anyDouble())).thenReturn(true)
      Mockito.when(net.newNode(Matchers.any(classOf[Environment]),Matchers.eq(Visibility.Network))
        .withComponent("filesystem",Visibility.Neighbors).withConnector().create()).thenReturn(fsNode)
      api.API.network=net
      val dir=Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")),"ocpp-read-routing-")
      val media=new Media(dir,!finalized && !recordable,32L*1024*1024,0)
      try {
        val h=media.open("sound.bin",Mode.Write);media.getHandle(h).write(Array[Byte](1,2,3));media.getHandle(h).close()
        if(finalized)media.finalizeRecording()
        val drive=Mockito.mock(classOf[CDDrive]);val schedule=new OpticalSoundSchedule
        Mockito.doAnswer(new org.mockito.stubbing.Answer[Object] {
          override def answer(invocation:org.mockito.invocation.InvocationOnMock):Object={schedule.requestRead();null}
        }).when(drive).requestReadSound()
        val fs=new OpticalFileSystem(media,null,drive)
        val ctx=Mockito.mock(classOf[Context]);val caller=Mockito.mock(classOf[Node])
        Mockito.when(caller.address()).thenReturn("synthetic-computer");Mockito.when(ctx.node()).thenReturn(caller)
        val open=Mockito.mock(classOf[Arguments]);Mockito.when(open.checkString(0)).thenReturn("sound.bin")
        Mockito.when(open.optString(1,"r")).thenReturn("r")
        val handle=fs.open(ctx,open)(0)
        val read=Mockito.mock(classOf[Arguments]);Mockito.when(read.isInteger(0)).thenReturn(true);Mockito.when(read.checkInteger(0)).thenReturn(handle.asInstanceOf[li.cil.oc.server.component.HandleValue].handle)
        Mockito.when(read.checkInteger(1)).thenReturn(16)
        test(fs,drive,ctx,read,schedule)
      } finally {media.close();api.API.network=previous}
  }
  @Test def nonemptyReadUsesDedicatedWhirrAndNoGenericSound(): Unit = fixture { (fs,drive,ctx,args,schedule) =>
    assert(fs.sound.isEmpty)
    assert(fs.read(ctx,args)(0).asInstanceOf[Array[Byte]].sameElements(Array[Byte](1,2,3)))
    Mockito.verify(drive,Mockito.times(1)).requestReadSound()
    assert(schedule.tick(100,true)==OpticalSoundSchedule.READ)
    assert(OpticalSoundSchedule.EVENTS(OpticalSoundSchedule.READ)=="ocpp_cd_read")
  }
  @Test def superclassCallbackReflectionStillDispatchesOpticalRead(): Unit = fixture { (fs,drive,ctx,args,schedule) =>
    classOf[FileSystem].getMethod("read",classOf[Context],classOf[Arguments]).invoke(fs,ctx,args)
    Mockito.verify(drive,Mockito.times(1)).requestReadSound()
    assert(schedule.tick(100,true)==OpticalSoundSchedule.READ)
  }
  @Test def eofEmptyAndMetadataNeverInventReadingAudio(): Unit = fixture { (fs,drive,ctx,args,schedule) =>
    fs.spaceTotal(ctx,args);Mockito.when(args.checkInteger(1)).thenReturn(0);fs.read(ctx,args)
    Mockito.verify(drive,Mockito.never()).requestReadSound()
    Mockito.when(args.checkInteger(1)).thenReturn(16);fs.read(ctx,args);fs.read(ctx,args)
    Mockito.verify(drive,Mockito.times(1)).requestReadSound()
    assert(schedule.tick(100,true)==OpticalSoundSchedule.READ)
    assert(schedule.tick(113,true)==OpticalSoundSchedule.STOP)
  }
  @Test def literalSeekUsesDedicatedWhirrAndInheritedCallbackDispatch(): Unit = fixture { (fs,drive,ctx,args,schedule) =>
    Mockito.when(args.checkString(1)).thenReturn("set")
    Mockito.when(args.checkInteger(2)).thenReturn(1)
    val position=classOf[FileSystem].getMethod("seek",classOf[Context],classOf[Arguments]).invoke(fs,ctx,args).asInstanceOf[Array[AnyRef]]
    assert(position(0).asInstanceOf[Number].longValue()==1)
    Mockito.verify(drive,Mockito.times(1)).requestReadSound()
    assert(schedule.tick(100,true)==OpticalSoundSchedule.READ)
    assert(schedule.tick(113,true)==OpticalSoundSchedule.STOP)
  }
  @Test def invalidSeekNeverRequestsAudio(): Unit = fixture { (fs,drive,ctx,args,schedule) =>
    Mockito.when(args.checkString(1)).thenReturn("invalid")
    try {fs.seek(ctx,args);assert(false)} catch {case _:IllegalArgumentException =>}
    Mockito.verify(drive,Mockito.never()).requestReadSound()
  }
  @Test def unownedSeekNeverRequestsAudio(): Unit = fixture { (fs,drive,ctx,args,schedule) =>
    Mockito.when(args.checkString(1)).thenReturn("set")
    Mockito.when(ctx.node().address()).thenReturn("unrelated-computer")
    try {fs.seek(ctx,args);assert(false)} catch {case _:java.io.IOException =>}
    Mockito.verify(drive,Mockito.never()).requestReadSound()
  }
  @Test def sealedCdSequentialReadDuringTrayCloseRequestsDeferredWhirrWithoutSeek(): Unit = fixture({ (fs,drive,ctx,args,schedule) =>
    assert(fs.optical.isReadOnly)
    schedule.tray(0,true)
    assert(fs.read(ctx,args)(0).asInstanceOf[Array[Byte]].sameElements(Array[Byte](1,2,3)))
    Mockito.verify(drive,Mockito.times(1)).requestReadSound()
    assert(schedule.tick(1,true)== -1)
    assert(schedule.tick(40,true)==OpticalSoundSchedule.READ)
    assert(schedule.tick(53,true)==OpticalSoundSchedule.STOP)
  },true)

  private def writeArguments(fs:OpticalFileSystem,ctx:Context,data:Array[Byte]):Arguments={
    val open=Mockito.mock(classOf[Arguments]);Mockito.when(open.checkString(0)).thenReturn("write.bin");Mockito.when(open.optString(1,"r")).thenReturn("w")
    val h=fs.open(ctx,open)(0).asInstanceOf[li.cil.oc.server.component.HandleValue].handle
    val args=Mockito.mock(classOf[Arguments]);Mockito.when(args.isInteger(0)).thenReturn(true);Mockito.when(args.checkInteger(0)).thenReturn(h);Mockito.when(args.checkByteArray(1)).thenReturn(data);args
  }
  @Test def successfulNonemptyWriteQueuesOneActualActivity():Unit=fixture { (fs,drive,ctx,args,schedule)=>
    val write=writeArguments(fs,ctx,Array[Byte](7,8));assert(fs.write(ctx,write)(0)==java.lang.Boolean.TRUE)
    val h=fs.optical.open("write.bin",Mode.Read);try{val b=new Array[Byte](2);assert(fs.optical.getHandle(h).read(b)==2);assert(b.sameElements(Array[Byte](7,8)))}finally{fs.optical.getHandle(h).close()}
    Mockito.verify(drive,Mockito.times(1)).requestReadSound();assert(schedule.tick(100,true)==OpticalSoundSchedule.READ)
  }
  @Test def inheritedReflectionDispatchesWriteOnce():Unit=fixture { (fs,drive,ctx,args,schedule)=>
    val write=writeArguments(fs,ctx,Array[Byte](9));classOf[FileSystem].getMethod("write",classOf[Context],classOf[Arguments]).invoke(fs,ctx,write)
    Mockito.verify(drive,Mockito.times(1)).requestReadSound()
  }
  @Test def emptyWriteNeverInventsCue():Unit=fixture { (fs,drive,ctx,args,schedule)=>
    val write=writeArguments(fs,ctx,Array.empty[Byte]);assert(fs.write(ctx,write)(0)==java.lang.Boolean.TRUE);Mockito.verify(drive,Mockito.never()).requestReadSound();assert(schedule.tick(100,true)== -1)
  }
  @Test def failedEnergyOrOwnershipNeverQueuesWrite():Unit=fixture { (fs,drive,ctx,args,schedule)=>
    val write=writeArguments(fs,ctx,Array[Byte](9));Mockito.when(fs.node.asInstanceOf[ComponentConnector].tryChangeBuffer(Matchers.anyDouble())).thenReturn(false)
    try{fs.write(ctx,write);assert(false)}catch{case _:java.io.IOException=>}
    Mockito.when(fs.node.asInstanceOf[ComponentConnector].tryChangeBuffer(Matchers.anyDouble())).thenReturn(true);Mockito.when(ctx.node().address()).thenReturn("wrong-owner")
    try{fs.write(ctx,write);assert(false)}catch{case _:java.io.IOException=>}
    Mockito.verify(drive,Mockito.never()).requestReadSound()
  }
  @Test def finalizedDiscRejectsWriteOpenWithoutAudio():Unit=fixture({ (fs,drive,ctx,args,schedule)=>
    try{writeArguments(fs,ctx,Array[Byte](9));assert(false)}catch{case _:java.io.IOException=>}
    Mockito.verify(drive,Mockito.never()).requestReadSound();assert(schedule.tick(100,true)== -1)
  },true)
  @Test def finalizingAnOpenRecordingHandleRejectsLaterWriteWithoutAudio():Unit=fixture({ (fs,drive,ctx,args,schedule)=>
    val write=writeArguments(fs,ctx,Array[Byte](9));assert(fs.optical.finalizeRecording())
    try{fs.write(ctx,write);assert(false)}catch{case _:java.io.IOException=>}
    Mockito.verify(drive,Mockito.never()).requestReadSound()
  },false,true)

  @Test def duplicateWriterStillRejectsAnAlreadyOpenReadFileWithoutAudio():Unit=fixture { (fs,drive,ctx,args,schedule)=>
    val open=Mockito.mock(classOf[Arguments]);Mockito.when(open.checkString(0)).thenReturn("sound.bin");Mockito.when(open.optString(1,"r")).thenReturn("w")
    try{fs.open(ctx,open);assert(false)}catch{case e:java.io.IOException=>assert(e.getMessage.contains("already open"))}
    Mockito.verify(drive,Mockito.never()).requestReadSound();assert(schedule.tick(100,true)== -1)
  }
}
