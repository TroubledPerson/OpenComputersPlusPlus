package li.cil.oc.common.tileentity.traits

import li.cil.oc.api
import li.cil.oc.api.network.{Environment => NetworkEnvironment,Message,Node,Visibility}
import net.minecraft.nbt.NBTTagCompound
import java.util.UUID

/** In-game cable endpoint only: no component callbacks, energy or host transport. */
trait VMControlEndpoint extends net.minecraft.tileentity.TileEntity with NetworkEnvironment {
  private var controlNode: Node = null
  private var savedControl = new NBTTagCompound
  private var removedControl = false
  private var controlId: String = null
  final def controlIdentity: String = {
    if(controlId==null)controlId=UUID.randomUUID().toString
    controlId
  }
  override def node: Node = {
    if(removedControl||getWorld==null||getWorld.isRemote||api.API.network==null)return null
    if(controlNode==null){
      val builder=api.Network.newNode(this,Visibility.None)
      if(builder!=null)controlNode=builder.create()
      if(controlNode!=null)controlNode.load(savedControl)
    }
    controlNode
  }
  final def controlTick(): Unit = {
    val n=node
    if(n!=null&&n.network==null){api.Network.joinOrCreateNetwork(this);controlIdentity;markDirty()}
  }
  private def removeControl(): Unit = {
    removedControl=true
    if(controlNode!=null){controlNode.save(savedControl);controlNode.remove();controlNode=null}
  }
  override def validate(): Unit = {removedControl=false;super.validate()}
  override def invalidate(): Unit = {removeControl();super.invalidate()}
  override def onChunkUnload(): Unit = {removeControl();super.onChunkUnload()}
  override def readFromNBT(tag: NBTTagCompound): Unit = {
    super.readFromNBT(tag)
    savedControl=tag.getCompoundTag("ocppControlNode").copy()
    val raw=tag.getString("ocppControlIdentity")
    controlId=try{val id=UUID.fromString(raw);if(id.toString==raw)raw else null}catch{case _:IllegalArgumentException=>null}
    if(controlNode!=null)controlNode.load(savedControl)
  }
  override def writeToNBT(tag: NBTTagCompound): NBTTagCompound = {
    val out=super.writeToNBT(tag)
    if(controlNode!=null)controlNode.save(savedControl)
    out.setTag("ocppControlNode",savedControl.copy())
    out.setString("ocppControlIdentity",controlIdentity)
    out
  }
  override def onConnect(n: Node): Unit = ()
  override def onDisconnect(n: Node): Unit = ()
  override def onMessage(m: Message): Unit = () // Never dispatch a host/VM operation.
}
