import javassist.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
public final class CardPlayObservationTest {
    public static void main(String[] args)throws Exception {
        ClassPool p=new ClassPool(true);Map<String,byte[]> bytes=new HashMap<>();
        CtClass player=p.makeClass("com.megacrit.cardcrawl.characters.AbstractPlayer");player.addConstructor(CtNewConstructor.defaultConstructor(player));bytes.put(player.getName(),player.toBytecode());
        CtClass monster=p.makeClass("com.megacrit.cardcrawl.monsters.AbstractMonster");monster.addField(CtField.make("public boolean allowed;",monster));monster.addConstructor(CtNewConstructor.defaultConstructor(monster));bytes.put(monster.getName(),monster.toBytecode());
        CtClass card=p.makeClass("com.megacrit.cardcrawl.cards.AbstractCard");card.addField(CtField.make("public String cantUseMessage=\"unchanged\";",card));
        card.addMethod(CtNewMethod.make("public boolean canUse(com.megacrit.cardcrawl.characters.AbstractPlayer player, com.megacrit.cardcrawl.monsters.AbstractMonster target){cantUseMessage=\"대상 조건을 만족하지 않습니다\";return target!=null && target.allowed;}",card));card.addConstructor(CtNewConstructor.defaultConstructor(card));bytes.put(card.getName(),card.toBytecode());
        String name="communicationmod.observation.CardPlayObservation";Path path=Paths.get(args[0],name.replace('.','/')+".class");if(!Files.exists(path))throw new AssertionError("Missing target-aware card availability observer");bytes.put(name,Files.readAllBytes(path));
        ClassLoader loader=new ClassLoader(CardPlayObservationTest.class.getClassLoader()) {protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException{synchronized(getClassLoadingLock(n)){Class<?> c=findLoadedClass(n);byte[] b=bytes.get(n);if(c==null&&b!=null)c=defineClass(n,b,0,b.length);if(c==null)c=super.loadClass(n,resolve);if(resolve)resolveClass(c);return c;}}};
        Class<?> c=loader.loadClass(card.getName()),pl=loader.loadClass(player.getName()),m=loader.loadClass(monster.getName());Object instance=c.newInstance(),target=m.newInstance();
        Method probe=loader.loadClass(name).getMethod("probe",c,pl,m);
        Map<?,?> blocked=(Map<?,?>)probe.invoke(null,instance,pl.newInstance(),target);
        check(Boolean.FALSE.equals(blocked.get("available")),"blocked target");check("대상 조건을 만족하지 않습니다".equals(blocked.get("reason")),"localized disabled reason");
        check(c.getField("cantUseMessage").get(instance).equals("unchanged"),"UI reason restored after query");
        m.getField("allowed").setBoolean(target,true);Map<?,?> allowed=(Map<?,?>)probe.invoke(null,instance,pl.newInstance(),target);
        check(Boolean.TRUE.equals(allowed.get("available"))&&!allowed.containsKey("reason"),"allowed target has no stale disabled reason");
        check(c.getField("cantUseMessage").get(instance).equals("unchanged"),"success restores UI reason");
        System.out.println("PASS: actual target-aware probe and localized reason without stale-message mutation");
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
