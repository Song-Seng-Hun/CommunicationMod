package communicationmod.observation;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import java.util.*;
import java.util.function.Function;
import static communicationmod.observation.NativeMechanicAccess.*;

/** Public objects/catalogs only; upgraded rows defer to the bounded standard preview queue. No render or roll. */
public final class CardPreviewObservation {
    private static final int LIMIT=256;
    private static final ThreadLocal<Boolean> PROJECTING=new ThreadLocal<>();
    private static final Set<String> UNKNOWN=new HashSet<>(Arrays.asList("Unknown","Unknown0Cost","Unknown1Cost","Unknown2Cost","Unknown3Cost",
        "UnknownBlock","UnknownBoss","UnknownClass","UnknownColorless","UnknownCommonAttack","UnknownCommonSkill","UnknownDexterity",
        "UnknownDraw","UnknownExhaust","UnknownRareAttack","UnknownRarePower","UnknownRareSkill","UnknownStrength","UnknownStrike",
        "UnknownUncommonAttack","UnknownUncommonPower","UnknownUncommonSkill","UnknownVulnerable","UnknownWeak","UnknownX"));
    private CardPreviewObservation() { }

    public static void addTo(Map<String,Object> out,AbstractCard card,Function<AbstractCard,? extends Map<String,Object>> view) {
        if(!CharacterResources.visible(card))return;
        boolean unknown=is(card,"sneckomod.cards.unknowns.AbstractUnknownCard");
        boolean split=card.getClass().getName().equals("slimebound.cards.SplitSpecialist");
        if(!unknown&&!split&&card.cardsToPreview==null)return;
        if(Boolean.TRUE.equals(PROJECTING.get())){out.put("previews",unavailable("nested_preview_omitted"));return;}
        PROJECTING.set(true);
        try {
            List<?> candidates;boolean ids=false,baseVariant=false;
            if(unknown) {
                if(!card.getClass().getName().equals("sneckomod.cards.unknowns."+card.getClass().getSimpleName())||!UNKNOWN.contains(card.getClass().getSimpleName())) {
                    out.put("previews",unavailable("unsupported_gallery_renderer"));return;
                }
                // Exact myList implementations are audited as read-only; never read lastUnknownRoll.
                candidates=(List<?>)call(card,"myList");ids=true;baseVariant=card.upgraded;
                if(candidates.isEmpty()&&card.cardsToPreview!=null){candidates=Collections.singletonList(card.cardsToPreview);ids=false;baseVariant=false;}
            } else if(split) {
                // choiceList() shuffles native RNG. Only read the exact renderer's cached full gallery.
                java.lang.reflect.Field cached=card.getClass().getDeclaredField("cardList");cached.setAccessible(true);
                candidates=(List<?>)cached.get(card);
            }
            else candidates=Collections.singletonList(card.cardsToPreview);
            Map<String,Object> result=new LinkedHashMap<>();List<Object> rows=new ArrayList<>();List<AbstractCard> sources=new ArrayList<>();
            result.put("source",ids?"public_unknown_gallery_catalog":split?"native_choice_previews":"native_card_preview");
            result.put("count",candidates.size());result.put("cards",rows);
            if(ids||split)result.put("order_visible",false);
            boolean complete=candidates.size()<=LIMIT;
            if(baseVariant) {
                // Finalized on detached copies by CardUpgradeObservation.finish before state publication.
                result.put("variant","base_catalog");result.put("unavailable_reason","upgraded_gallery_variants_not_cached");
            }
            for(int i=0;i<Math.min(candidates.size(),LIMIT);i++) {
                Object value=ids?CardLibrary.cards.get(candidates.get(i)):candidates.get(i);
                if(!(value instanceof AbstractCard)||value==card||!CharacterResources.visible((AbstractCard)value)){complete=false;continue;}
                rows.add(new LinkedHashMap<>(view.apply((AbstractCard)value)));
                sources.add((AbstractCard)value);
            }
            result.put("cards_complete",complete);
            if(candidates.size()>LIMIT)result.put("truncated",true);
            if(baseVariant)CardUpgradeObservation.offerGallery(result,sources);
            out.put("previews",result);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) {
            out.put("previews",unavailable("native_preview_binding_unavailable"));
        } finally {PROJECTING.remove();}
    }
    static Map<String,Object> unavailable(String reason) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("status","unavailable");result.put("cards_complete",false);result.put("unavailable_reason",reason);return result;
    }
}
