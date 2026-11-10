package li.cil.oc.integration.opencomputers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream}
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.UUID
import li.cil.oc.{Settings, api}
import li.cil.oc.api.detail.{Builder, NetworkAPI}
import li.cil.oc.api.network.{ComponentConnector, Environment, ManagedEnvironment, Visibility}
import li.cil.oc.common.item.{Delegator, OpenOSOpticalDisc, OpticalDisc}
import li.cil.oc.common.tileentity.CDDrive
import li.cil.oc.plusplus.{OpticalFileSystem => Media}
import li.cil.oc.server.component.OpticalFileSystem
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{CompressedStreamTools, NBTTagCompound}
import org.junit.Test
import org.mockito.{Matchers, Mockito}
import scala.collection.JavaConverters._

/** Actual driver mount, inventory tag replacement and NBT round trip; fixture media only. */
class OpenOSDiscLifecycleTest {
  net.minecraft.init.Bootstrap.register()
  private class SaveHarness extends CDDrive {
    override def isServer: Boolean = true
    def saveMedium(fs: ManagedEnvironment, stack: ItemStack): Unit = save(fs,DriverOpticalDisc,stack)
  }
  private def fixture(test: (SaveHarness, ItemStack, Path) => Unit): Unit = {
    val field=Settings.getClass.getDeclaredFields.find(_.getType==classOf[Settings]).get;field.setAccessible(true)
    val oldSettings=field.get(Settings);val oldNetwork=api.API.network
    val settings=Mockito.mock(classOf[Settings]);Mockito.when(settings.opticalCapacityBytes).thenReturn(32L*1024*1024)
    Mockito.when(settings.fileCost).thenReturn(512)
    val network=Mockito.mock(classOf[NetworkAPI])
    val builder=Mockito.mock(classOf[Builder.NodeBuilder])
    val componentBuilder=Mockito.mock(classOf[Builder.ComponentBuilder])
    val connectorBuilder=Mockito.mock(classOf[Builder.ComponentConnectorBuilder])
    val node=Mockito.mock(classOf[ComponentConnector])
    Mockito.when(node.address()).thenReturn("synthetic-disc-lifecycle")
    Mockito.when(network.newNode(Matchers.any(classOf[Environment]),Matchers.eq(Visibility.Network))).thenReturn(builder)
    Mockito.when(builder.withComponent(Matchers.anyString())).thenReturn(componentBuilder)
    Mockito.when(builder.withComponent("filesystem",Visibility.Neighbors)).thenReturn(componentBuilder)
    Mockito.when(componentBuilder.withConnector()).thenReturn(connectorBuilder)
    Mockito.when(componentBuilder.create()).thenReturn(node)
    Mockito.when(connectorBuilder.create()).thenReturn(node)
    field.set(Settings,settings);api.API.network=network
    val root=Paths.get(System.getProperty("ocpp.sourceRoot","."),"build","openos-lifecycle-fixtures");Files.createDirectories(root)
    try{test(new SaveHarness,new OpenOSOpticalDisc(new Delegator).createItemStack(),Files.createTempDirectory(root,"disc-"))}
    finally{api.API.network=oldNetwork;field.set(Settings,oldSettings)}
  }
  private def mount(stack:ItemStack,drive:SaveHarness,path:Path):OpticalFileSystem =
    DriverOpticalDisc.mountAt(stack,drive,path.toFile,!Item.dataTag(stack).hasKey("optical.id"))
  private def roundTrip(stack:ItemStack):ItemStack={
    val out=new ByteArrayOutputStream;CompressedStreamTools.write(stack.getTagCompound,new DataOutputStream(out))
    val copy=stack.copy();copy.setTagCompound(CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray))))
    copy
  }
  private def snapshot(root:Path):Map[String,(String,Long)]={
    val stream=Files.walk(root)
    try stream.iterator().asScala.filter(p=>Files.isRegularFile(p)).map{p=>
      val hash=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)).map(b=>f"${b&255}%02x").mkString
      root.relativize(p).toString -> ((hash,Files.getLastModifiedTime(p).toMillis()))
    }.toMap finally stream.close()
  }
  @Test def actualInventorySaveAndItemReloadPreserveIdentityMarkerAndBootFiles():Unit=fixture{(drive,stack,path)=>
    var fs=mount(stack,drive,path);val id=Item.dataTag(stack).getString("optical.id");val marker=Item.dataTag(stack).getString("optical.master")
    assert(marker.matches("[a-f0-9]{64}"));val before=snapshot(path)
    try{drive.saveMedium(fs,stack);assert(Item.dataTag(stack).getString("optical.master")==marker)
      drive.saveMedium(fs,stack);assert(Item.dataTag(stack).getString("optical.id")==id)
    }finally{fs.optical.close()}
    val loaded=roundTrip(stack);fs=mount(loaded,drive,path)
    try{fs.load(Item.dataTag(loaded));assert(fs.optical.isReadOnly && fs.optical.exists("/init.lua") && fs.optical.exists("/lib/core/boot.lua"))
      drive.saveMedium(fs,loaded);assert(Item.dataTag(loaded).getString("optical.master")==marker);assert(Item.dataTag(loaded).getString("optical.id")==id)
      assert(snapshot(path)==before)
    }finally{fs.optical.close()}
  }
  @Test def missingLegacyMarkerIsRecoveredOnlyAfterSealedContentValidation():Unit=fixture{(drive,stack,path)=>
    var fs=mount(stack,drive,path);val marker=Item.dataTag(stack).getString("optical.master");drive.saveMedium(fs,stack);fs.optical.close()
    Item.dataTag(stack).removeTag("optical.master");val loaded=roundTrip(stack);val before=snapshot(path)
    fs=mount(loaded,drive,path)
    try{assert(Item.dataTag(loaded).getString("optical.master")==marker);drive.saveMedium(fs,loaded)
      assert(Item.dataTag(loaded).getString("optical.master")==marker);assert(snapshot(path)==before)
    }finally{fs.optical.close()}
  }
  @Test def damagedSealedLegacyMasterIsRejectedWithoutReseedingOrRestoringMarker():Unit=fixture{(drive,stack,path)=>
    val fs=mount(stack,drive,path);drive.saveMedium(fs,stack);fs.optical.close();Item.dataTag(stack).removeTag("optical.master")
    Files.write(path.resolve("data/init.lua"),Array[Byte](7,9,1));val before=snapshot(path)
    try{mount(stack,drive,path);assert(false)}catch{case _:java.io.IOException=>}
    assert(!Item.dataTag(stack).hasKey("optical.master"));assert(snapshot(path)==before)
    val probe=new Media(path,false,32L*1024*1024,512);probe.close() // failed mount released its lease
  }
  @Test def interruptedWritableMasterAndMalformedMarkerRemainUntouched():Unit=fixture{(drive,stack,path)=>
    Item.dataTag(stack).setString("optical.id",UUID.randomUUID().toString)
    val media=new Media(path,false,32L*1024*1024,512);val h=media.open("keep.txt",li.cil.oc.api.fs.Mode.Write)
    media.getHandle(h).write(Array[Byte](3,4));media.getHandle(h).close();media.close();val before=snapshot(path)
    try{mount(stack,drive,path);assert(false)}catch{case _:java.io.IOException=>}
    assert(snapshot(path)==before && !Item.dataTag(stack).hasKey("optical.master"))
    Item.dataTag(stack).setString("optical.master","invalid")
    try{mount(stack,drive,path);assert(false)}catch{case _:java.io.IOException=>}
    assert(Item.dataTag(stack).getString("optical.master")=="invalid" && snapshot(path)==before)
  }
  @Test def blankRecordableAndRewritableDiscsStayBlankAcrossSaveAndRemount():Unit=fixture{(drive,unused,path)=>
    for(rw<-Seq(false,true)){
      val disc=new OpticalDisc(new Delegator,rw).createItemStack();val root=path.resolve(if(rw)"rw" else "r")
      var fs=mount(disc,drive,root);try{drive.saveMedium(fs,disc);assert(!Item.dataTag(disc).hasKey("optical.master"));assert(fs.optical.list("/").isEmpty)}finally{fs.optical.close()}
      val copy=roundTrip(disc);fs=mount(copy,drive,root);try{assert(!fs.optical.isReadOnly && fs.optical.list("/").isEmpty)}finally{fs.optical.close()}
    }
  }
}
