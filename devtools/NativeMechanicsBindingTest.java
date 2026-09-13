import javassist.*;
import javassist.expr.*;
import java.util.*;

/** Installed signatures and access levels, checked without initializing native classes. */
public final class NativeMechanicsBindingTest {
    public static void main(String[] args)throws Exception {
        ClassPool p=new ClassPool(true);for(String jar:args)p.insertClassPath(jar);
        String[] characters={"com.megacrit.cardcrawl.characters.Ironclad","com.megacrit.cardcrawl.characters.TheSilent","com.megacrit.cardcrawl.characters.Defect","com.megacrit.cardcrawl.characters.Watcher",
            "automaton.AutomatonChar","awakenedOne.AwakenedOneChar","champ.ChampChar","collector.CollectorChar","gremlin.characters.GremlinCharacter","guardian.characters.GuardianCharacter","hermit.characters.hermit","slimebound.characters.SlimeboundCharacter","sneckomod.TheSnecko","theHexaghost.TheHexaghost"};
        for(String character:characters)check(p.get(character).subtypeOf(p.get("com.megacrit.cardcrawl.characters.AbstractPlayer")),"native player "+character);
        String[][] fields={
            {"automaton.FunctionHelper","doStuff","held","secretStorage"},{"automaton.EasyInfoDisplayPanel","specialDisplays"},
            {"theHexaghost.HexaMod","renderFlames"},{"theHexaghost.GhostflameHelper","hexaGhostFlames","activeGhostFlame"},
            {"theHexaghost.ghostflames.AbstractGhostflame","charged","triggersRequired"},{"theHexaghost.ghostflames.InfernoGhostflame","energySpentThisTurn"},
            {"collector.CollectorCollection","collection","combatCollection"},{"gremlin.characters.GremlinCharacter","currentGremlin","mobState","nob"},
            {"gremlin.patches.GremlinMobState","gremlins","inCombat"},{"gremlin.orbs.GremlinStandby","hp","assetFolder"},
            {"awakenedOne.AwakenedOneMod","UP_NEXT","powersThisCombat"},{"awakenedOne.actions.ConjureAction","conjuresThisCombat"},
            {"awakenedOne.powers.DemonGlyphPower","POWER_ID"},{"awakenedOne.util.Wiz","POWERS_TO_AWAKEN"},{"awakenedOne.ui.OrbitingSpells","spellCards"},
            {"champ.stances.AbstractChampStance","charged"},{"slimebound.characters.SlimeboundCharacter","puddleForm"},
            {"slimebound.orbs.SpawnedSlime","upgraded","extraFontColor","debuffAmount","slimeBonus","noRender"},
            {"guardian.orbs.StasisOrb","stasisCard"},{"guardian.cards.AbstractGuardianCard","sockets","socketCount","thisGemsType"},
            {"sneckomod.patches.UnknownExtraUiPatch","parentCard"},{"com.evacipated.cardcrawl.mod.stslib.patches.core.AbstractCreature.TempHPField","tempHp"},
            {"com.megacrit.cardcrawl.characters.AbstractPlayer","maxOrbs"}};
        for(String[] binding:fields)for(int i=1;i<binding.length;i++)check(Modifier.isPublic(p.get(binding[0]).getField(binding[i]).getModifiers()),"public binding "+binding[0]+"."+binding[i]);
        check(Modifier.isPrivate(p.get("theHexaghost.ghostflames.InfernoGhostflame").getDeclaredField("DESCRIPTIONS").getModifiers()),"Inferno localization uses exact private field binding");
        String[][] getters={{"collector.util.NewReserves","reserveCount"},{"collector.util.EssenceSystem","essenceCount"},{"automaton.FunctionHelper","max"},
            {"gremlin.patches.GremlinMobState","getGremlinHP","isEnslaved"},{"champ.stances.AbstractChampStance","getRemainingChargeCount"},
            {"champ.StanceHelper","getStanceTechnique","getStanceFinisher"},{"champ.stances.DefensiveStance","amount","finisherAmount"},{"champ.stances.BerserkerStance","amount"},
            {"awakenedOne.util.Wiz","isAwakened","isInCombat","hasConjure"},{"automaton.CompileDisplayPanel","getTitle","getDescription"},
            {"automaton.cards.AbstractBronzeCard","getSpecialCompileText","lastCard"},{"automaton.cards.Terminator","getSpecialCompileText"},{"automaton.cards.ChosenStrike","getSpecialCompileText"},{"automaton.cards.InfiniteLoop","getSpecialCompileText"}};
        int checked=0;
        for(String[] binding:getters)for(int i=1;i<binding.length;i++){readOnly(p.get(binding[0]).getDeclaredMethod(binding[i]));checked++;}
        for(String flame:new String[]{"Searing","Crushing","Bolstering","Inferno","Mayhem"}) {
            CtClass type=p.get("theHexaghost.ghostflames."+flame+"Ghostflame");
            for(String getter:new String[]{"getName","getActiveFlamesTriggerCount","getEffectCount"}){readOnly(type.getDeclaredMethod(getter));checked++;}
            if(!flame.equals("Inferno")){readOnly(type.getDeclaredMethod("getDescription"));checked++;}
        }
        System.out.println("PASS: all 14 native player types, public/private resource bindings and "+checked+" audited read-only getter bodies");
    }
    static void readOnly(CtMethod method)throws Exception {
        method.instrument(new ExprEditor(){
            public void edit(FieldAccess field){check(!field.isWriter(),"observer getter writes state: "+method.getLongName());}
            public void edit(MethodCall call){check(!call.getClassName().equals("java.io.PrintStream"),"observer getter prints: "+method.getLongName());
                check(!call.getClassName().equals("com.megacrit.cardcrawl.random.Random"),"observer getter consumes RNG: "+method.getLongName());}
        });
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
