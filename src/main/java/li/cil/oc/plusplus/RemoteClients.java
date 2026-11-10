package li.cil.oc.plusplus;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.*;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.Directory;
import com.hierynomus.smbj.share.DiskShare;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Outbound clients only. No endpoint or credentials are supplied by Lua.
 * Profiles are server-held; every operation is cancellable and byte bounded.
 */
public final class RemoteClients {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int TIMEOUT_MS = 10000;
    private RemoteClients() { }

    /** Bootstrap-Java-only interface across the private dependency classloader. */
    public static Callable<Object> task(Map<String,Object> values, Function<String,InetAddress[]> resolver, BooleanSupplier authorized) {
        return new Transfer(values,resolver,authorized);
    }
    private static final class Transfer implements Callable<Object>, Closeable {
        private final Map<String,Object> values;
        private final Function<String,InetAddress[]> resolver;
        private final Cancellation cancellation;
        Transfer(Map<String,Object> values,Function<String,InetAddress[]> resolver,BooleanSupplier authorized) {
            this.values=new HashMap<>(values); this.resolver=resolver; cancellation=new Cancellation(authorized);
        }
        @Override public Object call() throws Exception {
            try {
                Profile p=new Profile((String)values.get("protocol"),(String)values.get("host"),(Integer)values.get("port"),
                    (String)values.get("username"),((String)values.get("password")).toCharArray(),(String)values.get("domain"),
                    (String)values.get("share"),(String)values.get("basePath"),(Boolean)values.get("writable"),(Boolean)values.get("encryption"));
                return execute(p,(String)values.get("operation"),(String)values.get("path"),(byte[])values.get("body"),
                    (Boolean)values.get("plainFtp"),host -> resolver.apply(host),cancellation);
            } finally { cancellation.close(); values.clear(); }
        }
        // Called from component removal: close sockets immediately, leave graceful
        // protocol cleanup to the worker's finally block, never the server tick.
        @Override public void close() { cancellation.cancel(); values.clear(); }
    }

    public static Map<String,Object> securityDefaults() throws Exception {
        SmbConfig smb=smbConfig(SocketFactory.getDefault(),true);
        SecureFtp ftp=new SecureFtp();
        Map<String,Object> result=new HashMap<>();
        result.put("ftpsHostnameChecking",ftp.isEndpointCheckingEnabled());
        result.put("smbSigning",smb.isSigningRequired()); result.put("smbEncryption",smb.isEncryptData());
        result.put("smbDfs",smb.isDfsEnabled()); result.put("dialects",smb.getSupportedDialects().toString());
        result.put("commonsIoVersion",org.apache.commons.io.IOUtils.class.getPackage().getImplementationVersion());
        result.put("commonsIoLoader",org.apache.commons.io.IOUtils.class.getClassLoader().toString());
        int serviceResources=0;
        Enumeration<java.net.URL> resources=RemoteClients.class.getClassLoader().getResources("META-INF/services/org.slf4j.spi.SLF4JServiceProvider");
        while(resources.hasMoreElements()) { resources.nextElement();serviceResources++; }
        result.put("privateSlf4jServiceResources",serviceResources);
        try (SMBClient client=new SMBClient(smb)) { /* Dependency initialization only; never connect. */ }
        return result;
    }

    public interface AddressPolicy { InetAddress[] resolve(String host) throws IOException; }

    public static final class Profile {
        public final String protocol, host, user, domain, share, basePath;
        public final int port;
        public final boolean writable, requireEncryption;
        private final char[] password;
        public Profile(String protocol, String host, int port, String user, char[] password,
                       String domain, String share, String basePath, boolean writable, boolean requireEncryption) {
            if (!Arrays.asList("smb", "ftps", "ftp").contains(protocol)) throw new IllegalArgumentException("unsupported protocol");
            if (host == null || host.isEmpty() || host.contains("/") || host.contains("\\") || port < 1 || port > 65535)
                throw new IllegalArgumentException("invalid endpoint");
            this.protocol=protocol; this.host=host; this.port=port; this.user=user;
            this.password=password.clone(); this.domain=domain; this.share=share;
            this.basePath=relativePath(basePath); this.writable=writable; this.requireEncryption=requireEncryption;
        }
    }

