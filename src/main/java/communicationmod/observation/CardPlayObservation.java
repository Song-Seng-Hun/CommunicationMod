package communicationmod.observation;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import java.util.*;

/** Target-specific native play predicates, preserving their transient UI error text. */
public final class CardPlayObservation {
    private CardPlayObservation() { }
    public static Map<String,Object> probe(AbstractCard card,AbstractPlayer player,AbstractMonster target) {
        String before=card.cantUseMessage;Map<String,Object> out=new LinkedHashMap<>();
        try {
            card.cantUseMessage=null;boolean allowed=card.canUse(player,target);out.put("available",allowed);
            if(!allowed)out.put("reason",card.cantUseMessage==null || card.cantUseMessage.isEmpty()?"native_card_use_condition_not_met":card.cantUseMessage);
        } finally {card.cantUseMessage=before;}
        return out;
    }
    public static boolean canUse(AbstractCard card,AbstractPlayer player,AbstractMonster target){return Boolean.TRUE.equals(probe(card,player,target).get("available"));}
    public static void addTo(Map<String,Object> out,AbstractCard card) {
        boolean targeted=card.target==AbstractCard.CardTarget.ENEMY || card.target==AbstractCard.CardTarget.SELF_AND_ENEMY;
        boolean playable=false;List<Object> targets=new ArrayList<>();
        if(targeted) {
            List<AbstractMonster> monsters=AbstractDungeon.getMonsters().monsters;
            for(int i=0;i<monsters.size();i++) {
                AbstractMonster target=monsters.get(i);if(target.isDeadOrEscaped() || target.isDying)continue;
                Map<String,Object> row=probe(card,AbstractDungeon.player,target);row.put("monster_index",i);row.put("name",target.name);
                playable|=Boolean.TRUE.equals(row.get("available"));targets.add(row);
            }
            out.put("target_playability",targets);
            if(targets.isEmpty())out.put("unplayable_reason","no_live_targets");
        } else {
            Map<String,Object> result=probe(card,AbstractDungeon.player,null);playable=Boolean.TRUE.equals(result.get("available"));
            if(!playable)out.put("unplayable_reason",result.get("reason"));
        }
        out.put("is_playable",playable);out.put("playability_source","native_canUse_by_target");
    }
}
