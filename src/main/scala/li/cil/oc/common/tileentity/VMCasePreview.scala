package li.cil.oc.common.tileentity

import li.cil.oc.Localization
import li.cil.oc.api.network.{Analyzable, Node}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.FakePlayer
import li.cil.oc.plusplus.{PreviewFailureState,BiosBootState,BiosReport,OpticalAudioDiagnostics}
import net.minecraft.util.ITickable
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.NetworkManager
import net.minecraft.network.play.server.SPacketUpdateTileEntity

/** In-game BIOS only. The optional provider has no guest execution capability. */
class VMCasePreview extends TileEntity with Analyzable with ITickable with traits.VMControlEndpoint {
  private val failure = new PreviewFailureState
  val bios = new BiosBootState
  private var clientBiosOn=false
  var diagnosticStatus=0
  private var nextInputTick=0L
  private var nextDiagnosticTick=0L
  private var memoryResult=0
  private var inputResult=0
  private var reportTick=Long.MinValue
  private var cachedReport=BiosReport.empty()
  private def clearReport(): Unit={memoryResult=0;inputResult=0;reportTick=Long.MinValue;cachedReport=BiosReport.empty()}
  def currentReport(): Array[Int] = {
    if(world==null||world.isRemote|| !biosOn)return BiosReport.empty()
    val tick=world.getTotalWorldTime
    if(reportTick==Long.MinValue||tick<reportTick||tick-reportTick>=20){
      cachedReport=BiosInventory.capture(this);reportTick=tick
      cachedReport(BiosReport.MEMORY_TEST)=memoryResult;cachedReport(BiosReport.INPUT_TEST)=inputResult
      cachedReport(BiosReport.OPTICAL_ENABLED)=if(OpticalAudioDiagnostics.enabled)1 else 0
      val audio=OpticalAudioDiagnostics.snapshot();Array.copy(audio,0,cachedReport,BiosReport.OPTICAL_BASE,audio.length)
    }
    cachedReport.clone()
  }
  def biosOn: Boolean = if(world!=null&&world.isRemote)clientBiosOn else bios.mayProject()
  private def dependency = if(li.cil.oc.api.vm.SuperBios.statusCode()==li.cil.oc.api.vm.SuperBios.OFFLINE)
    BiosBootState.Dependency.OFFLINE_BIOS else BiosBootState.Dependency.MISSING
  def failedStartActive: Boolean = failure.active()

