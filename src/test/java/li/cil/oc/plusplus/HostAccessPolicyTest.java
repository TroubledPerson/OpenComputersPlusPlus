package li.cil.oc.plusplus;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HostAccessPolicyTest {
    private final UUID server=UUID.randomUUID(),world=UUID.randomUUID(),computer=UUID.randomUUID(),player=UUID.randomUUID();
    private Set<UUID> players() { return Collections.singleton(player); }
    private HostAccessPolicy.Grant grant(HostAccessPolicy.Mode mode) { return new HostAccessPolicy.Grant(server,world,computer,players(),mode); }
    @Test public void modesMatchOwnerOptionsAndDefaultOff() {
        assertEquals(4,HostAccessPolicy.Mode.values().length);
        assertEquals(HostAccessPolicy.Mode.OFF,HostAccessPolicy.Mode.parse("off"));
        try { HostAccessPolicy.Mode.parse("selected_folders"); fail(); } catch(IllegalArgumentException expected) { }
    }
    @Test public void readOnlyModesDenyWritesAndOffDeniesEverything() {
        for(HostAccessPolicy.Mode mode:new HostAccessPolicy.Mode[]{HostAccessPolicy.Mode.MINECRAFT_READ_ONLY,HostAccessPolicy.Mode.SYSTEM_READ_ONLY}) {
            HostAccessPolicy.Grant grant=grant(mode);
            HostAccessPolicy.authorize(grant,mode,server,world,computer,players(),false,true);
            try { HostAccessPolicy.authorize(grant,mode,server,world,computer,players(),true,true); fail(); } catch(SecurityException expected) { }
            try { HostAccessPolicy.authorize(grant,HostAccessPolicy.Mode.OFF,server,world,computer,players(),false,true); fail(); } catch(SecurityException expected) { }
        }
    }
    @Test public void grantsCannotCrossIdentityOrOverrideOsPermissions() {
        HostAccessPolicy.Mode mode=HostAccessPolicy.Mode.SYSTEM_READ_WRITE;
        HostAccessPolicy.Grant grant=grant(mode);
        HostAccessPolicy.authorize(grant,mode,server,world,computer,players(),true,true);
        try { HostAccessPolicy.authorize(grant,mode,server,world,UUID.randomUUID(),players(),false,true); fail(); } catch(SecurityException expected) { }
        try { HostAccessPolicy.authorize(grant,mode,server,world,computer,Collections.singleton(UUID.randomUUID()),false,true); fail(); } catch(SecurityException expected) { }
        try { HostAccessPolicy.authorize(grant,mode,server,world,computer,players(),true,false); fail(); } catch(SecurityException expected) { }
        grant.revoke();
        try { HostAccessPolicy.authorize(grant,mode,server,world,computer,players(),false,true); fail(); } catch(SecurityException expected) { }
    }
    @Test public void syntaxRejectsWindowsDevicesAndTraversalWithoutReadingAnyFiles() {
        for(String path:new String[]{"../secret","/absolute","C:/file","\\\\server\\share","a:b","file.","folder ","CON.txt","a/COM1","a\u0000b"}) {
            try { HostAccessPolicy.relativePath(path); fail(path); } catch(IllegalArgumentException expected) { }
        }
        assertEquals("folder/file.txt",HostAccessPolicy.relativePath("folder/file.txt"));
    }
}
