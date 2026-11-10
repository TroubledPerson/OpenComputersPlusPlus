package li.cil.oc.server.component

import java.io.Closeable
import java.net.InetAddress
import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import java.util.function.{BooleanSupplier, Function}
import com.typesafe.config.Config
import li.cil.oc.Settings
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{Message, Node}
import li.cil.oc.api.prefab.AbstractValue
import li.cil.oc.plusplus.IsolatedClients
import scala.collection.JavaConverters._
import scala.collection.mutable

/** Adds opt-in outbound services without changing HTTP/TCP callbacks or ownership. */
class RemoteInternetCard extends InternetCard {
  private val activeOwner = new AtomicReference[Context]()
  private val epoch = new AtomicLong()
  private val jobs = mutable.Set.empty[RemoteInternetCard.RemoteValue]

  private def enabled: Boolean = {
    val config = Settings.get.config
    Settings.get.tcpEnabled && config.hasPath("plusplus.remoteClients.enabled") && config.getBoolean("plusplus.remoteClients.enabled")
  }
  private def requireOwner(context: Context): Unit =
    if (activeOwner.get() ne context) throw new IllegalArgumentException("can only be used by the owning computer")

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    owner.foreach(activeOwner.set)
  }
  private def closeJobs(): Unit = jobs.synchronized { epoch.incrementAndGet(); jobs.toVector.foreach(_.close()); jobs.clear() }
  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    if (owner.isEmpty) { activeOwner.set(null); closeJobs() }
  }
  override def onMessage(message: Message): Unit = {
    if ((message.name == "computer.stopped" || message.name == "computer.started") && Option(activeOwner.get()).exists(_.node == message.source)) closeJobs()
    super.onMessage(message)
  }

  @Callback(direct = true, doc = """function():table -- Returns outbound client status; no connection is opened.""")
  def remoteStatus(context: Context, args: Arguments): Array[AnyRef] = {
    requireOwner(context)
    result(Map[String, AnyRef]("enabled" -> Boolean.box(enabled), "operations" -> Array("list", "read", "write"),
      "maxTransferBytes" -> Int.box(1024 * 1024), "maxPending" -> Int.box(2)).asJava)
  }

  private def profile(reference: String, context: Context): Config = {
    if (!enabled) throw new IllegalArgumentException("outbound clients disabled by server admin")
    val config = Settings.get.config
    if (!config.hasPath("plusplus.remoteClients.profiles")) throw new IllegalArgumentException("profile not authorized")
    config.getConfigList("plusplus.remoteClients.profiles").asScala.find { p =>
      p.getString("id") == reference && p.getStringList("computers").asScala.contains(context.node.address)
    }.getOrElse(throw new IllegalArgumentException("profile not authorized"))
  }

  @Callback(doc = """function(profile:string, operation:string, path:string[, data:string]):userdata -- Starts a bounded outbound SMB/FTP/FTPS request using an admin-owned profile. Returns ready/finish/close handle.""")
  def remoteRequest(context: Context, args: Arguments): Array[AnyRef] = jobs.synchronized {
    requireOwner(context)
    if (jobs.size >= 2) throw new IllegalArgumentException("too many pending remote requests")
    val reference = args.checkString(0)
    val p = profile(reference, context)
    val operation = args.checkString(1)
    val path = args.checkString(2)
    val body = if (args.count > 3) args.checkByteArray(3) else Array.empty[Byte]
    if (body.length > 1024 * 1024 || path.length > 1024) throw new IllegalArgumentException("request size limit exceeded")
    val values = new java.util.HashMap[String, Object]()
    for (name <- Seq("protocol", "host", "username", "password")) values.put(name, p.getString(name))
    for (name <- Seq("domain", "share", "basePath")) values.put(name, if (p.hasPath(name)) p.getString(name) else "")
    values.put("port", Int.box(if (p.hasPath("port")) p.getInt("port") else if (p.getString("protocol") == "smb") 445 else 21))
    values.put("writable", Boolean.box(p.hasPath("writable") && p.getBoolean("writable")))
    values.put("encryption", Boolean.box(!p.hasPath("requireEncryption") || p.getBoolean("requireEncryption")))
    val config = Settings.get.config
    values.put("plainFtp", Boolean.box(config.hasPath("plusplus.remoteClients.allowPlainFtp") && config.getBoolean("plusplus.remoteClients.allowPlainFtp")))
    values.put("operation", operation); values.put("path", path); values.put("body", body)
    val value = new RemoteInternetCard.RemoteValue(this, context, reference, p, epoch.get(), values)
    jobs += value
    try RemoteInternetCard.executor.execute(value.future)
    catch { case _: RejectedExecutionException => value.close(); throw new IllegalArgumentException("server remote-request queue full") }
    result(value)
  }

}

