package li.cil.oc.common.tileentity

import li.cil.oc.api.network.{Component,Node}
import li.cil.oc.plusplus.BiosReport
import net.minecraft.tileentity.TileEntity
import scala.util.control.NonFatal

/** Bounded metadata only. Never calls component callbacks, mounts, lists or opens a disk. */
object BiosInventory {
  def capture(machine: VMCasePreview): Array[Int] = {
    val out=BiosReport.empty()
    def unavailable(): Array[Int]={val a=BiosReport.empty();a(BiosReport.STATUS)=2;a}
    if(machine.getWorld==null||machine.getWorld.isRemote|| !machine.biosOn)return out
    def loaded(t: TileEntity): Boolean=t!=null&& !t.isInvalid&&(t.getWorld eq machine.getWorld)&&
      t.getWorld.isBlockLoaded(t.getPos)&&(t.getWorld.getTileEntity(t.getPos) eq t)
    try{
      val node=machine.node;if(node==null||node.network==null||node.network.nodes()==null)return unavailable()
      val seen=new java.util.IdentityHashMap[Node,java.lang.Boolean]()
      val raids=new java.util.IdentityHashMap[Raid,java.lang.Boolean]()
      val screens=new java.util.IdentityHashMap[VMScreenPreview,java.lang.Boolean]()
      val volumes=new java.util.IdentityHashMap[li.cil.oc.server.component.FileSystem,java.lang.Boolean]()
      val it=node.network.nodes().iterator()
      while(it.hasNext){
        val n=it.next();if(n==null||seen.put(n,true)!=null||seen.size()>VMControlRouting.MaxNodes)return unavailable()
        n.host match {
          case r: Raid if loaded(r)=>raids.put(r,true)
          case s: VMScreenPreview if loaded(s)=>screens.put(s,true)
          case d: CDDrive if loaded(d)=>out(BiosReport.CD_DRIVES)+=1;if(d.filesystemNode.nonEmpty)out(BiosReport.MEDIA)+=1
          case _ =>
        }
        n match {
          case c: Component if c.canBeSeenFrom(node)=>
            out(BiosReport.COMPONENTS)+=1
            c.name() match {
              case "filesystem"=>out(BiosReport.FILESYSTEMS)+=1
              case "gpu"=>out(BiosReport.GPU)+=1
              case "modem"=>out(BiosReport.MODEM)+=1
              case "internet"=>out(BiosReport.INTERNET)+=1
              case "data"=>out(BiosReport.DATA)+=1
              case "sound_card"=>out(BiosReport.SOUND)+=1
              case "cd_drive"=>
              case _ =>out(BiosReport.OTHER)+=1
            }
            n.host match {
              case fs: li.cil.oc.server.component.FileSystem if volumes.put(fs,true)==null=>
                fs.host match {
                  case Some(t: TileEntity) if loaded(t)=>
                    t match {case r: Raid=>raids.put(r,true);case _=>}
                    try{
                      val total=fs.fileSystem.spaceTotal;val readOnly=fs.fileSystem.isReadOnly
                      if(total<0)out(BiosReport.QUOTA_UNKNOWN)+=1
                      else out(BiosReport.CAPACITY_KIB)=math.min(Int.MaxValue.toLong,out(BiosReport.CAPACITY_KIB).toLong+total/1024).toInt
                      if(readOnly)out(BiosReport.READ_ONLY)+=1
                    }catch{case NonFatal(_)=>out(BiosReport.QUOTA_UNKNOWN)+=1}
                  case _=>out(BiosReport.QUOTA_UNKNOWN)+=1
                }
              case _=>
            }
          case _=>
        }
      }
      out(BiosReport.NODES)=seen.size();out(BiosReport.RAIDS)=raids.size();out(BiosReport.SCREENS)=screens.size()
      out(BiosReport.STATUS)=1;BiosReport.normalize(out)
    }catch{case NonFatal(_)=>unavailable()}
  }
}
