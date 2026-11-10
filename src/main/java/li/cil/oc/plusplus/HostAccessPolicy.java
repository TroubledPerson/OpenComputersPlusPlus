package li.cil.oc.plusplus;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Pure authorization model only. No real-host filesystem provider is installed.
 * Neither mode selection nor a card grants access. A future provider must also
 * enforce race-safe containment and the OS account's permissions on every handle.
 */
public final class HostAccessPolicy {
    private HostAccessPolicy() { }
    public enum Mode {
        OFF("off",false), MINECRAFT_READ_ONLY("minecraft_read_only",false),
        SYSTEM_READ_ONLY("system_read_only",false), SYSTEM_READ_WRITE("system_read_write",true);
        public final String id; public final boolean writable;
        Mode(String id,boolean writable) { this.id=id;this.writable=writable; }
        public static Mode parse(String id) {
            for(Mode mode:values()) if(mode.id.equals(id)) return mode;
            throw new IllegalArgumentException("invalid host filesystem mode");
        }
    }
    public static final class Grant {
        public final UUID server,world,computer;
        public final Set<UUID> players;
        public final Mode mode;
        private final AtomicBoolean revoked=new AtomicBoolean();
        public Grant(UUID server,UUID world,UUID computer,Set<UUID> players,Mode mode) {
            if(server==null || world==null || computer==null || players==null || players.isEmpty() || mode==null || mode==Mode.OFF)
                throw new IllegalArgumentException("explicit server/world/computer/player grant required");
            this.server=server;this.world=world;this.computer=computer;
            this.players=Collections.unmodifiableSet(new HashSet<>(players));this.mode=mode;
        }
        public void revoke() { revoked.set(true); }
    }
    public static void authorize(Grant grant,Mode configured,UUID server,UUID world,UUID computer,Set<UUID> currentPlayers,boolean write,boolean osPermits) {
        if(grant==null || configured==Mode.OFF || grant.revoked.get() || grant.mode!=configured || !osPermits ||
            !grant.server.equals(server) || !grant.world.equals(world) || !grant.computer.equals(computer) ||
            !grant.players.equals(currentPlayers) || (write && !configured.writable)) throw new SecurityException("host access denied");
    }
    /** Volume aliases and actual anchors must be supplied by a future trusted provider.
     * This syntax check alone is deliberately not presented as containment.
     */
    public static String relativePath(String path) {
        if(path==null || path.length()>1024 || path.startsWith("/") || path.contains("\\") || path.contains(":")) throw new IllegalArgumentException("relative path required");
        for(int i=0;i<path.length();i++) if(Character.isISOControl(path.charAt(i))) throw new IllegalArgumentException("control character in path");
        for(String part:path.split("/",-1)) {
            if(part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")) throw new IllegalArgumentException("ambiguous path");
            String base=part.split("\\.",2)[0].toUpperCase(Locale.ROOT);
            if(base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) throw new IllegalArgumentException("device path denied");
        }
        return path;
    }
}
