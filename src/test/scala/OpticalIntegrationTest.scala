import li.cil.oc.api.API
import li.cil.oc.api.detail.{Builder, NetworkAPI}
import li.cil.oc.api.network.{Component, Environment, Visibility}
import li.cil.oc.api.machine.Callback
import li.cil.oc.common.item.{Delegator, Microchip, OpticalDisc, FloppyDisk}
import li.cil.oc.common.tileentity.{CDDrive, DiskDrive}
import li.cil.oc.integration.opencomputers.DriverOpticalDisc
import org.junit.runner.RunWith
import org.mockito.{Matchers, Mockito}
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class OpticalIntegrationTest extends FunSpec {
  net.minecraft.init.Bootstrap.register()
  describe("separate optical media and drive") {
    it("appends CD metadata without changing previous IDs and keeps media unstackable") {
      val parent=new Delegator
      for(i<-0 until 23)new Microchip(parent,0)
      val r=new OpticalDisc(parent,false);val rw=new OpticalDisc(parent,true)
      assert(r.itemId==23 && rw.itemId==24)
      assert(r.maxStackSize==1 && rw.maxStackSize==1)
      assert(r.unlocalizedName=="ocpp_cdr" && rw.unlocalizedName=="ocpp_cdrw")
      assert(DriverOpticalDisc.slot(r.createItemStack())=="optical")
    }
    it("accepts discs only in the new drive and leaves floppy/case slots separate") {
      ExtendedHardwareFixtures.withSettings {
      val previous=API.network
      val network=Mockito.mock(classOf[NetworkAPI])
      val builder=Mockito.mock(classOf[Builder.NodeBuilder])
      val componentBuilder=Mockito.mock(classOf[Builder.ComponentBuilder])
      val node=Mockito.mock(classOf[Component])
      Mockito.when(network.newNode(Matchers.any(classOf[Environment]),Matchers.eq(Visibility.Network))).thenReturn(builder)
      Mockito.when(builder.withComponent(Matchers.anyString())).thenReturn(componentBuilder)
      Mockito.when(componentBuilder.create()).thenReturn(node)
      API.network=network
      try {
        val parent=new Delegator;val cd=new OpticalDisc(parent,false).createItemStack();val floppy=new FloppyDisk(parent).createItemStack()
        val drive=new CDDrive
        assert(drive.getSizeInventory==1 && drive.isItemValidForSlot(0,cd))
        assert(!drive.isItemValidForSlot(1,cd) && !drive.isItemValidForSlot(0,floppy))
        assert(DriverOpticalDisc.worksWith(cd,classOf[CDDrive]))
        assert(!DriverOpticalDisc.worksWith(cd,classOf[DiskDrive]))
        assert(!DriverOpticalDisc.worksWith(floppy))
        assert(DriverOpticalDisc.createEnvironment(cd,drive)==null) // no world => no filesystem access
      } finally API.network=previous
      }
    }
    it("exposes the exact explicit finalize API with server-thread serialization") {
      val methods=classOf[CDDrive].getMethods.filter(_.isAnnotationPresent(classOf[Callback]))
      val finalizeMethod=methods.find(_.getName=="finalizeRecording").get
      val callback=finalizeMethod.getAnnotation(classOf[Callback])
      assert(callback.value()=="finalize" && !callback.direct())
      assert(methods.exists(_.getName=="media") && methods.exists(_.getName=="eject") && methods.exists(_.getName=="isFinalized"))
    }
    it("ticks bounded activity on the server, preserves read call limits and appends its packet ID") {
      assert(classOf[net.minecraft.util.ITickable].isAssignableFrom(classOf[CDDrive]))
      val read=classOf[li.cil.oc.server.component.OpticalFileSystem].getMethods.find(_.getName=="read").get
      val callback=read.getAnnotation(classOf[Callback]);assert(callback.direct() && callback.limit()==15)
      assert(li.cil.oc.common.PacketType.OcppOpticalSound.id==li.cil.oc.common.PacketType.OcppAudio.id+1)
      assert(li.cil.oc.common.PacketType.OcppAudio.id==li.cil.oc.common.PacketType.TextBufferMultiColorAlphaChange.id+1)
    }
  }
}
