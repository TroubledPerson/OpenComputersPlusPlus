package li.cil.oc.plusplus;
import java.util.HashSet;
import java.util.Set;
/** Shared drive slots for one motor plus one transient each: at most16 voices. */
public final class OpticalMotorVoices {
 private final Set<Object> positions=new HashSet<>();
 public boolean admit(Object position){if(positions.contains(position))return true;if(positions.size()>=OpticalMotorPlayback.MAX_DRIVES)return false;positions.add(position);return true;}
 public void release(Object position){positions.remove(position);}
 public void clear(){positions.clear();}
 public int size(){return positions.size();}
}
