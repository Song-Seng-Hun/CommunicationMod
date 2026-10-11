package communicationmod.observation;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.ui.buttons.PeekButton;
import java.util.*;
import java.util.function.Function;
import static communicationmod.observation.NativeMechanicAccess.*;

/** Installed native UI fields only. Never reads archetypes, future decks, priorities or RNG. */
public final class BossObservation {
    private static final String BOSS="charbosses.bosses.AbstractCharBoss";
    private static final String HERMIT="charbosses.bosses.Hermit.CharBossHermit";
    private static final String HERMIT_WHEEL="downfall:HermitWheelOfFortune";
    private static final int CARD_LIMIT=32, PANEL_LIMIT=64;
    private BossObservation() { }

    public static void addTo(Map<String,Object> monster,AbstractMonster actor,
        Function<AbstractCard,? extends Map<String,Object>> cardView,
        Function<AbstractRelic,? extends Map<String,Object>> relicView) {
        if(!is(actor,BOSS))return;
        Map<String,Object> out=new LinkedHashMap<>();out.put("source","native_charboss_ui");
        monster.put("public_combat",out);
        boolean visible=!actor.isDead && !actor.isDying && !actor.isEscaping && !actor.escaped;
        out.put("visible",visible);
        if(!visible){out.put("visibility_reason","boss_dead_or_leaving");return;}
        if(!rendererAudited(actor,new HashSet<>(Arrays.asList(BOSS,"charbosses.bosses.Hermit.CharBossHermit","charbosses.bosses.Ironclad.CharBossIronclad","charbosses.bosses.Merchant.CharBossMerchant","charbosses.bosses.Watcher.CharBossWatcher","com.megacrit.cardcrawl.monsters.AbstractMonster")))){out.put("renderer_unavailable_reason","unsupported_boss_renderer");MechanicsCompleteness.addTo(out);return;}
        panel(out,"hand",()->captureHand(out,actor,cardView));
        if(is(actor,HERMIT))panel(out,"roulette_preview",()->captureRoulettePreview(out,actor,cardView));
        panel(out,"energy",()->{
            // The numerator is global: never associate another boss's energy with this actor.
            if(stat(BOSS,"boss")!=actor)throw new IllegalStateException("inactive native boss");
            if(field(actor,"energyPanel")==null)throw new IllegalStateException("missing energy panel");
            Map<String,Object> energy=new LinkedHashMap<>();int current=number(stat("charbosses.ui.EnemyEnergyPanel","totalCount"));
            int capacity=number(field(field(actor,"energy"),"energy"));
            energy.put("current",current);energy.put("capacity",capacity);energy.put("displayed_text",current+"/"+capacity);
            energy.put("source","native_enemy_energy_panel");out.put("energy",energy);
        });
        panel(out,"stance",()->{
            Object stance=field(actor,"stance");if(stance==null)return;
            if(stat(BOSS,"boss")!=actor)throw new IllegalStateException("inactive native boss");
            if(!new HashSet<>(Arrays.asList("charbosses.stances.EnNeutralStance","charbosses.stances.EnCalmStance",
                "charbosses.stances.EnWrathStance","charbosses.stances.EnRealWrathStance","charbosses.stances.EnDivinityStance")).contains(stance.getClass().getName()))
                throw new IllegalStateException("unsupported stance renderer");
            // Active stances use native aura/particles/audio; the inherited image remains null.
            // Enemy stance classes shadow name/description in AbstractStance.
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",field(stance,"ID"));row.put("name",field(stance,"name"));
            row.putAll(PublicDescription.format((String)field(stance,"description"),key->null));
            row.put("source","native_current_enemy_stance");row.put("description_source","native_localized_stance_rules");out.put("stance",row);
        });
        panel(out,"relics",()->{
            List<?> relics=(List<?>)field(actor,"relics");List<Object> rows=new ArrayList<>();
            for(int i=0;i<Math.min(relics.size(),PANEL_LIMIT);i++)rows.add(new LinkedHashMap<>(relicView.apply((AbstractRelic)relics.get(i))));
            out.put("relics",rows);if(relics.size()>PANEL_LIMIT)out.put("relics_truncated",true);
        });
        panel(out,"orbs",()->{
            List<?> orbs=(List<?>)field(actor,"orbs");List<Object> rows=new ArrayList<>();
            for(int i=0;i<Math.min(orbs.size(),PANEL_LIMIT);i++)rows.add(orb((AbstractOrb)orbs.get(i)));
            out.put("orbs",rows);if(orbs.size()>PANEL_LIMIT)out.put("orbs_truncated",true);
        });
        panel(out,"mechanic_panel",()->{
            if(AbstractDungeon.isScreenUp || Settings.hidePopupDetails || stat(BOSS,"boss")!=actor)return;
            List<?> panels=(List<?>)stat("automaton.EasyInfoDisplayPanel","specialDisplays");boolean registered=false;
            for(Object display:panels)if(display!=null && display.getClass().getName().equals("charbosses.BossMechanicDisplayPanel")){registered=true;break;}
            if(!registered)return;
            String text=(String)stat("charbosses.BossMechanicDisplayPanel","mechanicDesc");
            if(text==null || text.isEmpty() || text.equals("NORENDER"))return;
            Map<String,Object> row=new LinkedHashMap<>();row.put("name",stat("charbosses.BossMechanicDisplayPanel","mechanicName"));
            row.putAll(PublicDescription.format(text,key->null));row.put("source","native_boss_mechanic_panel");out.put("mechanic_panel",row);
        });
        MechanicsCompleteness.addTo(out);
        // These two player-specific aggregate labels do not describe a boss.
        out.remove("character_specific_complete");out.remove("audited_panels_complete");
    }

