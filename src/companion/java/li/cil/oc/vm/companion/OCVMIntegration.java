package li.cil.oc.vm.companion;

import li.cil.oc.api.vm.SuperBios;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** Optional server-capable provider. Contains no QEMU payload or executable runner. */
@Mod(modid="ocpp_vmintegration",name="OC++ VM Integration",version="0.2.0-dev.2",acceptedMinecraftVersions="[1.12.2]",dependencies="required-after:opencomputers@[1.8.10-ocpp-dev.20,)",acceptableRemoteVersions="*")
public final class OCVMIntegration {
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event){SuperBios.register(new OfflineProvider());li.cil.oc.api.vm.v2.ProtocolV2.register(new OfflineProvider2());}
    public static final class OfflineProvider implements SuperBios.Provider {
        public int apiVersion(){return SuperBios.API_VERSION;}
        public String backend(){return "qemu-tcg";}
    }
    public static final class OfflineProvider2 implements li.cil.oc.api.vm.v2.ProtocolV2.Provider {
        public int apiVersion(){return 2;}
        public String backend(){return "qemu-tcg";}
        public li.cil.oc.api.vm.v2.ProtocolV2.Capabilities capabilities(){return new li.cil.oc.api.vm.v2.ProtocolV2.Capabilities();}
    }
}
