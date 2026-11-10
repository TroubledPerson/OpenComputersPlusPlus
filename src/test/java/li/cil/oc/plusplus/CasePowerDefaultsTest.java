package li.cil.oc.plusplus;

import com.typesafe.config.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Real default resources and the immutable dev10 source, without game startup. */
public class CasePowerDefaultsTest {
    private File project(){return new File(System.getProperty("ocpp.sourceRoot","."));}
    private Config defaults(){return ConfigFactory.parseFile(new File(project(),"src/main/resources/application.conf"));}
    private byte[] previous(String path)throws Exception {
        File zip=new File(project().getParentFile(),"deliverables/onejar-dev.10/OpenComputersPlusPlus-onejar-dev.10-source.zip");
        try(ZipFile z=new ZipFile(zip);InputStream in=z.getInputStream(z.getEntry(path));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);return out.toByteArray();
        }
    }
    @Test public void missingDefaultsChangeButExplicitOldCustomAndLegacyValuesWin(){
        Config d=defaults();String key="opencomputers.plusplus.casePowerRates";
        assertEquals(Arrays.asList(40,80,160),d.getIntList(key));
        assertEquals(Arrays.asList(5,10,20),d.getIntList("opencomputers.power.rate.case"));
        assertEquals(Arrays.asList(20,20,20),ConfigFactory.parseString(key+"=[20,20,20]").withFallback(d).getIntList(key));
        assertEquals(Arrays.asList(7,43,900),ConfigFactory.parseString(key+"=[7,43,900]").withFallback(d).getIntList(key));
        Config legacy=ConfigFactory.parseString("opencomputers.power.rate.case=[2,6,19]").withFallback(d);
        assertEquals(Arrays.asList(2,6,19),legacy.getIntList("opencomputers.power.rate.case"));
        assertEquals(Arrays.asList(40,80,160),legacy.getIntList(key));
    }
    @Test public void onlyAuthorizedPowerAndOptInDiagnosticsDefaultsChange()throws Exception {
        Config old=ConfigFactory.parseString(new String(previous("src/main/resources/application.conf"),StandardCharsets.UTF_8));
        String key="opencomputers.plusplus.casePowerRates";
        assertEquals(Arrays.asList(20,20,20),old.getIntList(key));
        assertEquals(old.withValue(key,ConfigValueFactory.fromIterable(Arrays.asList(40,80,160))).withValue("opencomputers.plusplus.opticalAudioDiagnostics",ConfigValueFactory.fromAnyRef(false)),defaults());
    }
    @Test public void settingsIndexingAndRuntimeLogicRemainExactExceptDiagnosticField()throws Exception {
        String path="src/main/scala/li/cil/oc/Settings.scala";
        assertArrayEquals(previous(path),ApprovedDiagnosticSource.normalize(path,Files.readAllBytes(new File(project(),path).toPath())));
    }
}
