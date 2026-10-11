import com.google.gson.*;
import javassist.*;
import javassist.bytecode.*;
import javassist.expr.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/** Native inventory and copied claim bodies. Test dependencies only; no real game classes initialized. */
final class NativeRewardCases {
    static final String D=DownfallUiTest.D,R=DownfallUiTest.R,C=DownfallUiTest.C,O=DownfallUiTest.O;
    final JsonArray rows;
    NativeRewardCases(DownfallUiTest t,String jar,String manifest)throws Exception {
        JsonObject document=new JsonParser().parse(new String(Files.readAllBytes(Paths.get(manifest)),UTF_8)).getAsJsonObject();
        rows=document.getAsJsonArray("rewards");
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(Paths.get(jar))){byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))>=0;)sha.update(buffer,0,n);}
        byte[] digest=sha.digest();
        StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format("%02x",b&255));
        t.expect(hex.toString().equals(document.get("jar_sha256").getAsString()),"native reward jar drift: review before repinning");
        Set<String> actual=new TreeSet<>(),expected=new TreeSet<>();
        try(ZipFile zip=new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {ZipEntry entry=entries.nextElement();if(!entry.getName().endsWith(".class"))continue;
                ClassFile file;try(DataInputStream in=new DataInputStream(zip.getInputStream(entry))){file=new ClassFile(in);}
                if((file.getAccessFlags()&(AccessFlag.ABSTRACT|AccessFlag.INTERFACE))!=0)continue;
                String parent=file.getSuperclass();
                while(parent!=null && !parent.equals("java.lang.Object")) {
                    if(parent.equals(R)){actual.add(file.getName());break;}
                    parent=t.nativePool.get(parent).getClassFile2().getSuperclass();
                }
            }
        }
        for(JsonElement e:rows)t.expect(expected.add(e.getAsJsonObject().get("class").getAsString()),"duplicate native reward class");
        t.expect(actual.equals(expected),"unclassified native rewards: actual="+actual+" manifest="+expected);
        Set<String> missing=new TreeSet<>(expected);missing.remove(missing.iterator().next());
        t.expect(!actual.equals(missing),"inventory detects erased fixture");
        missing=new TreeSet<>(actual);missing.add("fixture.NewNativeReward");
        t.expect(!missing.equals(expected),"inventory detects new native provider");
    }
    void prepare(DownfallUiTest t)throws Exception {
        String group="com.megacrit.cardcrawl.cards.CardGroup",player="com.megacrit.cardcrawl.characters.AbstractPlayer";
        t.make(group+"$CardGroupType",null,"public static "+group+"$CardGroupType UNSPECIFIED=new "+group+"$CardGroupType();");
        CtClass g=t.make(group,null,"public java.util.ArrayList group=new java.util.ArrayList();","public void addToTop("+C+" c){group.add(c);}",
            "public "+group+" getPurgeableCards(){return this;}","public "+group+" getUpgradableCards(){return this;}");
        g.addConstructor(CtNewConstructor.make("public CardGroup("+group+"$CardGroupType type){}",g));
        CtClass playerClass=t.p.get(player);
        playerClass.addField(CtField.make("public int gold;",playerClass));
        playerClass.addField(CtField.make("public "+group+" masterDeck=new "+group+"();",playerClass));
        playerClass.addMethod(CtNewMethod.make("public void gainGold(int amount){gold+=amount;}",playerClass));
        t.p.get(R).addField(CtField.make("public int goldAmt=7;",t.p.get(R)));
        t.make("downfall.downfallMod",null,"public static boolean choosingRemoveCard;","public static boolean choosingTransformCard;","public static boolean choosingUpgradeCard;","public static boolean choosingBossRelic;");
        CtClass choice=t.make("downfall.cards.OctoChoiceCard",C);choice.addConstructor(CtNewConstructor.make("public OctoChoiceCard(String id,String name,String image,String text){this.cardID=id;this.name=name;}",choice));
        t.make("expansioncontent.expansionContentMod",null,"public static String makeCardPath(String s){return s;}");
        t.make("expansioncontent.patches.CenterGridCardSelectScreen",null,"public static boolean centerGridSelect;");
        t.make("com.megacrit.cardcrawl.random.Random",null,"public int calls;","public int random(int max){calls++;return 0;}");
        t.p.get(D).addField(CtField.make("public static com.megacrit.cardcrawl.random.Random cardRandomRng=new com.megacrit.cardcrawl.random.Random();",t.p.get(D)));
        t.p.get(D).addField(CtField.make("public static java.util.ArrayList bossRelicPool=new java.util.ArrayList();",t.p.get(D)));
        String room="com.megacrit.cardcrawl.rooms.AbstractRoom",screen="com.megacrit.cardcrawl.screens.select.GridCardSelectScreen";
        t.make(room+"$RoomPhase",null,"public static "+room+"$RoomPhase INCOMPLETE=new "+room+"$RoomPhase();");
        t.p.get(room).addField(CtField.make("public "+room+"$RoomPhase phase;",t.p.get(room)));
        t.make("com.megacrit.cardcrawl.ui.buttons.DynamicBanner",null,"public void hide(){}");
        t.make("com.megacrit.cardcrawl.ui.buttons.CancelButton",null,"public void hide(){}");
        CtClass overlay=t.p.get("com.megacrit.cardcrawl.core.OverlayMenu");overlay.addField(CtField.make("public com.megacrit.cardcrawl.ui.buttons.CancelButton cancelButton=new com.megacrit.cardcrawl.ui.buttons.CancelButton();",overlay));
        t.p.get(D).addField(CtField.make("public static com.megacrit.cardcrawl.ui.buttons.DynamicBanner dynamicBanner=new com.megacrit.cardcrawl.ui.buttons.DynamicBanner();",t.p.get(D)));
        t.p.get(D+"$CurrentScreen").addField(CtField.make("public static "+D+"$CurrentScreen GRID=new "+D+"$CurrentScreen();",t.p.get(D+"$CurrentScreen")));
        t.make(screen,null,"public "+group+" opened;","public int count;","public String operation;",
            "public void open("+group+" cards,int n,String prompt,boolean upgrade,boolean transform,boolean any,boolean remove){opened=cards;count=n;operation=upgrade?\"upgrade\":transform?\"transform\":remove?\"remove\":\"none\";"+D+".screen="+D+"$CurrentScreen.GRID;}");
        t.p.get(D).addField(CtField.make("public static "+screen+" gridSelectScreen=new "+screen+"();",t.p.get(D)));
        for(JsonElement e:rows) {
            JsonObject row=e.getAsJsonObject();String name=row.get("class").getAsString();
            // Four prior adapters already have native fixtures in DownfallUiTest.
            if(name.startsWith("collector.") || name.equals("downfall.util.RareCardReward") || name.equals("theHexaghost.util.SealSealReward"))continue;
            CtClass fixture=t.make(name,name.equals("downfall.util.BossRelicReward")?R:"basemod.abstracts.CustomReward","public static String[] TEXT=new String[]{\"Reward\",\"Choose\",\"Choose one\",\"Relic\",\"Seal\"};");
            fixture.addMethod(CtNewMethod.copy(t.nativePool.get(name).getDeclaredMethod("claimReward"),fixture,null));
            t.make("fixture.Derived"+fixture.getSimpleName(),name);
            final List<String> types=new ArrayList<>();
            for(CtConstructor ctor:t.nativePool.get(name).getDeclaredConstructors())ctor.instrument(new ExprEditor(){public void edit(FieldAccess f){if(f.isReader() && f.getSignature().equals("Lcom/megacrit/cardcrawl/rewards/RewardItem$RewardType;"))types.add(f.getFieldName());}});
            if(name.equals("downfall.util.BossRelicReward")) {
                t.expect(t.nativePool.get(name).getSuperclass().getName().equals(R),"boss relic inherits base reward");
                t.nativePool.get(R).getConstructor("()V").instrument(new ExprEditor(){public void edit(FieldAccess f){if(f.isReader() && f.getSignature().equals("Lcom/megacrit/cardcrawl/rewards/RewardItem$RewardType;"))types.add(f.getFieldName());}});
            }
            t.expect(types.contains(row.get("type").getAsString()),name+" exact native reward type");
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"}) void verify(DownfallUiTest t)throws Exception {
        Object player=t.get(D,"player"),deck=t.get(player,"masterDeck");((List)t.get(deck,"group")).add(t.newObject(C));
        ((List)t.get(D,"bossRelicPool")).addAll(Arrays.asList("RelicA","RelicB","RelicC","RelicD"));
        for(JsonElement e:rows) {
            JsonObject row=e.getAsJsonObject();String name=row.get("class").getAsString(),kind=row.get("kind").getAsString(),type=row.get("type").getAsString();
            Object reward=t.newObject(name);t.type(reward,type);
            t.expect((Boolean)t.call(O+"CollectorRewardUi","supports",reward),"native reward unsupported: "+name);
            t.type(reward,"INVALID");t.expect(!(Boolean)t.call(O+"CollectorRewardUi","supports",reward),name+" wrong enum refused");t.type(reward,type);
            if(name.startsWith("collector.") || name.equals("downfall.util.RareCardReward") || name.equals("theHexaghost.util.SealSealReward"))continue;
            Object derived=t.newObject("fixture.Derived"+name.substring(name.lastIndexOf('.')+1));t.type(derived,type);
            t.expect(!(Boolean)t.call(O+"CollectorRewardUi","supports",derived),name+" unknown subclass refused");
            JsonObject detail=(JsonObject)t.call(O+"CollectorRewardUi","details",reward);
            Object hidden=t.newObject(C);t.set(hidden,"name","SECRET_UNOPENED");((List)t.get(reward,"cards")).add(hidden);
            t.expect(detail.equals(t.call(O+"CollectorRewardUi","details",reward)),name+" hidden candidates never affect observation");
            if(kind.equals("gold"))t.expect(detail.get("amount").getAsInt()==7,"bounty reads goldAmt, not unused amount");
            else if(kind.equals("card"))t.expect(detail.entrySet().size()==1 && detail.get("choice_details_available_after_claim").getAsBoolean(),name+" unopened choices deferred");
            else t.expect(kind.equals(detail.get("selection_operation").getAsString()) && detail.get("selection_count").getAsInt()==1,name+" deck operation explicit");
            for(String flag:new String[]{"isDone","ignoreReward"}){t.set(reward,flag,true);t.expect(!(Boolean)t.call(O+"CollectorRewardUi","claimable",reward),name+" pending/ignored blocked");t.set(reward,flag,false);}
            Object hb=t.get(reward,"hb");t.set(hb,"clicked",true);t.expect(!(Boolean)t.call(O+"CollectorRewardUi","claimable",reward),name+" physical input blocked");t.set(hb,"clicked",false);
            t.set(D,"screen",t.get(D+"$CurrentScreen","COMBAT_REWARD"));t.set(D,"isScreenUp",false);
            int gold=(Integer)t.get(player,"gold");t.call(O+"CollectorRewardUi","click",reward);
            t.expect((Boolean)t.get(reward,"isDone"),name+(kind.equals("boss_relic")?" base input fixture receives click; claim body is native":" installed CustomReward update receives click"));
            t.expect(!(Boolean)t.call(O+"CollectorRewardUi","claimable",reward),name+" cannot replay pending claim");
            t.expect(t.get(D,"screen")==t.get(D+"$CurrentScreen","COMBAT_REWARD") && gold==(Integer)t.get(player,"gold"),name+" click does not synthesize claim");
            int rolls=(Integer)t.get(t.get(D,"cardRandomRng"),"calls");
            t.expect(rolls==0 || !kind.equals("boss_relic"),"boss relic observation/click must not roll candidates");
            boolean completed=(Boolean)t.invoke(reward,"claimReward");
            if(kind.equals("card")) {
                Object screen=t.get(D,"cardRewardScreen");t.expect(!completed && t.get(D,"screen")==t.get(D+"$CurrentScreen","CARD_REWARD"),name+" native card selection pending");
                t.expect(t.get(screen,"opened")==t.get(reward,"cards") && t.get(screen,"owner")==reward,name+" native candidate and owner identity");
                t.expect(t.get(D,"previousScreen")==t.get(D+"$CurrentScreen","COMBAT_REWARD"),name+" native return route");
            } else if(kind.equals("gold"))t.expect(completed && (Integer)t.get(player,"gold")==gold+7,"native bounty pays goldAmt exactly once");
            else if(kind.equals("boss_relic")) {
                Object screen=t.get(D,"gridSelectScreen");t.expect(completed && t.get(D,"screen")==t.get(D+"$CurrentScreen","GRID"),"boss relic native grid");
                t.expect(((List)t.get(t.get(screen,"opened"),"group")).size()==3 && (Integer)t.get(screen,"count")==1,"boss relic native three candidates / one selection");
                t.expect((Integer)t.get(t.get(D,"cardRandomRng"),"calls")==rolls+3,"only native claim rolls candidates");
            } else {Object screen=t.get(D,"gridSelectScreen");t.expect(completed && t.get(D,"screen")==t.get(D+"$CurrentScreen","GRID"),name+" native grid open");
                t.expect(kind.equals(t.get(screen,"operation")) && (Integer)t.get(screen,"count")==1 && t.get(screen,"opened")==deck,name+" native grid semantics");}
        }
    }
}
