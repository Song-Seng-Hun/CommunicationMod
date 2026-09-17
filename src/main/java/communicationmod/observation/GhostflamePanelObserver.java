package communicationmod.observation;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import java.util.*;
import java.util.function.Function;

import static communicationmod.observation.NativePanelObserver.*;

/** Native visible ghostflames; Inferno prose avoids the logging getter. */
final class GhostflamePanelObserver implements NativePanelObserver {
    private static final Set<String> FLAMES=new HashSet<>(Arrays.asList("SearingGhostflame","CrushingGhostflame","BolsteringGhostflame","InfernoGhostflame","MayhemGhostflame"));
    public String failureKey(){return "ghostflames_unavailable_reason";}
    public void capture(Map<String,Object> out,AbstractPlayer player,Function<AbstractCard,? extends Map<String,Object>> cardView)throws ReflectiveOperationException {
            if(Boolean.TRUE.equals(optional("theHexaghost.HexaMod").getField("renderFlames").get(null))) {
                Class<?> helper=optional("theHexaghost.GhostflameHelper");Object active=helper.getField("activeGhostFlame").get(null);
                List<?> flames=(List<?>)helper.getField("hexaGhostFlames").get(null);List<Object> rows=new ArrayList<>();
                if(flames.size()>12)throw new IllegalStateException("ghostflame_budget_exceeded");
                for(int i=0;i<flames.size();i++) {
                    Object flame=flames.get(i);Class<?> flameType=flame.getClass();
                    if(!flameType.getName().equals("theHexaghost.ghostflames."+flameType.getSimpleName()) || !FLAMES.contains(flameType.getSimpleName()))throw new IllegalStateException("unknown_ghostflame_renderer");
                    Map<String,Object> row=new LinkedHashMap<>();row.put("index",i);row.put("active",flame==active);
                    row.put("name",flameType.getMethod("getName").invoke(flame));
                    // This pinned Inferno getter logs on every call; do not invoke it during observation.
                    if(flameType.getSimpleName().equals("InfernoGhostflame")) {
                        java.lang.reflect.Field descriptions=flameType.getDeclaredField("DESCRIPTIONS");
                        descriptions.setAccessible(true); // Exact pinned private localization cache, not a reflective state dump.
                        String[] d=(String[])descriptions.get(flame);
                        int spent=((Number)flameType.getField("energySpentThisTurn").get(flame)).intValue();
                        int effect=((Number)flameType.getMethod("getEffectCount").invoke(flame)).intValue();
                        row.putAll(PublicDescription.format(infernoDescription(d,Boolean.TRUE.equals(flameType.getField("charged").get(flame)),flame==active,spent,effect),key->null));
                    } else {
                        row.putAll(PublicDescription.format((String)flameType.getMethod("getDescription").invoke(flame),key->null));
                    }
                    row.put("charged",flameType.getField("charged").get(flame));row.put("triggers_required",flameType.getField("triggersRequired").get(flame));
                    row.put("trigger_count",flameType.getMethod("getActiveFlamesTriggerCount").invoke(flame));rows.add(row);
                }
                out.put("ghostflames",rows);
            }
    }
    /** Pinned localized Inferno formatter, omitting only its stdout diagnostic. */
    public static String infernoDescription(String[] d,boolean charged,boolean active,int spent,int effect) {
        if(d==null || d.length<9)throw new IllegalArgumentException("Inferno localization unavailable");
        int remaining=3-spent;
        return (charged?d[0]:"")+(active?(remaining>=1&&remaining<=3?d[4-remaining]:d[4]+remaining):d[5])+d[6]+effect+d[7]+(active?d[8]:"");
    }
}
