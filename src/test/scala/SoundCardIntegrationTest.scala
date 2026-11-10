import li.cil.oc.{Settings,api}
import li.cil.oc.api.API
import li.cil.oc.api.detail.{Builder,NetworkAPI}
import li.cil.oc.api.machine.{Arguments,Context}
import li.cil.oc.api.network.{Component,Environment,EnvironmentHost,Node,Visibility}
import li.cil.oc.common.item.{Delegator,Microchip,SoundCard => SoundItem}
import li.cil.oc.integration.opencomputers.DriverSoundCard
import li.cil.oc.plusplus.SoundPrimitives
import li.cil.oc.server.component.{SoundCard,FileSystem}
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.{ResourceLocation,SoundEvent}
import net.minecraft.world.World
import org.junit.runner.RunWith
import org.mockito.{Matchers,Mockito}
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SoundCardIntegrationTest extends FunSpec {
  net.minecraft.init.Bootstrap.register()
  def fixture(test:(SoundCard,Component,EnvironmentHost,Context,li.cil.oc.api.network.Network)=>Unit): Unit = ExtendedHardwareFixtures.withSettings {
    Mockito.when(Settings.get.soundCardsEnabled).thenReturn(true)
    Mockito.when(Settings.get.soundMaxEncodedBytes).thenReturn(1024)
    Mockito.when(Settings.get.soundMaxDurationSeconds).thenReturn(30)
    Mockito.when(Settings.get.soundMaxServerStreams).thenReturn(1)
    Mockito.when(Settings.get.soundMaxQueuedNotes).thenReturn(2)
    val previous=API.network;val factory=Mockito.mock(classOf[NetworkAPI]);val b=Mockito.mock(classOf[Builder.NodeBuilder]);val cb=Mockito.mock(classOf[Builder.ComponentBuilder])
    val cardNode=Mockito.mock(classOf[Component]);val net=Mockito.mock(classOf[li.cil.oc.api.network.Network]);val cpu=Mockito.mock(classOf[Node]);val ctx=Mockito.mock(classOf[Context])
    Mockito.when(factory.newNode(Matchers.any(classOf[Environment]),Matchers.any(classOf[Visibility]))).thenReturn(b)
    Mockito.when(b.withComponent("sound_card",Visibility.Neighbors)).thenReturn(cb);Mockito.when(cb.create()).thenReturn(cardNode)
    Mockito.when(cardNode.network()).thenReturn(net);Mockito.when(cardNode.address()).thenReturn("sound-test")
    Mockito.when(cpu.network()).thenReturn(net);Mockito.when(cpu.address()).thenReturn("computer-test");Mockito.when(ctx.node()).thenReturn(cpu)
    Mockito.when(net.node("computer-test")).thenReturn(cpu);Mockito.when(cardNode.canBeSeenFrom(cpu)).thenReturn(true)
    val host=Mockito.mock(classOf[EnvironmentHost]);Mockito.when(host.world()).thenReturn(Mockito.mock(classOf[World]))
    API.network=factory;val card=new SoundCard(host,2)
    try test(card,cardNode,host,ctx,net) finally {Mockito.when(host.world()).thenReturn(null);card.onDisconnect(cardNode);API.network=previous}
  }
  describe("sound cards") {
    it("appends25..27 with normal card slots and base tier limits") {
      val parent=new Delegator;for(i<-0 until 25)new Microchip(parent,0)
      for(tier<-0 to 2){val item=new SoundItem(parent,tier);val stack=item.createItemStack();assert(item.itemId==25+tier);assert(DriverSoundCard.worksWith(stack));assert(DriverSoundCard.slot(stack)==li.cil.oc.common.Slot.Card);assert(DriverSoundCard.tier(stack)==tier)}
    }
    it("uses actual registered vanilla note sounds without a MIDI device") {
      for(name<-SoundPrimitives.INSTRUMENTS)assert(SoundEvent.REGISTRY.containsKey(new ResourceLocation("minecraft:block.note."+name)),name)
    }
    it("rejects invisible sources, binds visible virtual media and revokes on removal without saving payload") {
      fixture { (card,node,host,ctx,net)=>
        val args=Mockito.mock(classOf[Arguments]);Mockito.when(args.checkString(0)).thenReturn("wav");Mockito.when(args.checkInteger(1)).thenReturn(10);Mockito.when(args.checkString(2)).thenReturn("disc");Mockito.when(args.optDouble(3,1)).thenReturn(1)
        val fs=Mockito.mock(classOf[Component]);Mockito.when(net.node("disc")).thenReturn(fs);Mockito.when(fs.network()).thenReturn(net);Mockito.when(fs.address()).thenReturn("disc");Mockito.when(fs.host()).thenReturn(Mockito.mock(classOf[FileSystem]))
        intercept[IllegalArgumentException]{card.begin(ctx,args)}
        Mockito.when(fs.canBeSeenFrom(ctx.node())).thenReturn(true);assert(card.begin(ctx,args)(0)==Boolean.box(true))
        val data=Mockito.mock(classOf[Arguments]);Mockito.when(data.checkByteArray(0)).thenReturn("secretdata".getBytes("UTF-8"));assert(card.write(ctx,data)(0)==Int.box(10))
        val nbt=new NBTTagCompound;card.save(nbt);assert(!nbt.toString.contains("secretdata") && !nbt.hasKey("upload"))
        Mockito.when(host.world()).thenReturn(null);card.onDisconnect(fs);assert(card.status(ctx,null)(0)=="idle")
      }
    }
    it("bounds queued tones and denies calls from an unrelated computer") {
      fixture { (card,node,host,ctx,net)=>
        val args=Mockito.mock(classOf[Arguments]);Mockito.when(args.checkString(0)).thenReturn("square");Mockito.when(args.checkInteger(1)).thenReturn(440);Mockito.when(args.checkDouble(2)).thenReturn(0.1);Mockito.when(args.optDouble(3,1)).thenReturn(1)
        assert(card.tone(ctx,args)(0)==Boolean.box(true));assert(card.tone(ctx,args)(0)==Boolean.box(true));intercept[IllegalStateException]{card.tone(ctx,args)}
        val stranger=Mockito.mock(classOf[Context]);Mockito.when(stranger.node()).thenReturn(Mockito.mock(classOf[Node]));intercept[IllegalStateException]{card.tone(stranger,args)}
      }
    }
  }
}