    /** Revocation closes registered sockets/streams, including a blocked control socket. */
    public static final class Cancellation implements Closeable {
        private final BooleanSupplier authorized;
        private final List<AutoCloseable> resources = new ArrayList<>();
        private boolean closed;
        private boolean disposed;
        public Cancellation(BooleanSupplier authorized) { this.authorized=authorized; }
        public synchronized void check() throws IOException {
            if (closed || Thread.currentThread().isInterrupted() || !authorized.getAsBoolean()) {
                close(); throw new IOException("remote access revoked");
            }
        }
        public synchronized <T extends AutoCloseable> T track(T resource) throws IOException {
            if (closed) { try { resource.close(); } catch(Exception ignored) { } throw new IOException("remote access revoked"); }
            resources.add(resource); return resource;
        }
        public synchronized void cancel() {
            closed=true;
            for(AutoCloseable resource:resources) if(resource instanceof Socket) try { resource.close(); } catch(Exception ignored) { }
        }
        @Override public synchronized void close() {
            if (disposed) return;
            cancel(); disposed=true;
            for (int i=resources.size()-1; i>=0; i--) try { resources.get(i).close(); } catch (Exception ignored) { }
            resources.clear();
        }
    }

    public static String relativePath(String path) {
        if (path == null || path.length() > 1024 || path.startsWith("/") || path.contains("\\") || path.contains(":"))
            throw new IllegalArgumentException("relative path required");
        for (int i=0;i<path.length();i++) if (Character.isISOControl(path.charAt(i))) throw new IllegalArgumentException("control character in path");
        for (String part:path.split("/", -1)) if (part.equals(".") || part.equals("..")) throw new IllegalArgumentException("path traversal");
        return path;
    }

    public static void validateOperation(String operation, byte[] body, boolean writable) {
        if (!Arrays.asList("list", "read", "write").contains(operation)) throw new IllegalArgumentException("expected list, read or write");
        if (body == null || body.length > MAX_BYTES) throw new IllegalArgumentException("transfer size limit exceeded");
        if (operation.equals("write") && !writable) throw new IllegalArgumentException("profile is read-only");
        if (!operation.equals("write") && body.length != 0) throw new IllegalArgumentException("body is only valid for write");
    }

    public static Object execute(Profile profile, String operation, String path, byte[] body,
                                 boolean allowPlainFtp, AddressPolicy addressPolicy, Cancellation cancellation) throws Exception {
        cancellation.check();
        validateOperation(operation,body,profile.writable);
        String relative=relativePath(path);
        String full=profile.basePath.isEmpty() ? relative : profile.basePath + (relative.isEmpty()?"":"/"+relative);
        if (profile.protocol.equals("ftp") && !allowPlainFtp) throw new IOException("plaintext FTP disabled");
        InetAddress[] addresses=addressPolicy.resolve(profile.host);
        if (addresses.length == 0) throw new IOException("endpoint denied");
        CheckedSocketFactory sockets=new CheckedSocketFactory(profile.host,profile.port,addresses,cancellation,!profile.protocol.equals("smb"));
        return profile.protocol.equals("smb") ? smb(profile,operation,full,body,sockets,cancellation)
            : ftp(profile,operation,full,body,sockets,cancellation);
    }

    /** Every library control/SMB connection is pinned to the policy-checked address set.
     * No proxy or referral can introduce another host through this factory.
     */
    public static final class CheckedSocketFactory extends SocketFactory {
        private final String host;
        private final int port;
        private final Set<InetAddress> addresses;
        private final Cancellation cancellation;
        private final boolean passiveData;
        public CheckedSocketFactory(String host,int port,InetAddress[] addresses,Cancellation cancellation) {
            this(host,port,addresses,cancellation,false);
        }
        public CheckedSocketFactory(String host,int port,InetAddress[] addresses,Cancellation cancellation,boolean passiveData) {
            this.host=host; this.port=port; this.addresses=new HashSet<>(Arrays.asList(addresses)); this.cancellation=cancellation;
            this.passiveData=passiveData;
        }
        public void validate(SocketAddress endpoint) throws IOException {
            cancellation.check();
            if (!(endpoint instanceof InetSocketAddress)) throw new IOException("endpoint denied");
            InetSocketAddress address=(InetSocketAddress)endpoint;
            if (address.isUnresolved() || (address.getPort()!=port && !(passiveData && address.getPort()>=1024)) || !addresses.contains(address.getAddress())) throw new IOException("endpoint denied");
        }
        @Override public Socket createSocket() throws IOException {
            return cancellation.track(new Socket(Proxy.NO_PROXY) {
                @Override public void connect(SocketAddress endpoint) throws IOException { connect(endpoint,TIMEOUT_MS); }
                @Override public void connect(SocketAddress endpoint,int timeout) throws IOException {
                    validate(endpoint); super.connect(endpoint,Math.min(TIMEOUT_MS,timeout<=0?TIMEOUT_MS:timeout)); setSoTimeout(TIMEOUT_MS);
                }
            });
        }
        @Override public Socket createSocket(String requestedHost,int requestedPort) throws IOException {
            if (!host.equalsIgnoreCase(requestedHost)) throw new IOException("endpoint denied");
            return createSocket(addresses.iterator().next(),requestedPort);
        }
        @Override public Socket createSocket(InetAddress address,int requestedPort) throws IOException {
            Socket socket=createSocket(); socket.connect(new InetSocketAddress(address,requestedPort),TIMEOUT_MS); return socket;
        }
        @Override public Socket createSocket(String h,int p,InetAddress local,int localPort) throws IOException {
            throw new IOException("local binding disabled");
        }
        @Override public Socket createSocket(InetAddress h,int p,InetAddress local,int localPort) throws IOException {
            throw new IOException("local binding disabled");
        }
    }

