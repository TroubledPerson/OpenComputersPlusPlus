package li.cil.oc.vm.simulated;

import li.cil.oc.api.vm.SuperBios;
import li.cil.oc.api.vm.v2.ProtocolV2;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** Optional development addon. Registration stays OFFLINE; fixtures are explicitly constructed. */
@Mod(modid="ocpp_vmintegration",name="OC++ VM Integration (Simulated)",version="0.3.0-dev.3",acceptedMinecraftVersions="[1.12.2]",dependencies="required-after:opencomputers@[1.8.10-ocpp-dev.20,)",acceptableRemoteVersions="*")
public final class OCVMIntegrationSimulated {
 @Mod.EventHandler public void preInit(FMLPreInitializationEvent event){SuperBios.register(new Provider1());ProtocolV2.register(new Provider2());}
 public static final class Provider1 implements SuperBios.Provider {public int apiVersion(){return 1;}public String backend(){return "qemu-tcg";}}
 public static final class Provider2 implements ProtocolV2.Provider {
  public int apiVersion(){return 2;}public String backend(){return "qemu-tcg";}
  public ProtocolV2.Capabilities capabilities(){return new ProtocolV2.Capabilities();}
  // Keep Provider.openOffline unchanged: no simulated successful guest response enters production API2.
 }
}
