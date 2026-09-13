package communicationmod.observation;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import java.util.*;
import java.util.function.Function;
import static communicationmod.observation.NativeMechanicAccess.*;

/** Public UI state only. Does not construct cards/orbs, roll spells, or refresh native caches. */
public final class CharacterResources {
    private CharacterResources() { }
    public static void addTo(Map<String,Object> out,AbstractPlayer player,Function<AbstractCard,? extends Map<String,Object>> view) {
        if(player==null)return;
        panel(out,"temporary_hp",()->out.put("temporary_hp",spire("com.evacipated.cardcrawl.mod.stslib.patches.core.AbstractCreature.TempHPField","tempHp",player)));
        panel(out,"orb_slots",()->out.put("max_orb_slots",field(player,"maxOrbs")));
        panel(out,"compile_panel",()->{
            if(!Boolean.TRUE.equals(stat("automaton.FunctionHelper","doStuff")))return;
            CardGroup held=(CardGroup)stat("automaton.FunctionHelper","held");
            for(AbstractCard card:held.group) {
                if(!visible(card))throw new IllegalStateException("masked compile card");
                if(is(card,"automaton.cards.AbstractBronzeCard")) {
                    String owner=card.getClass().getMethod("getSpecialCompileText").getDeclaringClass().getName();
                    if(!Arrays.asList("automaton.cards.AbstractBronzeCard","automaton.cards.ChosenStrike","automaton.cards.InfiniteLoop","automaton.cards.Terminator").contains(owner))
                        throw new IllegalStateException("unknown compile getter");
                }
            }
            for(Object value:(List<?>)stat("automaton.EasyInfoDisplayPanel","specialDisplays"))if(value.getClass().getName().equals("automaton.CompileDisplayPanel")) {
                Map<String,Object> row=PublicDescription.format((String)call(value,"getDescription"),key->null);
                row.put("title",call(value,"getTitle"));out.put("compile_panel",row);break;
            }
        });
        panel(out,"collector",()->{
            boolean collector=is(player,"collector.CollectorChar");
            int reserves=number(call(type("collector.util.NewReserves"),"reserveCount"));
            if(collector || reserves>0)out.put("reserves",reserves);
            if(!collector)return;
            out.put("essence",call(type("collector.util.EssenceSystem"),"essenceCount"));
            out.put("collection",cards((CardGroup)stat("collector.CollectorCollection","collection"),view));
            if(CombatObservation.inCombat())out.put("combat_collection",cards((CardGroup)stat("collector.CollectorCollection","combatCollection"),view));
        });
        if(is(player,"gremlin.characters.GremlinCharacter"))panel(out,"gremlins",()->{
            Object mob=field(player,"mobState");List<?> ids=(List<?>)field(mob,"gremlins");
            if(!mob.getClass().getName().equals("gremlin.patches.GremlinMobState"))throw new IllegalStateException("unknown mob getter");
            if(ids.size()>5)throw new IllegalStateException("formation budget");
            String active=(String)field(player,"currentGremlin");boolean combat=Boolean.TRUE.equals(field(mob,"inCombat"));
            List<Object> rows=new ArrayList<>();
            for(int i=0;i<ids.size();i++) {
                String id=(String)ids.get(i);Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("active",id.equals(active));
                int hp=number(mob.getClass().getMethod("getGremlinHP",int.class).invoke(mob,i));
                if(combat) {
                    hp=Boolean.TRUE.equals(mob.getClass().getMethod("isEnslaved",String.class).invoke(mob,id))?-1:0;
                    if(id.equals(active))hp=player.currentHealth;
                    else for(AbstractOrb orb:player.orbs)if(is(orb,"gremlin.orbs.GremlinStandby") && id.equals(field(orb,"assetFolder"))){hp=number(field(orb,"hp"));row.put("name",orb.name);break;}
                }
                row.put("hp",Math.max(0,hp));row.put("enslaved",hp<0);rows.add(row);
            }
            out.put("gremlins",rows);out.put("active_gremlin",active);out.put("nob_form",field(player,"nob"));
        });
        panel(out,"spells",()->{
            Class<?> wiz=type("awakenedOne.util.Wiz");
            if(!Boolean.TRUE.equals(call(wiz,"isInCombat")))return;
            boolean shown=is(player,"awakenedOne.AwakenedOneChar") || Boolean.TRUE.equals(call(wiz,"hasConjure"))
                || number(stat("awakenedOne.actions.ConjureAction","conjuresThisCombat"))>0
                || player.hasPower((String)stat("awakenedOne.powers.DemonGlyphPower","POWER_ID"));
            if(!shown)return;
            List<?> spells=(List<?>)stat("awakenedOne.ui.OrbitingSpells","spellCards");
            if(spells.size()>256)throw new IllegalStateException("spell budget");
            Map<String,Map<String,Object>> groups=new LinkedHashMap<>();
            Object next=stat("awakenedOne.AwakenedOneMod","UP_NEXT");
            boolean spellsComplete=true;
            for(Object value:spells) {
                AbstractCard card=(AbstractCard)value;if(!visible(card)){spellsComplete=false;continue;}
                Map<String,Object> row=groups.get(card.cardID);
                if(row==null){row=new LinkedHashMap<>();row.put("card",view.apply(card));row.put("count",0);row.put("up_next",false);groups.put(card.cardID,row);}
                row.put("count",number(row.get("count"))+1);
                if(card.tags.contains(next))row.put("up_next",true);
            }
            out.put("spells",new ArrayList<>(groups.values()));
            out.put("spells_complete",spellsComplete);
            int threshold=number(field(wiz,"POWERS_TO_AWAKEN"));
            out.put("awakening_threshold",threshold);out.put("awakening_progress",Math.min(number(stat("awakenedOne.AwakenedOneMod","powersThisCombat")),threshold-1));
            out.put("awakened",call(wiz,"isAwakened"));
        });
        panel(out,"champ_stance",()->{
            if(!is(player.stance,"champ.stances.AbstractChampStance"))return;
            String name=player.stance.getClass().getSimpleName();
            if(!player.stance.getClass().getName().equals("champ.stances."+name) || !Arrays.asList("BerserkerStance","DefensiveStance","UltimateStance").contains(name))throw new IllegalStateException("unknown stance");
            Map<String,Object> stance=new LinkedHashMap<>();stance.put("id",player.stance.ID);stance.put("charged",field(player.stance,"charged"));
            stance.put("technique_charges",call(player.stance,"getRemainingChargeCount"));
            stance.putAll(PublicDescription.format(player.stance.description,key->null));out.put("champ_stance",stance);
            panel(stance,"abilities",()->{
                Class<?> helper=type("champ.StanceHelper");
                stance.put("technique",PublicDescription.format((String)call(helper,"getStanceTechnique"),key->null));
                stance.put("finisher",PublicDescription.format((String)call(helper,"getStanceFinisher"),key->null));
            });
        });
        if(is(player,"slimebound.characters.SlimeboundCharacter"))panel(out,"slime_form",()->out.put("puddle_form",field(player,"puddleForm")));
    }
    private static Map<String,Object> cards(CardGroup group,Function<AbstractCard,? extends Map<String,Object>> view) {
        Map<String,Object> out=new LinkedHashMap<>();List<AbstractCard> copy=new ArrayList<>();
        for(AbstractCard card:group.group)if(visible(card))copy.add(card);
        copy.sort(Comparator.comparing((AbstractCard c)->c.cardID).thenComparing(c->String.valueOf(c.uuid)));
        // Like the public deck, retain the snapshot contents; MCP pages this immutable projection.
        // Filtering before sorting also keeps hidden/null entries out of ordering and projection.
        List<Object> rows=new ArrayList<>();
        for(AbstractCard card:copy)rows.add(view.apply(card));
        out.put("count",group.group.size());out.put("cards",rows);out.put("order_visible",false);
        out.put("cards_complete",rows.size()==group.group.size());return out;
    }
    public static Map<String,Object> cardDetails(AbstractCard card) {
        Map<String,Object> out=new LinkedHashMap<>();if(!visible(card))return out;
        if(is(card,"guardian.cards.AbstractGuardianCard"))panel(out,"sockets",()->{
            List<?> sockets=(List<?>)field(card,"sockets");if(sockets.size()>16)throw new IllegalStateException("socket budget");
            List<String> colors=new ArrayList<>();for(Object socket:sockets)colors.add(((Enum<?>)socket).name());
            out.put("socket_capacity",field(card,"socketCount"));out.put("sockets",colors);
            Object gem=field(card,"thisGemsType");if(gem!=null)out.put("gem_type",((Enum<?>)gem).name());
        });
        panel(out,"unknown_origin",()->{
            Object parent=spire("sneckomod.patches.UnknownExtraUiPatch","parentCard",card);
            if(parent instanceof AbstractCard && visible((AbstractCard)parent)) {
                AbstractCard origin=(AbstractCard)parent;Map<String,Object> row=new LinkedHashMap<>();row.put("id",origin.cardID);row.put("name",origin.name);
                out.put("unknown_origin",row); // Already transformed origin only; never the next roll/replacement pool.
            }
        });
        return out;
    }
    static boolean visible(AbstractCard card){return card!=null && card.isSeen && !card.isLocked && !card.isFlipped;}
}