    public static byte[] readBounded(InputStream input,Cancellation cancellation) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        byte[] chunk=new byte[4096]; int count;
        while (true) {
            cancellation.check(); count=input.read(chunk);
            if (count<0) break;
            if (count>MAX_BYTES-bytes.size()) throw new IOException("transfer size limit exceeded");
            bytes.write(chunk,0,count);
        }
        return bytes.toByteArray();
    }

    private interface Listing { InputStream listing(String path,Cancellation cancellation) throws IOException; }
    private static InputStream listingStream(Socket socket,Cancellation cancellation) throws IOException {
        if (socket==null) throw new IOException("listing unavailable");
        cancellation.track(socket);
        return socket.getInputStream();
    }
    private static void validateDataPort(int port) throws IOException {
        if (port<1024 || port>65535) throw new IOException("passive data port denied");
    }
    private static class PlainFtp extends FTPClient implements Listing {
        @Override protected void _parseExtendedPassiveModeReply(String reply) throws org.apache.commons.net.MalformedServerReplyException {
            super._parseExtendedPassiveModeReply(reply);
            if(getPassivePort()<1024) throw new org.apache.commons.net.MalformedServerReplyException("passive data port denied");
        }
        @Override protected void _parsePassiveModeReply(String reply) throws org.apache.commons.net.MalformedServerReplyException {
            super._parsePassiveModeReply(reply);
            if(getPassivePort()<1024) throw new org.apache.commons.net.MalformedServerReplyException("passive data port denied");
        }
        public InputStream listing(String path,Cancellation cancellation) throws IOException {
            return listingStream(_openDataConnection_("MLSD",path),cancellation);
        }
    }
    private static class SecureFtp extends FTPSClient implements Listing {
        SecureFtp() throws Exception { super(false,SSLContext.getDefault()); setEndpointCheckingEnabled(true); }
        @Override protected void _prepareDataSocket_(Socket socket) throws IOException {
            if (!(socket instanceof javax.net.ssl.SSLSocket)) throw new IOException("encrypted data connection required");
            javax.net.ssl.SSLSocket tls=(javax.net.ssl.SSLSocket)socket;
            javax.net.ssl.SSLParameters parameters=tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS"); tls.setSSLParameters(parameters);
        }
        @Override protected void _parseExtendedPassiveModeReply(String reply) throws org.apache.commons.net.MalformedServerReplyException {
            super._parseExtendedPassiveModeReply(reply);
            if(getPassivePort()<1024) throw new org.apache.commons.net.MalformedServerReplyException("passive data port denied");
        }
        @Override protected void _parsePassiveModeReply(String reply) throws org.apache.commons.net.MalformedServerReplyException {
            super._parsePassiveModeReply(reply);
            if(getPassivePort()<1024) throw new org.apache.commons.net.MalformedServerReplyException("passive data port denied");
        }
        public InputStream listing(String path,Cancellation cancellation) throws IOException {
            return listingStream(_openDataConnection_("MLSD",path),cancellation);
        }
    }

    private static Object ftp(Profile profile,String operation,String path,byte[] body,CheckedSocketFactory sockets,Cancellation cancellation) throws Exception {
        FTPClient client=profile.protocol.equals("ftps")?new SecureFtp():new PlainFtp();
        cancellation.track(() -> { if(client.isConnected()) client.disconnect(); });
        client.setSocketFactory(sockets);
        client.setConnectTimeout(TIMEOUT_MS); client.setDefaultTimeout(TIMEOUT_MS); client.setDataTimeout(TIMEOUT_MS);
        client.setControlEncoding("UTF-8"); client.setUseEPSVwithIPv4(true);
        // Never trust a server-advertised PASV host: data stays on the checked control peer.
        client.setIpAddressFromPasvResponse(false); client.setRemoteVerificationEnabled(true);
        client.connect(profile.host,profile.port);
        if(!client.login(profile.user,new String(profile.password))) throw new IOException("authentication failed");
        cancellation.check();
        if(client instanceof FTPSClient) { ((FTPSClient)client).execPBSZ(0); ((FTPSClient)client).execPROT("P"); }
        client.enterLocalPassiveMode();
        if(!client.setFileType(FTPClient.BINARY_FILE_TYPE)) throw new IOException("binary transfer unavailable");
        Object result;
        if(operation.equals("write")) {
            OutputStream output=client.storeFileStream(path);
            if(output==null) throw new IOException("write unavailable");
            cancellation.track(output);
            for(int offset=0;offset<body.length;offset+=4096) { cancellation.check(); output.write(body,offset,Math.min(4096,body.length-offset)); }
            output.close(); result=body.length;
        } else {
            InputStream input=operation.equals("list")?((Listing)client).listing(path,cancellation):client.retrieveFileStream(path);
            if(input==null) throw new IOException("read unavailable");
            cancellation.track(input); byte[] bytes=readBounded(input,cancellation); input.close();
            result=operation.equals("list")?new String(bytes,StandardCharsets.UTF_8):bytes;
        }
        cancellation.check();
        if(!client.completePendingCommand()) throw new IOException("transfer incomplete");
        return result;
    }

    public static SmbConfig smbConfig(SocketFactory sockets,boolean encryption) {
        return SmbConfig.builder().withSocketFactory(sockets).withDfsEnabled(false).withSigningRequired(true)
            .withEncryptData(encryption).withBufferSize(65536).withTimeout(TIMEOUT_MS,TimeUnit.MILLISECONDS)
            .withSoTimeout(TIMEOUT_MS,TimeUnit.MILLISECONDS).build();
    }

    private static Object smb(Profile profile,String operation,String path,byte[] body,CheckedSocketFactory sockets,Cancellation cancellation) throws Exception {
        cancellation.check();
        SMBClient client=cancellation.track(new SMBClient(smbConfig(sockets,profile.requireEncryption)));
        Connection connection=cancellation.track(client.connect(profile.host,profile.port));
        Session session=cancellation.track(connection.authenticate(new AuthenticationContext(profile.user,profile.password.clone(),profile.domain)));
        DiskShare share=cancellation.track((DiskShare)session.connectShare(profile.share));
        cancellation.check(); String smbPath=path.replace('/','\\');
        if(operation.equals("list")) {
            Directory directory=cancellation.track(share.openDirectory(smbPath,EnumSet.of(AccessMask.FILE_LIST_DIRECTORY,AccessMask.FILE_READ_ATTRIBUTES),
                EnumSet.noneOf(FileAttributes.class),SMB2ShareAccess.ALL,SMB2CreateDisposition.FILE_OPEN,EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE)));
            List<String> names=new ArrayList<>(); int bytes=0;
            for(FileIdBothDirectoryInformation entry:directory) {
                cancellation.check(); String name=entry.getFileName(); bytes+=name.length()*2;
                if(names.size()>=1024 || bytes>65536) throw new IOException("directory listing limit exceeded");
                if(!name.equals(".") && !name.equals("..")) names.add(name);
            }
            return names.toArray(new String[0]);
        }
        boolean write=operation.equals("write");
        com.hierynomus.smbj.share.File file=cancellation.track(share.openFile(smbPath,
            EnumSet.of(write?AccessMask.FILE_WRITE_DATA:AccessMask.FILE_READ_DATA),EnumSet.noneOf(FileAttributes.class),
            SMB2ShareAccess.ALL,write?SMB2CreateDisposition.FILE_OVERWRITE_IF:SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)));
        if(write) {
            int offset=0;
            while(offset<body.length) { cancellation.check(); long count=file.write(body,offset,offset,Math.min(4096,body.length-offset)); if(count<=0) throw new IOException("write incomplete"); offset+=count; }
            return offset;
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] chunk=new byte[4096]; long offset=0;
        while(true) {
            cancellation.check(); int count=file.read(chunk,offset);
            if(count<0) break;
            if(count==0) break;
            if(count>MAX_BYTES-bytes.size()) throw new IOException("transfer size limit exceeded");
            bytes.write(chunk,0,count); offset+=count;
        }
        return bytes.toByteArray();
    }
}
