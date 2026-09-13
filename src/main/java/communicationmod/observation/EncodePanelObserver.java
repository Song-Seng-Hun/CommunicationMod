package communicationmod.observation;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import java.util.*;
import java.util.function.Function;

import static communicationmod.observation.NativePanelObserver.*;

/** Native visible encode slots and existing function preview only. */
final class EncodePanelObserver implements NativePanelObserver {
    public String failureKey(){return "encode_unavailable_reason";}
    public void capture(Map<String,Object> out,AbstractPlayer player,Function<AbstractCard,? extends Map<String,Object>> cardView)throws ReflectiveOperationException {
            {
                Class<?> helper=optional("automaton.FunctionHelper");
                if(Boolean.TRUE.equals(helper.getField("doStuff").get(null))) {
                    Object group=helper.getField("held").get(null);
                    List<Object> sequence=new ArrayList<>();
                    int capacity=((Number)helper.getMethod("max").invoke(null)).intValue();
                    if(capacity<0 || capacity>16)throw new IllegalStateException("unknown_encode_capacity");
                    if(group instanceof CardGroup) {
                        List<AbstractCard> held=((CardGroup)group).group;
                        for(int i=0;i<Math.min(capacity,held.size());i++)if(visible(held.get(i))) {
                            Map<String,Object> row=new LinkedHashMap<>(cardView.apply(held.get(i)));row.put("encode_slot",i);sequence.add(row);
                        }
                        out.put("encode_sequence_complete",sequence.size()==Math.min(capacity,held.size()));
                    }
                    out.put("encode_capacity",capacity);
                    out.put("encode_sequence",sequence);
                    // Despite its implementation name, this is the existing function card rendered by FunctionHelper.render.
                    Object preview=helper.getField("secretStorage").get(null);
                    if(preview instanceof AbstractCard && visible((AbstractCard)preview))out.put("function_preview",cardView.apply((AbstractCard)preview));
                }
            }
    }
}
