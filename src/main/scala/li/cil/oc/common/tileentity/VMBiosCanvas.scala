package li.cil.oc.common.tileentity

import java.util.UUID
import scala.collection.JavaConverters._
import scala.collection.mutable
import li.cil.oc.Settings
import li.cil.oc.plusplus.BiosCanvasLayout
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/** Loaded, north-facing preview screens only. No chunk loads or ordinary GPU changes. */
object VMBiosCanvas {
  val Basis=new BiosCanvasLayout.Basis(new BiosCanvasLayout.Axis(0,0,-1),new BiosCanvasLayout.Axis(0,1,0))
  private val worlds=new java.util.WeakHashMap[net.minecraft.world.World,UUID]()
  private def worldId(w: net.minecraft.world.World): UUID = {
    var id=worlds.get(w);if(id==null){id=UUID.randomUUID();worlds.put(w,id)};id
  }
  def refresh(start: VMScreenPreview,force: Boolean=false): Unit = {
    if(!VMControlRouting.live(start)||start.getWorld.isRemote)return
    val world=start.getWorld;val tick=world.getTotalWorldTime
    if(!force&&start.layoutTick>=0&&tick>=start.layoutTick&&tick-start.layoutTick<10)return
    val pending=mutable.Queue(start);val found=mutable.LinkedHashMap.empty[BlockPos,VMScreenPreview]
    var overflow=false
    while(pending.nonEmpty&& !overflow){
      val s=pending.dequeue()
      if(!found.contains(s.getPos)){
        if(found.size>=BiosCanvasLayout.MAX_CELLS)overflow=true
        else{
          found(s.getPos)=s
          for(side<-Array(EnumFacing.WEST,EnumFacing.EAST,EnumFacing.UP,EnumFacing.DOWN)){
            val p=s.getPos.offset(side)
            if(!found.contains(p)&&world.isBlockLoaded(p))world.getTileEntity(p) match {
              case next: VMScreenPreview if VMControlRouting.live(next) => pending.enqueue(next)
              case _ =>
            }
          }
        }
      }
    }
    if(overflow){found.values.foreach(_.publishUnavailable(tick,VMControlRouting.Limit));return}
    val routes=found.values.map(s=>s.getPos->VMControlRouting.resolveNetwork(s)).toMap
    val id=worldId(world)
    val cells=found.values.map{s=>
      val p=s.getPos;val route=routes(p)
      new BiosCanvasLayout.Cell(id,p.getX,p.getY,p.getZ,Basis,7,0xFFFFFF,UUID.fromString(s.controlIdentity),if(route.status==VMControlRouting.Connected)UUID.fromString(route.identity)else null)
    }.toSeq
    val width=if(Settings.get==null)8 else Settings.get.maxScreenWidth
    val height=if(Settings.get==null)6 else Settings.get.maxScreenHeight
    val groups=try{BiosCanvasLayout.partition(cells.asJava,width,height).asScala}catch{
      // Copied/invalid tile identities must blank the display, never crash a tick.
      case _: IllegalArgumentException => found.values.foreach(_.publishUnavailable(tick,VMControlRouting.Revoked));return
    }
    for(group<-groups;cell<-group.cells.asScala){
      val p=new BlockPos(cell.x,cell.y,cell.z)
      found(p).publishCanvas(tick,group,cell,routes(p))
    }
  }
}