object RemoteInternetCard {
  class RemoteValue(card: RemoteInternetCard, context: Context, reference: String, original: Config, generation: Long, values: java.util.Map[String, Object]) extends AbstractValue with Closeable {
    def this() = this(null, null, "", null, -1, new java.util.HashMap[String, Object]())
    private val closed = new AtomicBoolean(card == null)
    @volatile private var profileConfig = original
    @volatile private var task: Callable[Object] = null
    private def authorized: Boolean = card != null && !closed.get() && (card.activeOwner.get() eq context) && card.epoch.get() == generation &&
      (try { card.profile(reference, context) == profileConfig } catch { case _: Exception => false })
    @volatile var future: FutureTask[Object] = if (card == null) null else new FutureTask[Object](new Callable[Object] {
      override def call(): Object = {
        val resolver = new Function[String, Array[InetAddress]] {
          override def apply(host: String): Array[InetAddress] = {
            if (!authorized) throw new IllegalArgumentException("request revoked")
            val addresses = InetAddress.getAllByName(host)
            if (addresses.isEmpty || !addresses.forall(InternetCard.isRequestAllowed(Settings.get, _, host))) throw new IllegalArgumentException("endpoint denied")
            addresses
          }
        }
        val work = IsolatedClients.task(values, resolver, new BooleanSupplier { override def getAsBoolean = authorized })
        task = work
        if (!authorized) { work.asInstanceOf[Closeable].close(); throw new IllegalArgumentException("request revoked") }
        try work.call() finally work.asInstanceOf[Closeable].close()
      }
    })
    private def check(caller: Context): Unit = {
      if (card == null) throw new IllegalArgumentException("request closed after load")
      card.requireOwner(caller); if (caller ne context) throw new IllegalArgumentException("wrong computer")
    }
    @Callback(direct = true, doc = """function():boolean -- Returns whether the request has completed.""")
    def ready(caller: Context, args: Arguments): Array[AnyRef] = { check(caller); result(future == null || future.isDone) }
    @Callback(direct = true, doc = """function():any -- Gets a completed result and releases the handle. FTP listing is MLSD text; SMB listing is a filename array. Errors contain no credentials.""")
    def finish(caller: Context, args: Arguments): Array[AnyRef] = {
      check(caller)
      val current = future
      if (current == null) return result(Unit)
      if (!current.isDone) return result(Unit, "not ready")
      try result(current.get())
      catch { case _: Exception => result(Unit, "remote operation failed or access revoked") }
      finally close()
    }
    @Callback(direct = true, doc = """function() -- Cancels the request and closes its resources.""")
    def close(caller: Context, args: Arguments): Array[AnyRef] = { check(caller); close(); null }
    override def dispose(caller: Context): Unit = close()
    override def load(nbt: net.minecraft.nbt.NBTTagCompound): Unit = close()
    override def close(): Unit = if (closed.compareAndSet(false, true)) {
      val current = future
      if (current != null) current.cancel(true)
      val work = task
      if (work != null) work.asInstanceOf[Closeable].close()
      future = null; task = null
      values.clear(); profileConfig = null
      card.jobs.synchronized { card.jobs -= this }
    }
  }
  private val executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue[Runnable](8), new ThreadFactory {
    override def newThread(work: Runnable): Thread = { val thread = new Thread(work, "OC++ bounded outbound client"); thread.setDaemon(true); thread }
  }, new ThreadPoolExecutor.AbortPolicy())
}