    private static void captureHand(Map<String,Object> out,Object actor,Function<AbstractCard,? extends Map<String,Object>> view)throws ReflectiveOperationException {
        List<Object> rows=new ArrayList<>();out.put("hand",rows);
        if(Settings.hideCards){out.put("hand_visible",false);out.put("hand_visibility_reason","native_hide_cards");return;}
        out.put("hand_visible",true);
        CardGroup hand=(CardGroup)field(actor,"hand");Object playing=field(actor,"cardInUse");
        for(int i=0;i<Math.min(hand.group.size(),CARD_LIMIT);i++) {
            AbstractCard card=hand.group.get(i);
            if(card==playing && (PeekButton.isPeeking || AbstractDungeon.screen==AbstractDungeon.CurrentScreen.HAND_SELECT))continue;
            Map<String,Object> row=publicCard(card,view);row.put("slot",i);
            rows.add(row);
        }
        if(hand.group.size()>CARD_LIMIT)out.put("hand_truncated",true);
    }

    private static void captureRoulettePreview(Map<String,Object> out,AbstractMonster actor,Function<AbstractCard,? extends Map<String,Object>> view)throws ReflectiveOperationException {
        if(stat(BOSS,"boss")!=actor)throw new IllegalStateException("inactive native boss");
        Map<String,Object> row=new LinkedHashMap<>();row.put("visible",false);
        if(!actor.hasPower(HERMIT_WHEEL))row.put("visibility_reason","native_wheel_inactive");
        else if(Settings.hideCards)row.put("visibility_reason","native_hide_cards");
        else {
            // Cached renderer-only flag; never inspect the archetype or its future deck.
            java.lang.reflect.Field noRender=type(HERMIT).getDeclaredField("noRender");
            noRender.setAccessible(true);
            if(noRender.getBoolean(actor))row.put("visibility_reason","native_preview_hidden");
            else {
                AbstractCard preview=(AbstractCard)stat(HERMIT,"previewCard");
                if(preview==null)row.put("visibility_reason","native_preview_absent");
                else {
                    Map<String,Object> card=publicCard(preview,view);
                    row.put("visible",card.get("visible"));
                    if(Boolean.TRUE.equals(card.get("visible")))row.put("card",card);
                    else row.put("visibility_reason",card.get("visibility_reason"));
                }
            }
        }
        out.put("roulette_preview",row);
    }

    private static Map<String,Object> publicCard(AbstractCard card,Function<AbstractCard,? extends Map<String,Object>> view) {
            Map<String,Object> row=new LinkedHashMap<>();
            boolean face=card!=null && card.isSeen && !card.isLocked && !card.isFlipped
                && card.current_y>=-200.0f*Settings.scale && card.current_y<=Settings.HEIGHT+200.0f*Settings.scale;
            row.put("visible",face);
            if(face && !rendererAudited(card,new HashSet<>(Arrays.asList("com.megacrit.cardcrawl.cards.AbstractCard","charbosses.cards.AbstractBossCard","charbosses.cards.hermit.AbstractHermitBossCard","charbosses.cards.colorless.EnHandOfGreedHermitNecro")))) {
                row.put("details_complete",false);row.put("unavailable_reason","unsupported_card_renderer");
            }else if(face) {
                // Only UI card facts: generic projection also contains player payment budgets and previews.
                Map<String,Object> projected=view.apply(card);
                for(String key:Arrays.asList("id","name","upgrades","type","rarity","exhausts","ethereal",
                    "description","description_raw","description_lines","description_complete","description_truncated",
                    "unresolved_variables","displayed_values","description_source","description_rendering","text_language",
                    "displayed_cost","displayed_cost_text","displayed_cost_complete","displayed_cost_source","displayed_cost_unavailable_reason"))
                    if(projected.containsKey(key))row.put(key,projected.get(key));
                // Intent overlays deliberately omitted, including Runic Dome's hidden predicted damage.
            }else row.put("visibility_reason","card_face_hidden");
            return row;
    }

    /** Unknown overriding renderers can hide faces/panels; do not guess their visibility. */
    private static boolean rendererAudited(Object value,Set<String> owners) {
        for(Class<?> c=value.getClass();c!=null;c=c.getSuperclass())for(java.lang.reflect.Method method:c.getDeclaredMethods())
            if(!java.lang.reflect.Modifier.isAbstract(method.getModifiers())
                && (method.getName().equals("render") || method.getName().equals("renderHand")) && !owners.contains(c.getName()))return false;
        return true;
    }
    private static Map<String,Object> orb(AbstractOrb orb)throws ReflectiveOperationException {
        if(!is(orb,"charbosses.orbs.AbstractEnemyOrb"))throw new IllegalStateException("unsupported enemy orb renderer");
        Map<String,Object> row=new LinkedHashMap<>();row.put("id",orb.ID);row.put("name",orb.name);
        boolean empty=is(orb,"charbosses.orbs.EnemyEmptyOrbSlot");row.put("empty_slot",empty);
        if(empty)return row;
        boolean values=Boolean.TRUE.equals(field(orb,"showValues"));row.put("values_visible",values);
        if(values){row.put("passive_amount",orb.passiveAmount);row.put("evoke_amount",orb.evokeAmount);row.putAll(PublicDescription.format(orb.description,key->null));}
        else row.put("values_visibility_reason","native_orb_values_hidden");
        return row;
    }
}



