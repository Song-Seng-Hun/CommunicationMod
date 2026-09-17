package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.map.DungeonMap;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import communicationmod.ChoiceScreenUtils;
import javassist.CannotCompileException;
import javassist.CtBehavior;

import java.util.ArrayList;

@SpirePatch(
        clz=DungeonMap.class,
        method="update"
)
public class DungeonMapPatch {

    public static boolean doBossHover = false;

    @SpireInsertPatch(
            locator=Locator.class
    )
    public static void Insert(DungeonMap _instance) {

        if(doBossHover) {
            // Consume even when a manual transition made this queued input stale.
            doBossHover = false;
            if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.MAP
                    || AbstractDungeon.getCurrRoom() == null
                    || AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMPLETE
                    || !ChoiceScreenUtils.bossNodeAvailable()) return;
            _instance.bossHb.hovered = true;
            InputHelper.justClickedLeft = true;
        }
    }

    private static class Locator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher matcher = new Matcher.MethodCallMatcher(Hitbox.class, "update");
            int[] results = LineFinder.findInOrder(ctMethodToPatch, new ArrayList<Matcher>(), matcher);
            results[0] += 1;
            return results;
        }
    }

}
