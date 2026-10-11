import javassist.*;
import javassist.expr.*;
import java.util.*;
import java.util.jar.*;
/** Missing actual converter wiring and native privacy gates are not covered by player-mechanics tests. */
public final class BossObservationBindingTest {
    public static void main(String[] args)throws Exception {
        ClassPool p=new ClassPool(true);for(String path:args)p.insertClassPath(path);
        CtClass boss=p.get("charbosses.bosses.AbstractCharBoss");
        for(String field:new String[]{"hand","relics","orbs","stance","energy","energyPanel","boss"})check(Modifier.isPublic(boss.getField(field).getModifiers()),"public boss binding "+field);
        requireRead(boss.getDeclaredMethod("render"),"isDead","stance","relics","orbs","energyPanel");
        requireRead(boss.getDeclaredMethod("renderHand"),"hand","cardInUse");
        CtClass hermit=p.get("charbosses.bosses.Hermit.CharBossHermit");
        requireRead(hermit.getDeclaredMethod("render"),"previewCard","noRender","isDead","isDying");
        check(Modifier.isPublic(hermit.getDeclaredField("previewCard").getModifiers()) && Modifier.isStatic(hermit.getDeclaredField("previewCard").getModifiers()),"native public roulette preview binding");
        check(Modifier.isPrivate(hermit.getDeclaredField("noRender").getModifiers()),"cached renderer-only preview mask binding");
        requireRead(p.get("com.megacrit.cardcrawl.cards.AbstractCard").getDeclaredMethod("render",new CtClass[]{p.get("com.badlogic.gdx.graphics.g2d.SpriteBatch")}),"hideCards");
        requireRead(p.get("charbosses.ui.EnemyEnergyPanel").getDeclaredMethod("render"),"totalCount","energy");
        requireRead(p.get("charbosses.cards.AbstractBossCard").getDeclaredMethod("render"),"showIntent","hov2","isDying","isEscaping","hideCombatElements");
        requireRead(p.get("automaton.SuperTip").getDeclaredMethod("render"),"isScreenUp","specialDisplays","hidePopupDetails");
        requireRead(p.get("charbosses.orbs.AbstractEnemyOrb").getDeclaredMethod("renderText"),"showValues","evokeAmount","passiveAmount");
        requireRead(p.get("charbosses.stances.EnWrathStance").getDeclaredMethod("updateAnimation"),"effectsQueue");
        for(String f:new String[]{"name","description"})check(Modifier.isPublic(p.get("charbosses.stances.AbstractEnemyStance").getDeclaredField(f).getModifiers()),"shadowed enemy stance "+f);
        // Freeze all face-render override families: unknown native renderers require a new audit.
        Set<String> faceRenderers=new TreeSet<>();
        try(JarFile jar=new JarFile(args[1])){Enumeration<JarEntry> es=jar.entries();while(es.hasMoreElements()){
            String n=es.nextElement().getName();if(!n.startsWith("charbosses/cards/")||!n.endsWith(".class"))continue;
            CtClass c=p.get(n.substring(0,n.length()-6).replace('/','.'));
            for(CtMethod m:c.getDeclaredMethods())if(m.getName().equals("render"))faceRenderers.add(c.getName());
        }}
        check(faceRenderers.equals(new TreeSet<>(Arrays.asList("charbosses.cards.AbstractBossCard","charbosses.cards.hermit.AbstractHermitBossCard","charbosses.cards.colorless.EnHandOfGreedHermitNecro"))),"native face-render overrides changed: "+faceRenderers);
        final boolean[] wired={false};p.get("communicationmod.GameStateConverter").getDeclaredMethod("convertMonsterToJson").instrument(new ExprEditor(){public void edit(MethodCall c){if(c.getClassName().equals("communicationmod.observation.BossObservation")&&c.getMethodName().equals("addTo"))wired[0]=true;}});
        check(wired[0],"actual monster converter omits public boss UI");
        CtClass observer=p.get("communicationmod.observation.BossObservation");
        for(CtMethod m:observer.getDeclaredMethods())m.instrument(new ExprEditor(){
            public void edit(FieldAccess f){check(!f.isWriter(),"observer mutates native field: "+f.getFieldName());}
            public void edit(MethodCall c){String n=c.getMethodName();check(!n.equals("render")&&!n.equals("makeCopy")&&!n.equals("updateDescription")&&!n.equals("generateDeck")&&!n.equals("applyPowers"),"observer calls native mutation/renderer: "+n);check(!c.getClassName().contains("cardcrawl.random"),"observer consumes RNG");}
        });
        System.out.println("PASS: native boss bindings, renderer visibility contracts, read-only body and actual converter wiring");
    }
    static void requireRead(CtMethod m,String... names)throws Exception {Set<String> actual=new HashSet<>();m.instrument(new ExprEditor(){public void edit(FieldAccess f){if(f.isReader())actual.add(f.getFieldName());}});for(String n:names)check(actual.contains(n),"native renderer no longer reads "+m.getLongName()+" / "+n);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

