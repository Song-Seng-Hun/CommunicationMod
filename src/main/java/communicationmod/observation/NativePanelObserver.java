package communicationmod.observation;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import java.util.*;
import java.util.function.Function;

/** A pinned public panel. Visibility gates stay in each observer, including cross-character panels. */
interface NativePanelObserver {
    String failureKey();
    void capture(Map<String,Object> out,AbstractPlayer player,Function<AbstractCard,? extends Map<String,Object>> cardView) throws ReflectiveOperationException;
    static Class<?> optional(String name)throws ClassNotFoundException{return Class.forName(name,false,NativePanelObserver.class.getClassLoader());}
    static boolean visible(AbstractCard card){return card!=null && card.isSeen && !card.isLocked && !card.isFlipped;}
}
