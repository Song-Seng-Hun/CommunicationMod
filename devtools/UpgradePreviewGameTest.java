import javassist.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Real GameAdapter/service and installed SearingBlow/Strike/Hermit upgrade bodies.
 * Graphics, localization and constructor dependencies are field-only fixtures.
 * This is not a live gameplay or arbitrary-mod-side-effect acceptance test.
 */
public final class UpgradePreviewGameTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        ClassPool p=new ClassPool(true),installed=new ClassPool(true);
        installed.insertClassPath(args[0]);installed.insertClassPath(args[1]);installed.insertClassPath(args[2]);
        Map<String,byte[]> bytes=new HashMap<>();
        fixture(p,bytes,"com.megacrit.cardcrawl.cards.AbstractCard$CardTarget",new String[0],new String[]{"public String name(){return \"ENEMY\";}"});
        fixture(p,bytes,"com.megacrit.cardcrawl.localization.CardStrings",new String[]{"public String NAME=\"Searing Blow\";"},new String[0]);
        fixture(p,bytes,"com.megacrit.cardcrawl.cards.AbstractCard",new String[]{
            "public String cardID=\"fixture\";","public String name=\"Card\";","public String rawDescription=\"Damage\";",
            "public java.util.UUID uuid=java.util.UUID.randomUUID();","public int timesUpgraded;","public int misc;",
            "public int cost=2;","public int costForTurn=2;","public int baseDamage=12;","public int damage=12;","public int baseBlock;","public int baseMagicNumber;",
            "public boolean upgraded;","public boolean exhaust;","public boolean isEthereal;","public boolean isInnate;","public boolean selfRetain;","public boolean retain;",
            "public boolean isSeen=true;","public boolean isLocked;","public boolean isFlipped;","public int copyCalls;","public int descriptionCalls;",
            "public com.megacrit.cardcrawl.cards.AbstractCard$CardTarget target=new com.megacrit.cardcrawl.cards.AbstractCard$CardTarget();"
        },new String[]{
            "public boolean canUpgrade(){return !upgraded;}","public void upgrade(){}",
            "public void upgradeDamage(int amount){baseDamage+=amount;damage=baseDamage;}",
            "public void upgradeName(){timesUpgraded++;upgraded=true;name=name+\"+\";}",
            "public void initializeTitle(){}","public void displayUpgrades(){damage=baseDamage;}","public void initializeDescription(){descriptionCalls++;}",
            "public com.megacrit.cardcrawl.cards.AbstractCard makeStatEquivalentCopy(){copyCalls++;try { com.megacrit.cardcrawl.cards.AbstractCard c=(com.megacrit.cardcrawl.cards.AbstractCard)getClass().newInstance();java.lang.reflect.Field[] fs=com.megacrit.cardcrawl.cards.AbstractCard.class.getFields();for(int i=0;i<fs.length;i++){java.lang.reflect.Field f=fs[i];if(!java.lang.reflect.Modifier.isStatic(f.getModifiers())&&!f.getName().equals(\"copyCalls\")&&!f.getName().equals(\"descriptionCalls\"))f.set(c,f.get(this));}return c;}catch(Exception e){throw new RuntimeException(e);}}"
        });
        for(String name:Arrays.asList("com.megacrit.cardcrawl.cards.red.SearingBlow","com.megacrit.cardcrawl.cards.red.Strike_Red","hermit.cards.Snapshot")) {
            CtClass real=installed.get(name),card=p.makeClass(name,p.get("com.megacrit.cardcrawl.cards.AbstractCard"));
            card.addField(CtField.make("public static com.megacrit.cardcrawl.localization.CardStrings cardStrings=new com.megacrit.cardcrawl.localization.CardStrings();",card));
            card.addConstructor(CtNewConstructor.defaultConstructor(card));
            card.addMethod(CtNewMethod.copy(real.getDeclaredMethod("upgrade"),card,null));
            if(name.endsWith("SearingBlow"))card.addMethod(CtNewMethod.copy(real.getDeclaredMethod("canUpgrade"),card,null));
            bytes.put(name,card.toBytecode());
        }
        fixture(p,bytes,"com.megacrit.cardcrawl.cards.CardGroup",new String[]{"public java.util.ArrayList group=new java.util.ArrayList();"},new String[0]);
        fixture(p,bytes,"com.megacrit.cardcrawl.characters.AbstractPlayer",new String[0],new String[0]);
        fixture(p,bytes,"com.megacrit.cardcrawl.dungeons.AbstractDungeon",new String[]{"public static com.megacrit.cardcrawl.characters.AbstractPlayer player=new com.megacrit.cardcrawl.characters.AbstractPlayer();"},new String[0]);
        fixture(p,bytes,"com.megacrit.cardcrawl.screens.select.GridCardSelectScreen",new String[]{
            "public boolean confirmScreenUp;","public com.megacrit.cardcrawl.cards.AbstractCard hoveredCard;",
            "public com.megacrit.cardcrawl.cards.AbstractCard upgradePreviewCard;","public com.megacrit.cardcrawl.cards.AbstractCard branchUpgradePreviewCard_204;",
            "public java.util.ArrayList previewCards_211=new java.util.ArrayList();","public com.megacrit.cardcrawl.cards.CardGroup targetGroup=new com.megacrit.cardcrawl.cards.CardGroup();",
            "public Boolean waitingForUpgradeSelection_754=Boolean.FALSE;","public Boolean waitingForBranchUpgradeSelection_561=Boolean.FALSE;","public Boolean isBranchUpgrading_600=Boolean.FALSE;"
        },new String[0]);
        CtClass multi=p.makeInterface("com.evacipated.cardcrawl.mod.stslib.cards.interfaces.MultiUpgradeCard");bytes.put(multi.getName(),multi.toBytecode());
        CtClass multiCard=p.makeClass("fixture.MultiCard",p.get("com.megacrit.cardcrawl.cards.AbstractCard"));multiCard.addInterface(multi);multiCard.addConstructor(CtNewConstructor.defaultConstructor(multiCard));bytes.put(multiCard.getName(),multiCard.toBytecode());
        CtClass branchInterface=p.makeInterface("com.evacipated.cardcrawl.mod.stslib.cards.interfaces.BranchingUpgradesCard");bytes.put(branchInterface.getName(),branchInterface.toBytecode());
        CtClass branchCard=p.makeClass("fixture.BranchCard",p.get("com.megacrit.cardcrawl.cards.AbstractCard"));branchCard.addInterface(branchInterface);branchCard.addConstructor(CtNewConstructor.defaultConstructor(branchCard));bytes.put(branchCard.getName(),branchCard.toBytecode());
        fixture(p,bytes,"fixture.Vertex",new String[]{"public int index;","public boolean strict;","public com.megacrit.cardcrawl.cards.AbstractCard card;","public java.util.ArrayList parents=new java.util.ArrayList();","public java.util.ArrayList exclusions=new java.util.ArrayList();"},new String[0]);
        fixture(p,bytes,"fixture.Graph",new String[]{"public java.util.ArrayList vertices=new java.util.ArrayList();"},new String[0]);
        fixture(p,bytes,"com.evacipated.cardcrawl.mod.stslib.ui.MultiUpgradeTree",new String[]{"public static com.megacrit.cardcrawl.cards.AbstractCard mainCard;","public static java.util.ArrayList cardList=new java.util.ArrayList();","public static java.util.ArrayList takenList=new java.util.ArrayList();","public static java.util.ArrayList lockedList=new java.util.ArrayList();","public static Object cardGraph;"},new String[0]);
        fixture(p,bytes,"com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton",new String[]{"public com.megacrit.cardcrawl.cards.AbstractCard cardToPreview;"},new String[0]);
        CtClass eventSubclass=p.makeClass("fixture.CustomEventButton",p.get("com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton"));
        eventSubclass.addConstructor(CtNewConstructor.defaultConstructor(eventSubclass));bytes.put(eventSubclass.getName(),eventSubclass.toBytecode());
        fixture(p,bytes,"communicationmod.observation.CardObservation",new String[0],new String[]{
            "public static void addTo(java.util.Map output,com.megacrit.cardcrawl.cards.AbstractCard card){output.put(\"description\",\"피해 \"+card.baseDamage);output.put(\"description_complete\",Boolean.TRUE);output.put(\"displayed_cost_complete\",Boolean.FALSE);}"});
        for(String suffix:Arrays.asList("","$GameAdapter","$Candidate")) {
            String name="communicationmod.observation.CardUpgradeObservation"+suffix;
            bytes.put(name,installed.get(name).toBytecode());
        }
        try {String name="communicationmod.observation.NativeUpgradeTree";bytes.put(name,installed.get(name).toBytecode());}catch(NotFoundException beforeImplementation){}
        ClassLoader loader=new ClassLoader(UpgradePreviewGameTest.class.getClassLoader()) {
            protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)) {
                    Class<?> found=findLoadedClass(name);byte[] b=bytes.get(name);
                    if(found==null&&b!=null)found=defineClass(name,b,0,b.length);
                    if(found==null){if(name.startsWith("com.megacrit.")||name.startsWith("hermit."))throw new ClassNotFoundException("Unexpected native dependency "+name);return super.loadClass(name,resolve);}
                    if(resolve)resolveClass(found);return found;
                }
            }
        };
        Class<?> cardType=loader.loadClass("com.megacrit.cardcrawl.cards.AbstractCard");
        Class<?> observation=loader.loadClass("communicationmod.observation.CardUpgradeObservation");
        Method begin=observation.getMethod("begin"),offer=observation.getMethod("offer",Map.class,cardType),finish=observation.getMethod("finish",Map.class);
        Object searing=loader.loadClass("com.megacrit.cardcrawl.cards.red.SearingBlow").newInstance();
        cardType.getField("cardID").set(searing,"Searing Blow");
        for(int i=0;i<3;i++)cardType.getMethod("upgrade").invoke(searing);
        check(cardType.getField("baseDamage").getInt(searing)==27,"installed Searing Blow +3 baseline");
        Map<String,Object> json=new HashMap<>(),state=new HashMap<>();state.put("floor",10);
        begin.invoke(null);offer.invoke(null,json,searing);finish.invoke(null,state);
        Map<String,Object> preview=(Map<String,Object>)json.get("upgrade_preview"),after=(Map<String,Object>)preview.get("after");
        check(preview.get("status").equals("available"),"real adapter produced Searing preview: "+preview);
        check(after.get("upgrades").equals(4)&&after.get("base_damage").equals(34),"installed repeat upgrade body executed once");
        check(after.get("description").equals("피해 34")&&!after.containsKey("uuid"),"new values and no generated preview identity");
        check(cardType.getField("timesUpgraded").getInt(searing)==3&&cardType.getField("descriptionCalls").getInt(searing)==0,"original upgrade/description untouched");
        check(Boolean.FALSE.equals(after.get("displayed_cost_complete")),"incompleteness retained");
        begin.invoke(null);offer.invoke(null,new HashMap<>(),searing);finish.invoke(null,state);
        check(cardType.getField("copyCalls").getInt(searing)==1,"actual binding caches stable context");
        state.put("floor",11);begin.invoke(null);offer.invoke(null,new HashMap<>(),searing);finish.invoke(null,state);
        check(cardType.getField("copyCalls").getInt(searing)==2,"actual binding invalidates context");
        for(String name:Arrays.asList("com.megacrit.cardcrawl.cards.red.Strike_Red","hermit.cards.Snapshot")) {
            Object card=loader.loadClass(name).newInstance();Map<String,Object> out=new HashMap<>();
            begin.invoke(null);offer.invoke(null,out,card);finish.invoke(null,state);
            Map<String,Object> pview=(Map<String,Object>)out.get("upgrade_preview");
            int expected=name.endsWith("Snapshot")?14:15;
            check(((Map<?,?>)pview.get("after")).get("base_damage").equals(expected),"installed upgrade body: "+name);
            check(Boolean.FALSE.equals(pview.get("can_upgrade_after")),"ordinary one-time cap: "+name);
        }
        Class<?> gridType=loader.loadClass("com.megacrit.cardcrawl.screens.select.GridCardSelectScreen");Object grid=gridType.newInstance();
        gridType.getField("confirmScreenUp").setBoolean(grid,true);gridType.getField("hoveredCard").set(grid,searing);
        Object nativeAfter=cardType.getMethod("makeStatEquivalentCopy").invoke(searing);cardType.getMethod("upgrade").invoke(nativeAfter);
        gridType.getField("upgradePreviewCard").set(grid,nativeAfter);
        Method nativePreview=observation.getMethod("nativeGridPreview",gridType);
        Map<String,Object> nativeResult=(Map<String,Object>)nativePreview.invoke(null,grid);
        check(nativeResult.get("status").equals("not_displayed"),"stale hover outside target group suppressed");
        Object group=gridType.getField("targetGroup").get(grid);((List<Object>)group.getClass().getField("group").get(group)).add(searing);
        gridType.getField("branchUpgradePreviewCard_204").set(grid,nativeAfter);
        ((List<Object>)gridType.getField("previewCards_211").get(grid)).add(nativeAfter);
        nativeResult=(Map<String,Object>)nativePreview.invoke(null,grid);
        check(nativeResult.get("status").equals("displayed")&&((List<?>)nativeResult.get("alternatives")).isEmpty(),"stale branch/multi previews never attach to an ordinary card");
        check(cardType.getField("timesUpgraded").getInt(nativeAfter)==4&&cardType.getField("descriptionCalls").getInt(nativeAfter)==0,"native preview not re-upgraded/reinitialized");
        Object branching=loader.loadClass("fixture.BranchCard").newInstance(),normalCopy=cardType.getMethod("makeStatEquivalentCopy").invoke(branching),branchCopy=cardType.getMethod("makeStatEquivalentCopy").invoke(branching);
        cardType.getField("baseDamage").setInt(normalCopy,16);cardType.getField("baseDamage").setInt(branchCopy,20);
        gridType.getField("hoveredCard").set(grid,branching);gridType.getField("upgradePreviewCard").set(grid,normalCopy);gridType.getField("branchUpgradePreviewCard_204").set(grid,branchCopy);
        ((List<Object>)group.getClass().getField("group").get(group)).add(branching);gridType.getField("waitingForBranchUpgradeSelection_561").set(grid,Boolean.TRUE);
        nativeResult=(Map<String,Object>)nativePreview.invoke(null,grid);
        check(((List<?>)nativeResult.get("alternatives")).size()==1&&Boolean.TRUE.equals(nativeResult.get("choice_required")),"displayed normal/branch choice exposed");
        gridType.getField("waitingForBranchUpgradeSelection_561").set(grid,Boolean.FALSE);gridType.getField("isBranchUpgrading_600").set(grid,Boolean.TRUE);
        nativeResult=(Map<String,Object>)nativePreview.invoke(null,grid);
        check(((Map<?,?>)nativeResult.get("after")).get("base_damage").equals(20),"chosen branch, not normal copy, is the confirmation result");
        gridType.getField("waitingForBranchUpgradeSelection_561").set(grid,Boolean.FALSE);
        Object treeSource=loader.loadClass("fixture.MultiCard").newInstance(),treeMain=cardType.getMethod("makeStatEquivalentCopy").invoke(treeSource),treeNext=cardType.getMethod("makeStatEquivalentCopy").invoke(treeSource);
        cardType.getField("baseDamage").setInt(treeNext,20);
        gridType.getField("hoveredCard").set(grid,treeSource);gridType.getField("waitingForUpgradeSelection_754").set(grid,Boolean.TRUE);
        ((List<Object>)group.getClass().getField("group").get(group)).add(treeSource);
        Class<?> tree=loader.loadClass("com.evacipated.cardcrawl.mod.stslib.ui.MultiUpgradeTree"),vertex=loader.loadClass("fixture.Vertex");
        tree.getField("mainCard").set(null,treeMain);List<Object> treeCards=(List<Object>)tree.getField("cardList").get(null);treeCards.add(treeMain);treeCards.add(treeNext);
        ((List<Object>)tree.getField("takenList").get(null)).add(treeMain);((List<Object>)tree.getField("lockedList").get(null)).add(treeNext);
        Object graph=loader.loadClass("fixture.Graph").newInstance(),v0=vertex.newInstance(),v1=vertex.newInstance();
        vertex.getField("card").set(v0,treeMain);vertex.getField("card").set(v1,treeNext);vertex.getField("index").setInt(v0,-1);vertex.getField("index").setInt(v1,4);
        ((List<Object>)vertex.getField("parents").get(v1)).add(v0);((List<Object>)graph.getClass().getField("vertices").get(graph)).addAll(Arrays.asList(v0,v1));tree.getField("cardGraph").set(null,graph);
        nativeResult=(Map<String,Object>)nativePreview.invoke(null,grid);
        check(nativeResult.get("scope").equals("native_multi_upgrade_tree"),"actual MultiUpgradeTree is the preview source");
        Map<?,?> treeNode=(Map<?,?>)((List<?>)nativeResult.get("nodes")).get(1);
        check(Boolean.FALSE.equals(treeNode.get("ui_selectable"))&&Boolean.TRUE.equals(treeNode.get("locked")),"locked alternatives are not offered as usable");
        check(treeNode.get("parents").equals(Arrays.asList(-1))&&treeNode.get("upgrade_index").equals(4),"visible dependency indices retained");
        check(((Map<?,?>)treeNode.get("card")).get("base_damage").equals(20),"actual displayed tree node snapshot");
        ((List<Object>)tree.getField("lockedList").get(null)).clear();nativeResult=(Map<String,Object>)nativePreview.invoke(null,grid);
        check(Boolean.TRUE.equals(((Map<?,?>)((List<?>)nativeResult.get("nodes")).get(1)).get("ui_selectable")),"tree locked state is read fresh");
        cardType.getField("misc").setInt(treeMain,123);nativeResult=(Map<String,Object>)nativePreview.invoke(null,grid);
        check(nativeResult.get("status").equals("unavailable"),"unrelated cached tree must not leak");
        Class<?> buttonType=loader.loadClass("com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton");Object button=buttonType.newInstance();buttonType.getField("cardToPreview").set(button,searing);
        check(observation.getMethod("eventCard",buttonType).invoke(null,button)==searing,"native event preview association");
        Object customButton=loader.loadClass("fixture.CustomEventButton").newInstance();buttonType.getField("cardToPreview").set(customButton,searing);
        check(observation.getMethod("eventCard",buttonType).invoke(null,customButton)==searing,"inherited mod event preview association");
        cardType.getField("isFlipped").setBoolean(searing,true);json=new HashMap<>();begin.invoke(null);offer.invoke(null,json,searing);finish.invoke(null,state);
        check(((Map<?,?>)json.get("upgrade_preview")).get("reason").equals("card_not_visible"),"hidden native cards not simulated");
        System.out.println("PASS: real adapter + installed Searing Blow/Strike/Hermit upgrade bodies; native grid/event previews; source/cache/visibility");
    }
    static void fixture(ClassPool p,Map<String,byte[]> bytes,String name,String[] fields,String[] methods)throws Exception {
        CtClass c=p.makeClass(name);for(String f:fields)c.addField(CtField.make(f,c));
        c.addConstructor(CtNewConstructor.defaultConstructor(c));for(String m:methods)c.addMethod(CtNewMethod.make(m,c));bytes.put(name,c.toBytecode());
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
