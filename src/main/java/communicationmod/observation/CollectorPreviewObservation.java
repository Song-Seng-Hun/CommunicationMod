package communicationmod.observation;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import java.util.*;
import java.util.function.Function;
import static communicationmod.observation.NativeMechanicAccess.*;

/** Mirrors the pinned public monster-hover preview using existing catalog objects, never getCollectedCard(). */
public final class CollectorPreviewObservation {
    private static final String[][] BOSS_CARDS={
        {"Ironclad","Immolate","Reaper","Barricade"},{"Silent","Corpse Explosion","After Image","Storm of Steel"},
        {"Defect","Buffer","Core Surge","Seek"},{"Watcher","ConjureBlade","Ragnarok","Omniscience"},
        {"Hermit","hermit:Adapt","hermit:Magnum","hermit:FromBeyond"}};
    private CollectorPreviewObservation() { }

    public static void addTo(Map<String,Object> out,AbstractMonster monster,Function<AbstractCard,? extends Map<String,Object>> view) {
        if(monster==null||AbstractDungeon.player==null)return;
        boolean visible=false;
        try {
            visible=Objects.equals(AbstractDungeon.player.chosenClass,stat("collector.CollectorChar$Enums","THE_COLLECTOR"));
            if(!visible)visible=!((CardGroup)stat("collector.CollectorCollection","collection")).group.isEmpty();
            if(!visible)return;
            Map<?,?> pool=(Map<?,?>)stat("collector.CollectorCollection","collectionPool");
            String id=pool.containsKey(monster.id)?(String)pool.get(monster.id):bossCard(monster);
            AbstractCard template=CardLibrary.cards.get(id);
            if(!CharacterResources.visible(template)){out.put("collectible_preview",CardPreviewObservation.unavailable("collectible_catalog_card_not_visible"));return;}
            // getCollectedCard adds exactly CollectedCardMod: Exhaust, localized prefix and descriptor.
            // Reproduce those presentation fields on a detached map, not on a native card object.
            String[] text=(String[])field(stat("collector.cardmods.CollectedCardMod","uiStrings"),"TEXT");
            if(text.length<2||text[0]==null||text[1]==null)throw new IllegalStateException("collectible localization missing");
            Map<String,Object> card=new LinkedHashMap<>(view.apply(template));
            Map<String,Object> prefix=PublicDescription.format(text[0],key->null);
            String before=String.valueOf(card.getOrDefault("description",""));
            card.put("description",prefix.get("description")+"\n"+before);card.put("exhausts",true);
            card.put("description_complete",Boolean.TRUE.equals(prefix.get("description_complete"))&&Boolean.TRUE.equals(card.get("description_complete")));
            card.put("collectible_descriptor",PublicDescription.format(text[1],key->null).get("description"));
            Map<String,Object> result=new LinkedHashMap<>();result.put("source","pinned_public_collectible_catalog");result.put("card",card);
            out.put("collectible_preview",result);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) {
            if(visible)out.put("collectible_preview",CardPreviewObservation.unavailable("native_collectible_binding_unavailable"));
        }
    }
    private static String bossCard(AbstractMonster monster)throws ReflectiveOperationException {
        for(String[] row:BOSS_CARDS)if(is(monster,"charbosses.bosses."+row[0]+".CharBoss"+row[0])) {
            Object archetype=field(monster,"chosenArchetype");
            if(archetype!=null){int act=number(field(archetype,"actNum"));return row[act==1?1:act==2?2:3];}
        }
        return "collector:DefaultCollectibleCard";
    }
}
