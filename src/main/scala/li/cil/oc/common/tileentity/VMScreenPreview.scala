package li.cil.oc.common.tileentity

import li.cil.oc.Localization
import li.cil.oc.api.network.{Analyzable, Node}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.FakePlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.NetworkManager
import net.minecraft.network.play.server.SPacketUpdateTileEntity
import net.minecraft.util.math.{AxisAlignedBB,BlockPos}
import li.cil.oc.plusplus.{BiosBootState,BiosCanvasLayout,BiosReport}

/** Shared SuperBIOS canvas, deliberately separate from ordinary TextBuffer/GPU. */
class VMScreenPreview extends TileEntity with Analyzable with net.minecraft.util.ITickable with traits.VMControlEndpoint {
  var displayRoute=VMControlRouting.Disconnected
  var displayProvider=0
  var displayDiagnostic=0
  var displayReport=BiosReport.empty()
  var displayBoot=false
  var displayPage=0
  var displaySelection=0
  var displayEpoch=0L
  var canvasWidth=1
  var canvasHeight=1
  var canvasOrigin: BlockPos=null
  var canvasFingerprint=""
  private[tileentity] var layoutTick = -1L
  private var group: BiosCanvasLayout.Group=null
  private var cell: BiosCanvasLayout.Cell=null
  private var displayCounter=0
  private var displayCase=""
  private var sentDisplay=""
  def isCanvasOrigin: Boolean = canvasOrigin!=null&&canvasOrigin==pos
  /** Suppress partial client updates/chunk boundaries instead of drawing two canvases. */
  def canvasReady: Boolean = {
    if(world==null|| !isCanvasOrigin||canvasFingerprint.isEmpty)return false
    for(x<-0 until canvasWidth;y<-0 until canvasHeight){
      val p=canvasOrigin.add(-x,y,0)
      if(!world.isBlockLoaded(p))return false
      world.getTileEntity(p) match {
        case s: VMScreenPreview if !s.isInvalid&&s.canvasFingerprint==canvasFingerprint&&s.canvasOrigin==canvasOrigin&&
          s.canvasWidth==canvasWidth&&s.canvasHeight==canvasHeight&&s.displayBoot==displayBoot&&s.displayEpoch==displayEpoch&&
          s.displayPage==displayPage&&s.displaySelection==displaySelection&&s.displayDiagnostic==displayDiagnostic&&java.util.Arrays.equals(s.displayReport,displayReport) =>
        case _ => return false
      }
    }
    true
  }
  private def syncDisplay(): Unit = if(world!=null&& !world.isRemote){val s=world.getBlockState(pos);world.notifyBlockUpdate(pos,s,s,3)}
  def setDiagnostic(value: Int): Unit = if(world!=null&& !world.isRemote){displayDiagnostic=if(value==1)1 else 0;syncDisplay()}
  private def publishIfChanged(): Unit = {
    val value=Seq(displayRoute,displayProvider,displayDiagnostic,displayBoot,displayPage,displaySelection,displayEpoch,canvasWidth,canvasHeight,canvasOrigin,canvasFingerprint,displayCase,displayReport.mkString(",")).mkString(";")
    if(value!=sentDisplay){sentDisplay=value;syncDisplay()}
  }
  private[tileentity] def publishUnavailable(tick: Long,status: Int): Unit = {
    layoutTick=tick;group=null;cell=null;displayRoute=status;displayProvider=0;displayDiagnostic=0;displayBoot=false
    displayReport=BiosReport.empty()
    displayPage=0;displaySelection=0;displayEpoch=0;canvasWidth=1;canvasHeight=1;canvasOrigin=pos;canvasFingerprint="";displayCase="";publishIfChanged()
  }
  private[tileentity] def publishCanvas(tick: Long,g: BiosCanvasLayout.Group,c: BiosCanvasLayout.Cell,route: VMControlRouting.Route): Unit = {
    layoutTick=tick;group=g;cell=c;canvasWidth=g.width;canvasHeight=g.height
    canvasOrigin=new BlockPos(g.origin.x,g.origin.y,g.origin.z);canvasFingerprint=g.fingerprint
    displayRoute=route.status;displayCase=route.identity
    val machine=route.machine
    displayProvider=if(machine!=null)li.cil.oc.api.vm.SuperBios.statusCode()else 0
    displayBoot=machine!=null&&machine.biosOn&&displayProvider==li.cil.oc.api.vm.SuperBios.OFFLINE
    displayPage=if(displayBoot)machine.bios.page().ordinal()else 0
    displaySelection=if(displayBoot)machine.bios.selection()else 0
    displayEpoch=if(machine!=null)machine.bios.epoch()else 0L
    displayDiagnostic=if(displayBoot)machine.diagnosticStatus else 0
    displayReport=if(displayBoot)machine.currentReport()else BiosReport.empty()
    displayReport(BiosReport.COLUMNS)=g.width;displayReport(BiosReport.ROWS)=g.height
    publishIfChanged()
  }
  def refreshCanvas(force: Boolean=false): Unit = VMBiosCanvas.refresh(this,force)
  /** Vanilla block-hit packet supplies face/local coordinates; never accepts host input. */
  def touch(player: EntityPlayer,side: EnumFacing,x: Float,y: Float,z: Float): Boolean = {
    if(!VMControlRouting.mayInspect(this,player)||world.isRemote)return false
    refreshCanvas(true)
    val route=VMControlRouting.resolve(this,player)
    if(group==null||cell==null||route.machine==null||route.identity!=displayCase|| !displayBoot)return false
    val d=side.getDirectionVec
    val point=group.hit(cell,new BiosCanvasLayout.Axis(d.getX,d.getY,d.getZ),x,y,z)
    if(point==null)return false
    val action=BiosBootState.actionAt(displayPage,point.x,point.y)
    val result=route.machine.biosAction(player,action,displayEpoch)
    if(result)refreshCanvas(true)
    result
  }
  override def update(): Unit = {
    controlTick()
    if(world!=null&& !world.isRemote){
      displayCounter=(displayCounter+1)%5
      if(displayCounter==1){
        refreshCanvas()
        if(group!=null&&cell!=null)publishCanvas(layoutTick,group,cell,VMControlRouting.resolveNetwork(this))
      }
    }
  }
  override def getUpdateTag: NBTTagCompound = {
    val t=super.getUpdateTag;t.setInteger("ocppRoute",displayRoute);t.setInteger("ocppProvider",displayProvider);t.setInteger("ocppDiagnostic",displayDiagnostic)
    t.setBoolean("ocppBiosOn",displayBoot);t.setInteger("ocppBiosPage",displayPage);t.setInteger("ocppBiosSelection",displaySelection);t.setLong("ocppBiosEpoch",displayEpoch)
    t.setIntArray("ocppBiosReport",BiosReport.normalize(displayReport))
    t.setInteger("ocppCanvasWidth",canvasWidth);t.setInteger("ocppCanvasHeight",canvasHeight);t.setLong("ocppCanvasOrigin",(if(canvasOrigin==null)pos else canvasOrigin).toLong)
    t.setString("ocppCanvasLayout",canvasFingerprint);t
  }
  private def readDisplay(t: NBTTagCompound): Unit = {
    val oldOrigin=if(canvasOrigin==null)pos else canvasOrigin
    val oldWidth=canvasWidth;val oldHeight=canvasHeight;val oldKey=canvasFingerprint
    val r=t.getInteger("ocppRoute");displayRoute=if(r>=0&&r<=VMControlRouting.Revoked)r else VMControlRouting.Disconnected
    displayProvider=if(t.getInteger("ocppProvider")==1)1 else 0
    displayDiagnostic=if(t.getInteger("ocppDiagnostic")==1)1 else 0
    displayBoot=t.getBoolean("ocppBiosOn")&&displayRoute==VMControlRouting.Connected&&displayProvider==1
    displayPage=math.max(0,math.min(4,t.getInteger("ocppBiosPage")));displaySelection=math.max(0,math.min(2,t.getInteger("ocppBiosSelection")));displayEpoch=t.getLong("ocppBiosEpoch")
    displayReport=BiosReport.normalize(t.getIntArray("ocppBiosReport"))
    val width=t.getInteger("ocppCanvasWidth");val height=t.getInteger("ocppCanvasHeight");val origin=BlockPos.fromLong(t.getLong("ocppCanvasOrigin"));val key=t.getString("ocppCanvasLayout")
    if(width>=1&&height>=1&&width<=256&&height<=256&&width.toLong*height<=256&&origin.getZ==pos.getZ&&
      pos.getX.toLong<=origin.getX&&pos.getX.toLong>origin.getX.toLong-width&&pos.getY.toLong>=origin.getY&&pos.getY.toLong<origin.getY.toLong+height&&key.matches("[0-9a-f]{64}")){
      canvasWidth=width;canvasHeight=height;canvasOrigin=origin;canvasFingerprint=key
    }else{canvasWidth=1;canvasHeight=1;canvasOrigin=pos;canvasFingerprint="";displayBoot=false}
    // Chunk meshes depend only on membership/geometry, never menu page or BIOS power.
    if(world!=null&&world.isRemote&&(oldOrigin!=canvasOrigin||oldWidth!=canvasWidth||oldHeight!=canvasHeight||oldKey!=canvasFingerprint)){
      world.markBlockRangeForRenderUpdate(oldOrigin.add(-oldWidth,-1,-1),oldOrigin.add(1,oldHeight,1))
      world.markBlockRangeForRenderUpdate(canvasOrigin.add(-canvasWidth,-1,-1),canvasOrigin.add(1,canvasHeight,1))
    }
  }
  override def handleUpdateTag(t: NBTTagCompound): Unit = {super.handleUpdateTag(t);readDisplay(t)}
  override def getUpdatePacket: SPacketUpdateTileEntity = new SPacketUpdateTileEntity(pos,0,getUpdateTag)
  override def onDataPacket(manager: NetworkManager,packet: SPacketUpdateTileEntity): Unit = readDisplay(packet.getNbtCompound)
  override def getRenderBoundingBox: AxisAlignedBB = {
    val p=if(canvasOrigin==null)pos else canvasOrigin
    new AxisAlignedBB(p.getX-canvasWidth+1,p.getY,p.getZ,p.getX+1,p.getY+canvasHeight,p.getZ+1)
  }
  override def onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    if (getWorld != null && !getWorld.isRemote && !player.isInstanceOf[FakePlayer])
      player.sendMessage(Localization.Analyzer.LastError({
        val route=VMControlRouting.resolve(this,player)
        if(route.status==VMControlRouting.Connected)li.cil.oc.api.vm.SuperBios.statusKey(li.cil.oc.api.vm.SuperBios.statusCode()) else VMControlRouting.statusKey(route.status)
      }))
    null
  }
}
