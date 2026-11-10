package li.cil.oc.plusplus;

import java.io.File;
import org.junit.Test;

/** Uses the same checks in direct staged JUnit and a future full Gradle build. */
public class TierSpriteLayersTest {
    private void group(int index) throws Exception {
        String source=System.getProperty("ocpp.spriteSourceItems");
        String generated=System.getProperty("ocpp.spriteGenerated");
        if(source==null||generated==null)throw new AssertionError("explicit original/staged resource roots required");
        TierSpriteStyleChecks.runGroup(index,new File(source),new File(generated));
    }
    @Test public void preservesNativeSilhouettesAndSharedPixels() throws Exception { group(0); }
    @Test public void retainsPatternedDiesAndFrameColourRoles() throws Exception { group(1); }
    @Test public void coloursHddBodyAndRetainsHighlights() throws Exception { group(2); }
    @Test public void preservesContactsAndColoursNativeTierMarks() throws Exception { group(3); }
    @Test public void preservesPaletteDiversityAndApuAnimation() throws Exception { group(4); }
    @Test public void bindsBakedModelsWithoutDoubleTint() throws Exception { group(5); }
}