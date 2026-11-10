import li.cil.oc.api
import li.cil.oc.api.network.{Environment,Network,Node,Visibility}
import li.cil.oc.common.{container,tileentity}
import li.cil.oc.common.tileentity.VMControlRouting
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.world.World
import net.minecraft.util.math.BlockPos
import org.junit.Test
import org.mockito.{Matchers,Mockito}

/** Mapped server logic with synthetic in-memory OC nodes; never a world or guest. */
class VMControlRoutingTest {
 private class Screen extends tileentity.VMScreenPreview {var forced:Node=null;override def node=forced}
 private class Machine extends tileentity.VMCasePreview {var forced:Node=null;var posts=0;override def node=forced;override protected def emitPostSound(): Unit={posts+=1}}
 private def fixture(f:(Screen,Machine,EntityPlayer,World,Network,Node,Node)=>Unit): Unit = {
  net.minecraft.init.Bootstrap.register()
  val w=Mockito.mock(classOf[World]);val player=Mockito.mock(classOf[EntityPlayer]);val network=Mockito.mock(classOf[Network]);val s=new Screen;val m=new Machine
  val sn=Mockito.mock(classOf[Node]);val mn=Mockito.mock(classOf[Node]);s.forced=sn;m.forced=mn
  s.setWorld(w);m.setWorld(w);s.setPos(BlockPos.ORIGIN);m.setPos(new BlockPos(1,0,0));player.world=w
  Mockito.when(w.isBlockLoaded(s.getPos)).thenReturn(true);Mockito.when(w.isBlockLoaded(m.getPos)).thenReturn(true)
  Mockito.when(w.getTileEntity(s.getPos)).thenReturn(s);Mockito.when(w.getTileEntity(m.getPos)).thenReturn(m)
  Mockito.when(player.isEntityAlive).thenReturn(true);Mockito.when(player.getUniqueID).thenReturn(java.util.UUID.randomUUID())
  Mockito.when(sn.network).thenReturn(network);Mockito.when(mn.network).thenReturn(network);Mockito.when(sn.host).thenReturn(s);Mockito.when(mn.host).thenReturn(m)
  Mockito.when(network.nodes()).thenReturn(java.util.Arrays.asList(sn,mn))
  f(s,m,player,w,network,sn,mn)
 }
 @Test def uniqueConnectionUsesOnlyLoadedMatchingCaseAndKeepsGpuSeparate(): Unit = fixture{(s,m,p,w,n,sn,mn)=>
  val r=VMControlRouting.resolve(s,p);assert(r.status==1&&(r.machine eq m)&&r.identity==m.controlIdentity)
  assert(!classOf[api.internal.TextBuffer].isAssignableFrom(s.getClass))
  Mockito.when(w.isBlockLoaded(m.getPos)).thenReturn(false);assert(VMControlRouting.resolve(s,p).status==0)
  Mockito.when(w.isBlockLoaded(m.getPos)).thenReturn(true);Mockito.when(w.getTileEntity(m.getPos)).thenReturn(new tileentity.VMCasePreview);assert(VMControlRouting.resolve(s,p).status==0)
 }
 @Test def zeroMultipleAndOversizedNetworksFailClosed(): Unit = fixture{(s,m,p,w,n,sn,mn)=>
  Mockito.when(n.nodes()).thenReturn(java.util.Arrays.asList(sn));assert(VMControlRouting.resolve(s,p).status==0)
  val second=new Machine;second.setWorld(w);second.setPos(new BlockPos(2,0,0));val n2=Mockito.mock(classOf[Node]);second.forced=n2
  Mockito.when(w.isBlockLoaded(second.getPos)).thenReturn(true);Mockito.when(w.getTileEntity(second.getPos)).thenReturn(second);Mockito.when(n2.host).thenReturn(second);Mockito.when(n2.network).thenReturn(n)
  Mockito.when(n.nodes()).thenReturn(java.util.Arrays.asList(sn,mn,n2));assert(VMControlRouting.resolve(s,p).status==2)
  val many=new java.util.ArrayList[Node]();for(i<-0 to VMControlRouting.MaxNodes)many.add(sn)
  Mockito.when(n.nodes()).thenReturn(many);assert(VMControlRouting.resolve(s,p).status==3)
 }
 @Test def wrongWorldDistanceDeadPlayerAndDetachedNodeAreDenied(): Unit = fixture{(s,m,p,w,n,sn,mn)=>
  val fake=Mockito.mock(classOf[net.minecraftforge.common.util.FakePlayer]);assert(!VMControlRouting.mayInspect(s,fake))
  Mockito.when(p.getDistanceSq(0.5,0.5,0.5)).thenReturn(65);assert(VMControlRouting.resolve(s,p).status==0)
  Mockito.when(p.getDistanceSq(0.5,0.5,0.5)).thenReturn(0);Mockito.when(p.isEntityAlive).thenReturn(false);assert(VMControlRouting.resolve(s,p).status==0)
  Mockito.when(p.isEntityAlive).thenReturn(true);p.world=Mockito.mock(classOf[World]);assert(VMControlRouting.resolve(s,p).status==0)
  p.world=w;Mockito.when(mn.network).thenReturn(Mockito.mock(classOf[Network]));assert(VMControlRouting.resolve(s,p).status==0)
 }
 @Test def screenSessionRevokesInsteadOfRetargetingAfterDisconnect(): Unit = fixture{(s,m,p,w,n,sn,mn)=>
  val c=new container.VMScreenPreview(s,p);p.openContainer=c;assert(c.canInteractWith(p));assert(c.routeStatus==1&&c.inventorySlots.isEmpty)
  Mockito.when(n.nodes()).thenReturn(java.util.Arrays.asList(sn));assert(!c.canInteractWith(p));assert(c.routeStatus==4)
  Mockito.when(n.nodes()).thenReturn(java.util.Arrays.asList(sn,mn));assert(!c.canInteractWith(p));assert(!c.enchantItem(p,0))
 }
 @Test def diagnosticIsExplicitSessionBoundRateLimitedAndMockOnly(): Unit = fixture{(s,m,p,w,n,sn,mn)=>
  val provider=new li.cil.oc.vm.companion.OCVMIntegration.OfflineProvider
  li.cil.oc.api.vm.SuperBios.register(provider)
  try{
   p.openContainer=new container.VMCasePreview(m);assert(m.trySimulatedStart(p));assert(m.posts==1)
   val c=new container.VMScreenPreview(s,p);p.openContainer=c;assert(c.canInteractWith(p));assert(c.providerStatus==1)
   assert(!c.enchantItem(p,99));assert(!c.enchantItem(p,0));assert(c.enchantItem(p,12))
   Mockito.when(w.getTotalWorldTime).thenReturn(2L);assert(c.enchantItem(p,0));assert(c.diagnosticStatus==1&&s.displayDiagnostic==1)
   assert(!c.enchantItem(p,0));Mockito.when(w.getTotalWorldTime).thenReturn(22L);assert(c.enchantItem(p,0))
   p.openContainer=null;assert(!c.enchantItem(p,0));assert(m.posts==1);assert(!li.cil.oc.api.vm.SuperBios.mayExecuteGuest())
  }finally{li.cil.oc.api.vm.SuperBios.unregister(provider)}
 }
 @Test def clientDisplayPacketClampsValuesAndHasNoGuestState(): Unit = {
  val s=new tileentity.VMScreenPreview;val t=new net.minecraft.nbt.NBTTagCompound
  t.setInteger("ocppRoute",999);t.setInteger("ocppProvider",999);t.setInteger("ocppDiagnostic",999)
  s.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(BlockPos.ORIGIN,0,t))
  assert(s.displayRoute==0&&s.displayProvider==0&&s.displayDiagnostic==0)
  t.setInteger("ocppRoute",1);t.setInteger("ocppProvider",1);t.setInteger("ocppDiagnostic",1)
  s.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(BlockPos.ORIGIN,0,t))
  assert(s.displayRoute==1&&s.displayProvider==1&&s.displayDiagnostic==1)
 }
 @Test def serverPublishesConnectedInWorldOutputAndClearsItOnDisconnect(): Unit = fixture{(s,m,p,w,n,sn,mn)=>
  s.update();assert(s.displayRoute==1&&s.displayProvider==0)
  Mockito.verify(w).notifyBlockUpdate(s.getPos,null,null,3)
  s.setDiagnostic(1);assert(s.displayDiagnostic==1)
  Mockito.when(n.nodes()).thenReturn(java.util.Arrays.asList(sn))
  for(i<-0 until 20)s.update()
  assert(s.displayRoute==0&&s.displayProvider==0&&s.displayDiagnostic==0)
 }
 @Test def controlIdentityAndNodeAddressRoundTripWithoutCreatingHostState(): Unit = {
  net.minecraft.init.Bootstrap.register()
  net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(classOf[tileentity.VMCasePreview],"minecraft:ocpp_control_test")
  val previous=api.API.network;val network=Mockito.mock(classOf[api.detail.NetworkAPI]);val builder=Mockito.mock(classOf[api.detail.Builder.NodeBuilder]);val node=Mockito.mock(classOf[Node])
  Mockito.when(network.newNode(Matchers.any(classOf[Environment]),Matchers.eq(Visibility.None))).thenReturn(builder);Mockito.when(builder.create()).thenReturn(node)
  val address=java.util.UUID.randomUUID().toString
  Mockito.doAnswer(new org.mockito.stubbing.Answer[AnyRef]{override def answer(i: org.mockito.invocation.InvocationOnMock): AnyRef={i.getArguments()(0).asInstanceOf[net.minecraft.nbt.NBTTagCompound].setString("address",address);null}}).when(node).save(Matchers.any(classOf[net.minecraft.nbt.NBTTagCompound]))
  api.API.network=network
  try{
   val w=Mockito.mock(classOf[World]);val first=new tileentity.VMCasePreview;first.setWorld(w);assert(first.node eq node)
   val id=first.controlIdentity;val saved=first.writeToNBT(new net.minecraft.nbt.NBTTagCompound)
   val second=new tileentity.VMCasePreview;second.readFromNBT(saved);assert(second.controlIdentity==id);assert(second.node==null)
   second.setWorld(w);assert(second.node eq node)
   val captured=org.mockito.ArgumentCaptor.forClass(classOf[net.minecraft.nbt.NBTTagCompound]);Mockito.verify(node,Mockito.atLeast(2)).load(captured.capture())
   val tags=captured.getAllValues;assert(tags.get(tags.size-1).getString("address")==address)
  }finally{api.API.network=previous}
 }
 @Test def nodeIsServerOnlyNonComponentAndRemovedOnUnload(): Unit = {
  val previous=api.API.network;val network=Mockito.mock(classOf[api.detail.NetworkAPI]);val builder=Mockito.mock(classOf[api.detail.Builder.NodeBuilder]);val node=Mockito.mock(classOf[Node])
  Mockito.when(network.newNode(Matchers.any(classOf[Environment]),Matchers.eq(Visibility.None))).thenReturn(builder);Mockito.when(builder.create()).thenReturn(node)
  api.API.network=network
  try{
   val t=new tileentity.VMCasePreview;assert(t.node==null);Mockito.verifyZeroInteractions(network)
   val w=Mockito.mock(classOf[World]);t.setWorld(w);assert(t.node eq node);assert(!node.isInstanceOf[api.network.Component])
   Mockito.verify(network).newNode(t,Visibility.None);t.onChunkUnload();Mockito.verify(node).remove();assert(t.node==null)
  }finally{api.API.network=previous}
 }
}
