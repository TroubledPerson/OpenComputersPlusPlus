package li.cil.oc.plusplus;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteSafetyTest {
    @Test public void isolatesLibrariesAndRetainsSecureDefaultsWithoutConnecting() throws Exception {
        Map<String,Object> defaults=IsolatedClients.securityDefaults();
        assertEquals(true,defaults.get("ftpsHostnameChecking"));
        assertEquals(true,defaults.get("smbSigning")); assertEquals(true,defaults.get("smbEncryption"));
        assertEquals(false,defaults.get("smbDfs")); assertFalse(defaults.get("dialects").toString().contains("SMB_1"));
    }
    @Test public void validatesPinnedEndpointWithoutOpeningSocket() throws Exception {
        InetAddress allowed=InetAddress.getByAddress(new byte[]{(byte)203,0,113,5});
        RemoteClients.Cancellation token=new RemoteClients.Cancellation(() -> true);
        RemoteClients.CheckedSocketFactory factory=new RemoteClients.CheckedSocketFactory("fixture.invalid",445,new InetAddress[]{allowed},token);
        factory.validate(new InetSocketAddress(allowed,445));
        try { factory.validate(new InetSocketAddress(allowed,22)); fail(); } catch(IOException expected) { }
        try { factory.validate(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),445)); fail(); } catch(IOException expected) { }
        try { factory.validate(InetSocketAddress.createUnresolved("other.invalid",445)); fail(); } catch(IOException expected) { }
    }
    @Test public void revocationClosesResourcesAndDeniesFurtherOperations() throws Exception {
        AtomicBoolean authorized=new AtomicBoolean(true), closed=new AtomicBoolean();
        RemoteClients.Cancellation token=new RemoteClients.Cancellation(authorized::get);
        token.track(() -> closed.set(true)); token.check(); authorized.set(false);
        try { token.check(); fail(); } catch(IOException expected) { }
        assertTrue(closed.get());
        try { token.track(() -> {}); fail(); } catch(IOException expected) { }
    }
    @Test public void transferBoundariesAreExact() throws Exception {
        RemoteClients.Cancellation token=new RemoteClients.Cancellation(() -> true);
        assertEquals(RemoteClients.MAX_BYTES,RemoteClients.readBounded(new ByteArrayInputStream(new byte[RemoteClients.MAX_BYTES]),token).length);
        try { RemoteClients.readBounded(new ByteArrayInputStream(new byte[RemoteClients.MAX_BYTES+1]),token); fail(); } catch(IOException expected) { }
        try { RemoteClients.validateOperation("write",new byte[1],false); fail(); } catch(IllegalArgumentException expected) { }
        try { RemoteClients.validateOperation("write",new byte[RemoteClients.MAX_BYTES+1],true); fail(); } catch(IllegalArgumentException expected) { }
    }
    @Test public void rejectsTraversalAndFtpCommandInjection() {
        for(String path:new String[]{"../secret","a/../b","/absolute","C:/file","a\\b","file\r\nDELE other"}) {
            try { RemoteClients.relativePath(path); fail(path); } catch(IllegalArgumentException expected) { }
        }
        assertEquals("folder/file.bin",RemoteClients.relativePath("folder/file.bin"));
    }
    @Test public void disabledPlainFtpDoesNotResolveOrConnect() throws Exception {
        RemoteClients.Profile profile=new RemoteClients.Profile("ftp","fixture.invalid",21,"synthetic",new char[0],"","","",false,true);
        AtomicBoolean resolved=new AtomicBoolean();
        try {
            RemoteClients.execute(profile,"read","file",new byte[0],false,host -> { resolved.set(true); throw new IOException(); },new RemoteClients.Cancellation(() -> true)); fail();
        } catch(IOException expected) { }
        assertFalse(resolved.get());
    }
}