  def trySimulatedStart(player: EntityPlayer): Boolean = {
    if (world == null || world.isRemote || player == null || player.isInstanceOf[FakePlayer] ||
      player.world != world || !world.isBlockLoaded(pos) || world.getTileEntity(pos) != this) return false
    val authorized = player.openContainer match {
      case gui: li.cil.oc.common.container.VMCasePreview => (gui.preview eq this) && gui.canInteractWith(player)
      case _ => false
    }
    val transition=bios.power(dependency,authorized,world.getTotalWorldTime)
    if(!transition.accepted)return false
    clearReport()
    diagnosticStatus=0
    if(transition.beep){failure.tryStart();emitFailedStartSound()}
    else {
      failure.clear()
      // Only this accepted power transition can emit POST; polling/rendering never do.
      if(transition.state==BiosBootState.State.BIOS)emitPostSound()
    }
    synchronizeDisplay()
    true
  }
  protected def emitFailedStartSound(): Unit =
    li.cil.oc.server.PacketSender.sendSound(world,pos.getX+0.5,pos.getY+0.5,pos.getZ+0.5,"--")
  protected def emitPostSound(): Unit =
    li.cil.oc.server.PacketSender.sendSound(world,pos.getX+0.5,pos.getY+0.5,pos.getZ+0.5,".")
  private def synchronizeDisplay(): Unit = {
    val state=world.getBlockState(pos)
    world.notifyBlockUpdate(pos,state,state,3)
  }
  override def update(): Unit = {
    controlTick()
    if(world!=null&& !world.isRemote){
      val epoch=bios.epoch();bios.dependencyChanged(dependency)
      if(!biosOn&&(memoryResult!=0||inputResult!=0||reportTick!=Long.MinValue))clearReport()
      if(failure.tick()||epoch!=bios.epoch())synchronizeDisplay()
    }else failure.tick()
  }
  /** Caller validates the player, case identity, layout, session and epoch first. */
  def runDiagnostic(player: EntityPlayer): Boolean = {
    if(!biosOn||li.cil.oc.api.vm.SuperBios.statusCode()!=li.cil.oc.api.vm.SuperBios.OFFLINE||player==null||player.getUniqueID==null)return false
    val worldId=java.util.UUID.randomUUID()
    val session=new li.cil.oc.api.vm.MockVmTransport(worldId,java.util.UUID.fromString(controlIdentity),player.getUniqueID)
    try{
      val reply=session.request(worldId,java.util.UUID.fromString(controlIdentity),player.getUniqueID,java.util.UUID.randomUUID(),"status","")
      diagnosticStatus=if(reply.startsWith("offline;"))1 else 0
      diagnosticStatus==1
    }finally{session.revoke()}
  }
  def biosAction(player: EntityPlayer,action: Int,epoch: Long): Boolean = {
    if(world==null||world.isRemote||player==null||player.isInstanceOf[FakePlayer]|| !player.isEntityAlive||player.world!=world|| !VMControlRouting.live(this))return false
    bios.dependencyChanged(dependency)
    val tick=world.getTotalWorldTime
    if(!biosOn||epoch!=bios.epoch()||tick<0||tick<nextInputTick)return false
    val accepted=action match {
      case 0 if bios.page()==BiosBootState.Page.DIAGNOSTICS&&tick>=nextDiagnosticTick =>
        nextDiagnosticTick=if(tick>Long.MaxValue-20)Long.MaxValue else tick+20
        runDiagnostic(player)
      case n if (n==20||n==21)&&bios.page()==BiosBootState.Page.DIAGNOSTICS&&tick>=nextDiagnosticTick =>
        nextDiagnosticTick=if(tick>Long.MaxValue-20)Long.MaxValue else tick+20
        if(n==20)memoryResult=BiosReport.memorySelfTest()else inputResult=BiosReport.inputSelfTest()
        reportTick=Long.MinValue;true
      case 22=>bios.openAudio(epoch,true)
      case 1 => bios.key(BiosBootState.Key.UP,epoch,true)
      case 2 => bios.key(BiosBootState.Key.DOWN,epoch,true)
      case 3 => bios.key(BiosBootState.Key.ENTER,epoch,true)
      case 4 => bios.key(BiosBootState.Key.BACK,epoch,true)
      case n if n>=10&&n<=12&&bios.page()==BiosBootState.Page.MAIN => bios.click(0.5,0.3+(n-10)*0.16,epoch,true)
      case _ => false
    }
    if(accepted)nextInputTick=if(tick>Long.MaxValue-2)Long.MaxValue else tick+2
    accepted
  }
  override def getUpdateTag: NBTTagCompound = {
    val tag=super.getUpdateTag; tag.setInteger("ocppPreviewFailureTicks",failure.remaining());tag.setBoolean("ocppBiosOn",biosOn); tag
  }
  override def getUpdatePacket: SPacketUpdateTileEntity = new SPacketUpdateTileEntity(pos,0,getUpdateTag)
  override def handleUpdateTag(tag: NBTTagCompound): Unit = {
    super.handleUpdateTag(tag); failure.synchronize(tag.getInteger("ocppPreviewFailureTicks"));clientBiosOn=tag.getBoolean("ocppBiosOn")
  }
  override def onDataPacket(manager: NetworkManager, packet: SPacketUpdateTileEntity): Unit =
    {failure.synchronize(packet.getNbtCompound.getInteger("ocppPreviewFailureTicks"));clientBiosOn=packet.getNbtCompound.getBoolean("ocppBiosOn")}
  override def onChunkUnload(): Unit = { failure.clear();bios.shutdown();clearReport();clientBiosOn=false; super.onChunkUnload() }
  override def invalidate(): Unit = {bios.shutdown();clearReport();clientBiosOn=false;super.invalidate()}

  override def onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    if (getWorld != null && !getWorld.isRemote && !player.isInstanceOf[FakePlayer])
      player.sendMessage(Localization.Analyzer.LastError(li.cil.oc.api.vm.SuperBios.statusKey(li.cil.oc.api.vm.SuperBios.statusCode())))
    null // Suppress invented component/energy/backend information.
  }
}
