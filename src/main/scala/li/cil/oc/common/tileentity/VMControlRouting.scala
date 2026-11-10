package li.cil.oc.common.tileentity

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.util.FakePlayer

/** Server-thread-only, bounded and fail-closed. Never loads a chunk or chooses first. */
object VMControlRouting {
  final val Disconnected=0;final val Connected=1;final val Ambiguous=2;final val Limit=3;final val Revoked=4
  final val MaxNodes=256
  def statusKey(status: Int): String = status match {
    case Connected => "ocpp.superbios.connected"
    case Ambiguous => "ocpp.superbios.ambiguous"
    case Limit => "ocpp.superbios.networkLimit"
    case Revoked => "ocpp.superbios.revoked"
    case _ => "ocpp.superbios.disconnected"
  }
  case class Route(status: Int,machine: VMCasePreview=null,identity: String="")
  def live(tile: TileEntity): Boolean = tile!=null&& !tile.isInvalid&&tile.getWorld!=null&&
    tile.getWorld.isBlockLoaded(tile.getPos)&& (tile.getWorld.getTileEntity(tile.getPos) eq tile)
  def mayInspect(screen: VMScreenPreview,player: EntityPlayer): Boolean =
    player!=null&& !player.isInstanceOf[FakePlayer]&&player.isEntityAlive&&live(screen)&&
      player.world==screen.getWorld&&player.getDistanceSq(screen.getPos.getX+0.5,screen.getPos.getY+0.5,screen.getPos.getZ+0.5)<=64
  def resolve(screen: VMScreenPreview,player: EntityPlayer): Route = {
    if(!mayInspect(screen,player)||screen.getWorld.isRemote)return Route(Disconnected)
    resolveNetwork(screen)
  }
  def resolveNetwork(screen: VMScreenPreview): Route = {
    if(!live(screen)||screen.getWorld.isRemote)return Route(Disconnected)
    val origin=screen.node
    if(origin==null||origin.network==null)return Route(Disconnected)
    val network=origin.network
    val nodes=network.nodes().iterator()
    var count=0;var found: VMCasePreview=null
    while(nodes.hasNext){
      if(count>=MaxNodes)return Route(Limit)
      val n=nodes.next();count+=1
      if(n!=null&& (n.network eq network))n.host match {
        case machine: VMCasePreview if machine.getWorld==screen.getWorld&&live(machine)&& (machine.node eq n) =>
          if(found!=null&& !(found eq machine))return Route(Ambiguous)
          found=machine
        case _ =>
      }
    }
    if(found==null)Route(Disconnected) else Route(Connected,found,found.controlIdentity)
  }
}
