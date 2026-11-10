package li.cil.oc.plusplus;

/** Minecraft1.12 text uses its sixteen-colour palette, independent of GPU RGB. */
public final class TierText {
    private TierText() {}
    public static String colour(int tier) {
        switch(tier) {
            case 4: return "\u00a7a";
            case 5: return "\u00a76";
            case 6: return "\u00a7c";
            default: return "";
        }
    }
    public static String line(int tier,String text) {
        if(text==null)throw new IllegalArgumentException("tooltip text required");
        String colour=colour(tier);
        return colour.isEmpty()?text:colour+text+"\u00a7r";
    }
}
