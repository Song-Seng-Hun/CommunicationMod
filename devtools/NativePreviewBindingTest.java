import javassist.bytecode.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Class-file inspection only. Never defines, initializes, instantiates, or invokes game classes. */
public final class NativePreviewBindingTest implements Opcode {
    static final String O="communicationmod.observation.", CARD="com.megacrit.cardcrawl.cards.AbstractCard";
    static final String U="sneckomod.cards.unknowns.", COLLECTION="collector.CollectorCollection";
    static final String MOD="collector.cardmods.CollectedCardMod";
    static final String SHA="148c422dd26e869fbdc9a63f9ab072373c3d791d3e3fcbe8d87b55ae75a29226";
    static final String[] UNKNOWNS={"Unknown","Unknown0Cost","Unknown1Cost","Unknown2Cost","Unknown3Cost","UnknownBlock","UnknownBoss","UnknownClass","UnknownColorless","UnknownCommonAttack","UnknownCommonSkill","UnknownDexterity","UnknownDraw","UnknownExhaust","UnknownRareAttack","UnknownRarePower","UnknownRareSkill","UnknownStrength","UnknownStrike","UnknownUncommonAttack","UnknownUncommonPower","UnknownUncommonSkill","UnknownVulnerable","UnknownWeak","UnknownX"};
    static final String[][] BOSSES={{"Ironclad","Immolate","Reaper","Barricade"},{"Silent","Corpse Explosion","After Image","Storm of Steel"},{"Defect","Buffer","Core Surge","Seek"},{"Watcher","ConjureBlade","Ragnarok","Omniscience"},{"Hermit","hermit:Adapt","hermit:Magnum","hermit:FromBeyond"}};
    final Path compiled;
    final ZipFile evil,game;
    int assertions;
    NativePreviewBindingTest(String[] args)throws Exception {compiled=Paths.get(args[0]);evil=new ZipFile(args[1]);game=new ZipFile(args[2]);}
    public static void main(String[] args)throws Exception {
        NativePreviewBindingTest t=new NativePreviewBindingTest(args);
        try {
            try(InputStream in=Files.newInputStream(Paths.get(args[1]))){t.check(SHA.equals(hash(in)),"installed Downfall SHA drift; re-audit native bindings before updating pin");}
            System.out.println("PIN: EvilWithin-modded.jar SHA256="+SHA);
            t.unknowns();t.split();t.collector();t.eventRelics();t.production();t.negativeControls();
            System.out.println("PASS: native preview binding ("+t.assertions+" assertions); 25 exact read-only myList getters, private Split cache, 15 boss mappings, modifier semantics, production callbacks; no game class loading");
        } finally {t.evil.close();t.game.close();}
    }
    void unknowns()throws Exception {
        List<String> actual=strings(method(prod("CardPreviewObservation"),"<clinit>"));
        actual.removeIf(s->!s.startsWith("Unknown"));
        check(actual.equals(Arrays.asList(UNKNOWNS)),"production exact 25-class Unknown allowlist");
        for(String name:UNKNOWNS){
            ClassFile c=nativeClass(U+name);check(c.getSuperclass().equals(U+"AbstractUnknownCard"),name+" native superclass");
            MethodInfo m=method(c,"myList");check(m.getDescriptor().equals("()Ljava/util/ArrayList;") && (m.getAccessFlags()&(AccessFlag.PUBLIC|AccessFlag.STATIC|AccessFlag.ABSTRACT))==AccessFlag.PUBLIC,name+" public instance myList signature");
            readOnlyGetter(m);
            if(!name.equals("UnknownClass")) {
                String field=name.equals("UnknownX")?"unknownXCostReplacements":Character.toLowerCase(name.charAt(0))+name.substring(1)+"Replacements";
                check(tokens(m).equals(Arrays.asList("getstatic "+U+"AbstractUnknownCard."+field+":Ljava/util/ArrayList;","areturn")),name+" returns exact public catalog only");
                field(U+"AbstractUnknownCard",field,"Ljava/util/ArrayList;",AccessFlag.PUBLIC|AccessFlag.STATIC);
            } else {
                field("sneckomod.SneckoMod","unknownClasses","Ljava/util/ArrayList;",AccessFlag.PUBLIC|AccessFlag.STATIC);
                field(U+"AbstractUnknownCard","unknownClassReplacements","Ljava/util/ArrayList;",AccessFlag.PUBLIC|AccessFlag.STATIC);
                field(U+name,"myColor","Lcom/megacrit/cardcrawl/cards/AbstractCard$CardColor;",AccessFlag.PUBLIC);
                check(calls(m).equals(Arrays.asList("java.util.ArrayList.size()I","java.util.ArrayList.get(I)Ljava/lang/Object;","java.util.ArrayList.get(I)Ljava/lang/Object;")),"UnknownClass only size/get, no RNG, factories or writes");
            }
        }
        for(String name:new String[]{"isSeen","isLocked","isFlipped","upgraded"})field(CARD,name,"Z",AccessFlag.PUBLIC);
        field(CARD,"cardsToPreview","Lcom/megacrit/cardcrawl/cards/AbstractCard;",AccessFlag.PUBLIC);
        field("com.megacrit.cardcrawl.helpers.CardLibrary","cards","Ljava/util/HashMap;",AccessFlag.PUBLIC|AccessFlag.STATIC);
        System.out.println("PASS: all 25 installed myList bodies/signatures are audited read-only catalogs");
    }
    void readOnlyGetter(MethodInfo m)throws Exception {
        Set<Integer> allowed=new HashSet<>(Arrays.asList(GETSTATIC,GETFIELD,ARETURN,ICONST_0,ISTORE_1,ILOAD_1,INVOKEVIRTUAL,IF_ICMPGE,CHECKCAST,ALOAD_0,IF_ACMPNE,IINC,GOTO,ACONST_NULL));
        CodeAttribute code=m.getCodeAttribute();check(code!=null && code.getExceptionTable().size()==0,"myList has inspectable body without handlers");
        for(int at:positions(m))check(allowed.contains(code.iterator().byteAt(at)),"myList forbidden opcode: "+Mnemonic.OPCODE[code.iterator().byteAt(at)]);
        for(String call:calls(m))check(call.equals("java.util.ArrayList.size()I")||call.equals("java.util.ArrayList.get(I)Ljava/lang/Object;"),"myList forbidden call: "+call);
        for(String token:tokens(m))if(token.startsWith("get"))check(token.startsWith("getstatic "+U+"AbstractUnknownCard.unknown")||token.startsWith("getstatic sneckomod.SneckoMod.unknownClasses:")||token.startsWith("getfield "+U+"UnknownClass.myColor:"),"myList forbidden read: "+token);
    }
    void split()throws Exception {
        field("slimebound.cards.SplitSpecialist","cardList","Ljava/util/ArrayList;",AccessFlag.PRIVATE);
        MethodInfo choice=method(nativeClass("slimebound.cards.SplitSpecialist"),"choiceList");
        check(choice.getDescriptor().equals("()Ljava/util/ArrayList;"),"Split choiceList signature");
        check(calls(choice).contains("java.util.Collections.shuffle(Ljava/util/List;Ljava/util/Random;)V"),"forbidden Split getter really shuffles");
        check(tokens(choice).toString().contains("AbstractDungeon.cardRng:"),"forbidden Split getter reads game RNG");
        MethodInfo projection=method(prod("CardPreviewObservation"),"addTo");
        check(strings(projection).contains("cardList") && calls(projection).contains("java.lang.Class.getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;") && calls(projection).contains("java.lang.reflect.Field.setAccessible(Z)V"),"Split projection binds private cache, not getter");
    }
    void collector()throws Exception {
        field(COLLECTION,"collection","Lcom/megacrit/cardcrawl/cards/CardGroup;",AccessFlag.PUBLIC|AccessFlag.STATIC);
        field(COLLECTION,"collectionPool","Ljava/util/HashMap;",AccessFlag.PUBLIC|AccessFlag.STATIC);
        field("com.megacrit.cardcrawl.cards.CardGroup","group","Ljava/util/ArrayList;",AccessFlag.PUBLIC);
        field("collector.CollectorChar$Enums","THE_COLLECTOR","Lcom/megacrit/cardcrawl/characters/AbstractPlayer$PlayerClass;",AccessFlag.PUBLIC|AccessFlag.STATIC);
        field(MOD,"uiStrings","Lcom/megacrit/cardcrawl/localization/UIStrings;",AccessFlag.PUBLIC|AccessFlag.STATIC|AccessFlag.FINAL);
        field("com.megacrit.cardcrawl.localization.UIStrings","TEXT","[Ljava/lang/String;",AccessFlag.PUBLIC);
        field("charbosses.bosses.AbstractBossDeckArchetype","actNum","I",AccessFlag.PUBLIC);
        MethodInfo factory=method(nativeClass(COLLECTION),"getCollectedCard");
        check(factory.getDescriptor().equals("(Lcom/megacrit/cardcrawl/monsters/AbstractMonster;)Lcom/megacrit/cardcrawl/cards/AbstractCard;") && (factory.getAccessFlags()&(AccessFlag.PUBLIC|AccessFlag.STATIC))==(AccessFlag.PUBLIC|AccessFlag.STATIC),"native collectible factory signature");
        List<String> expected=new ArrayList<>();int row=0;CodeIterator it=factory.getCodeAttribute().iterator();ConstPool cp=factory.getConstPool();
        for(int at:positions(factory))if(it.byteAt(at)==TABLESWITCH){
            check(row<BOSSES.length,"only five boss switches");String[] boss=BOSSES[row++];expected.addAll(Arrays.asList(boss));
            String type="charbosses.bosses."+boss[0]+".CharBoss"+boss[0];
            field(type,"chosenArchetype","Lcharbosses/bosses/AbstractBossDeckArchetype;",AccessFlag.PUBLIC);
            int base=(at+4)&~3;check(it.s32bitAt(base+4)==1 && it.s32bitAt(base+8)==3,"native boss acts 1..3");
            check(it.s32bitAt(base)==it.s32bitAt(base+20),"native default boss act uses third card");
            for(int act=1;act<=3;act++)check(boss[act].equals(nativeCardId(factory,at+it.s32bitAt(base+8+4*act))),"native "+boss[0]+" act "+act+" maps to "+boss[act]);
        }
        check(row==5,"five native boss mappings");
        check(strings(method(prod("CollectorPreviewObservation"),"<clinit>")).equals(expected),"production catalog exactly mirrors native boss order and all 15 cards");
        List<String> factoryTokens=tokens(factory);
        check(factoryTokens.contains("new collector.cards.collectibles.DefaultCollectibleCard") && factoryTokens.contains("new "+MOD),"native default and CollectedCardMod application");
        check(calls(factory).get(calls(factory).size()-1).equals("basemod.helpers.CardModifierManager.addModifier(Lcom/megacrit/cardcrawl/cards/AbstractCard;Lbasemod/abstracts/AbstractCardModifier;)V"),"native final operation applies collected modifier");
        check(strings(method(prod("CollectorPreviewObservation"),"bossCard")).contains("collector:DefaultCollectibleCard"),"production fallback matches pinned native default");
        modifier(nativeClass(MOD),nativeClass("collector.cardmods.ActuallyCollectedCardMod"));
        System.out.println("PASS: pinned native Collector acts/default, localization prefix, Exhaust and descriptor chain");
    }
    String nativeCardId(MethodInfo method,int at)throws Exception {
        CodeIterator it=method.getCodeAttribute().iterator();ConstPool cp=method.getConstPool();int op=it.byteAt(at),index=op==LDC?it.byteAt(at+1):it.u16bitAt(at+1);
        if(op==LDC||op==LDC_W)return cp.getStringInfo(index);
        check(op==GETSTATIC && cp.getFieldrefName(index).equals("ID"),"native boss ID is literal or static ID");
        String owner=cp.getFieldrefClassName(index);check(owner.startsWith("hermit.cards."),"only pinned Hermit ID initializer needed");
        List<String> init=tokens(method(nativeClass(owner),"<clinit>"));
        check(init.get(0).equals("ldc_class "+owner) && init.get(1).equals("invokevirtual java.lang.Class.getSimpleName()Ljava/lang/String;") && init.get(2).equals("invokestatic hermit.HermitMod.makeID(Ljava/lang/String;)Ljava/lang/String;"),"Hermit ID initializer is class simple name through pinned makeID");
        return "hermit:"+owner.substring(owner.lastIndexOf('.')+1);
    }
    void modifier(ClassFile mod,ClassFile actual)throws Exception {
        List<String> initial=tokens(method(mod,"onInitialApplication"));
        check(initial.equals(Arrays.asList("aload_0","aload_1","getfield "+CARD+".exhaust:Z","putfield "+MOD+".hadExhaust:Z","aload_1","iconst_1","putfield "+CARD+".exhaust:Z","aload_1","new collector.cardmods.ActuallyCollectedCardMod","dup","invokespecial collector.cardmods.ActuallyCollectedCardMod.<init>()V","invokestatic basemod.helpers.CardModifierManager.addModifier(Lcom/megacrit/cardcrawl/cards/AbstractCard;Lbasemod/abstracts/AbstractCardModifier;)V","return")),"native modifier forces Exhaust and adds descriptor modifier only");
        List<String> description=tokens(method(mod,"modifyDescription"));
        check(description.equals(Arrays.asList("new java.lang.StringBuilder","dup","invokespecial java.lang.StringBuilder.<init>()V","getstatic "+MOD+".uiStrings:Lcom/megacrit/cardcrawl/localization/UIStrings;","getfield com.megacrit.cardcrawl.localization.UIStrings.TEXT:[Ljava/lang/String;","iconst_0","aaload","invokevirtual java.lang.StringBuilder.append(Ljava/lang/String;)Ljava/lang/StringBuilder;","aload_1","invokevirtual java.lang.StringBuilder.append(Ljava/lang/String;)Ljava/lang/StringBuilder;","invokevirtual java.lang.StringBuilder.toString()Ljava/lang/String;","areturn")),"native prefix is TEXT[0] plus original description");
        List<String> descriptor=tokens(method(actual,"extraDescriptors"));
        check(descriptor.equals(Arrays.asList("iconst_1","anewarray java.lang.String","dup","iconst_0","getstatic "+MOD+".uiStrings:Lcom/megacrit/cardcrawl/localization/UIStrings;","getfield com.megacrit.cardcrawl.localization.UIStrings.TEXT:[Ljava/lang/String;","iconst_1","aaload","aastore","invokestatic java.util.Arrays.asList([Ljava/lang/Object;)Ljava/util/List;","areturn")),"native descriptor is TEXT[1] only");
    }
    void eventRelics()throws Exception {
        String button="com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton",guardian="guardian.ui.RelicPreviewButton",relic="com.megacrit.cardcrawl.relics.AbstractRelic";
        field(button,"relicToPreview","Lcom/megacrit/cardcrawl/relics/AbstractRelic;",AccessFlag.PRIVATE);
        field(guardian,"r","Lcom/megacrit/cardcrawl/relics/AbstractRelic;",AccessFlag.PRIVATE);
        for(String type:new String[]{button,guardian}) {
            List<String> render=tokens(method(nativeClass(type),"renderRelicPreview"));
            check(render.contains("getfield "+type+"."+(type.equals(button)?"relicToPreview":"r")+":Lcom/megacrit/cardcrawl/relics/AbstractRelic;"),"native preview uses exact cached relic field");
            check(render.contains("getfield "+relic+".tips:Ljava/util/ArrayList;"),"native event hover displays existing relic tips");
        }
        check(calls(method(compiledClass("communicationmod.GameStateConverter"),"getEventState")).contains(O+"EventPreviewObservation.addRelicTo(Ljava/util/Map;Lcom/megacrit/cardcrawl/ui/buttons/LargeDialogOptionButton;)V"),"active event converter projects relic previews");
        safeProjection(prod("EventPreviewObservation"));
        System.out.println("PASS: native base/Guardian event relic fields, cached tooltip rendering and converter wiring");
    }
    void production()throws Exception {
        for(String name:new String[]{"CardPreviewObservation","CollectorPreviewObservation"})safeProjection(prod(name));
        callback(prod("CardObservation"),"addTo",O+"CardPreviewObservation.addTo");
        callback(compiledClass("communicationmod.GameStateConverter"),"convertMonsterToJson",O+"CollectorPreviewObservation.addTo");
        check(calls(method(prod("CardPreviewObservation"),"addTo")).contains(O+"CardUpgradeObservation.offerGallery(Ljava/util/Map;Ljava/util/List;)V"),"upgraded gallery delegates to existing bounded preview queue");
        check(calls(method(prod("CollectorRewardUi"),"details")).contains(O+"CardUpgradeObservation.snapshotWithUpgrade(Lcom/megacrit/cardcrawl/cards/AbstractCard;)Ljava/util/Map;"),"custom reward obtains current-context upgrade comparison");
        MethodInfo hover=method(nativeClass("collector.patches.CollectiblesPatches.MonsterHoverShowCardPatches$AddTip"),"Insert");
        check(calls(hover).contains(COLLECTION+".getCollectedCard(Lcom/megacrit/cardcrawl/monsters/AbstractMonster;)Lcom/megacrit/cardcrawl/cards/AbstractCard;"),"installed hover path owns native collectible preview");
        System.out.println("PASS: active CardObservation and monster converter callbacks bind existing snapshot helpers; no native factory/RNG calls");
    }
    void safeProjection(ClassFile c)throws Exception {
        for(Object value:c.getMethods()){MethodInfo m=(MethodInfo)value;if(m.getCodeAttribute()==null)continue;String previous="";for(String token:tokens(m)){
            check(!token.contains("lastUnknownRoll")&&!token.contains("choiceList")&&!token.contains("getCollectedCard"),"forbidden future/RNG/factory binding: "+token);
            check(!token.toLowerCase(Locale.ROOT).contains("rng")&&!token.contains(".random.")&&!token.contains("java.util.Random"),"forbidden native RNG read: "+token);
            if(token.startsWith("invokestatic "+O+"NativeMechanicAccess.call("))check(previous.equals("ldc myList"),"only audited myList reflection invocation");
            check(!token.startsWith("invokevirtual java.lang.reflect.Method.invoke(")&&!token.startsWith("invokevirtual java.lang.reflect.Field.set("),"projection must not invoke or mutate through arbitrary reflection");
            if(token.startsWith("invoke") || token.startsWith("putfield") || token.startsWith("putstatic"))check(!token.matches(".* (com\\.megacrit\\.|sneckomod\\.|slimebound\\.|collector\\.).*"),"projection must not invoke native methods or mutate native fields: "+token);
            previous=token;
        }}
    }
    void callback(ClassFile c,String methodName,String target)throws Exception {
        MethodInfo m=method(c,methodName);List<Integer> pos=positions(m);CodeIterator it=m.getCodeAttribute().iterator();ConstPool cp=c.getConstPool();boolean found=false;
        for(int n=1;n<pos.size();n++)if(token(m,pos.get(n)).startsWith("invokestatic "+target+"(")){
            found=true;int previous=pos.get(n-1);check(it.byteAt(previous)==INVOKEDYNAMIC,"preview receives a snapshot callback");
            int bootstrap=cp.getInvokeDynamicBootstrap(it.u16bitAt(previous+1));BootstrapMethodsAttribute attr=(BootstrapMethodsAttribute)c.getAttribute(BootstrapMethodsAttribute.tag);
            int handle=attr.getMethods()[bootstrap].arguments[1];int ref=cp.getMethodHandleIndex(handle);
            check(cp.getMethodrefClassName(ref).equals(O+"CardUpgradeObservation"),"callback owner is snapshot helper");
            String name=cp.getMethodrefName(ref);check(name.equals("snapshot"),"callback binds snapshot directly, without a wrapper or native copy/upgrade");
            check(cp.getMethodrefType(ref).equals("(Lcom/megacrit/cardcrawl/cards/AbstractCard;)Ljava/util/Map;"),"snapshot callback descriptor");
            MethodInfo bound=method(prod("CardUpgradeObservation"),name);
            if(!c.getName().startsWith(O))check((bound.getAccessFlags()&AccessFlag.PUBLIC)!=0,"cross-package callback public access");
        }
        check(found,"active production wiring: "+c.getName()+"."+methodName+" -> "+target);
    }
    void negativeControls()throws Exception {
        MethodInfo getter=method(nativeClass(U+"Unknown"),"myList");getter.getCodeAttribute().iterator().writeByte(PUTSTATIC,0);
        rejects(()->readOnlyGetter(getter),"myList write mutation");
        ClassFile projection=prod("CardPreviewObservation");MethodInfo m=method(projection,"addTo");
        Bytecode forbidden=new Bytecode(m.getConstPool());forbidden.addLdc("choiceList");forbidden.addOpcode(POP);m.getCodeAttribute().iterator().insert(forbidden.get());
        rejects(()->safeProjection(projection),"Split forbidden getter reference");
        ClassFile rngProjection=prod("CardPreviewObservation");MethodInfo rngMethod=method(rngProjection,"addTo");
        Bytecode rngRead=new Bytecode(rngMethod.getConstPool());rngRead.addGetstatic("com.megacrit.cardcrawl.dungeons.AbstractDungeon","cardRng","Lcom/megacrit/cardcrawl/random/Random;");rngRead.addOpcode(POP);rngMethod.getCodeAttribute().iterator().insert(rngRead.get());
        rejects(()->safeProjection(rngProjection),"direct native RNG-field read");
        ClassFile mod=nativeClass(MOD);method(mod,"onInitialApplication").getCodeAttribute().iterator().writeByte(ICONST_0,9);
        rejects(()->modifier(mod,nativeClass("collector.cardmods.ActuallyCollectedCardMod")),"native Exhaust semantics drift");
        ClassFile consumer=prod("CardObservation");removeCall(consumer,"addTo",O+"CardPreviewObservation.addTo");
        rejects(()->callback(consumer,"addTo",O+"CardPreviewObservation.addTo"),"removed production preview wiring");
        ClassFile converter=compiledClass("communicationmod.GameStateConverter");removeCall(converter,"convertMonsterToJson",O+"CollectorPreviewObservation.addTo");
        rejects(()->callback(converter,"convertMonsterToJson",O+"CollectorPreviewObservation.addTo"),"removed production Collector wiring");
    }
    void removeCall(ClassFile owner,String name,String target)throws Exception {
        MethodInfo m=method(owner,name);CodeIterator body=m.getCodeAttribute().iterator();
        for(int at:positions(m))if(token(m,at).startsWith("invokestatic "+target+"(")){body.writeByte(NOP,at);body.writeByte(NOP,at+1);body.writeByte(NOP,at+2);return;}
        throw new AssertionError("negative control target missing: "+target);
    }
    interface Checked {void run()throws Exception;}
    void rejects(Checked action,String label)throws Exception {try{action.run();}catch(AssertionError expected){System.out.println("PASS negative control: "+label+" -> "+expected.getMessage());return;}throw new AssertionError("guard missed "+label);}
    ClassFile prod(String name)throws Exception{return compiledClass(O+name);}
    ClassFile compiledClass(String name)throws Exception {Path file=compiled.resolve(name.replace('.','/')+".class");check(Files.isRegularFile(file),"missing fresh production binding "+name);try(InputStream in=Files.newInputStream(file)){return read(in);}}
    ClassFile nativeClass(String name)throws Exception {String entry=name.replace('.','/')+".class";ZipFile source=evil.getEntry(entry)!=null?evil:game;ZipEntry found=source.getEntry(entry);check(found!=null,"installed class missing: "+name);try(InputStream in=source.getInputStream(found)){return read(in);}}
    static ClassFile read(InputStream in)throws Exception{return new ClassFile(new DataInputStream(in));}
    MethodInfo method(ClassFile c,String name){for(Object value:c.getMethods()){MethodInfo m=(MethodInfo)value;if(m.getName().equals(name))return m;}throw new AssertionError("missing method "+c.getName()+"."+name);}
    void field(String type,String name,String descriptor,int flags)throws Exception {
        ClassFile c=nativeClass(type);for(Object value:c.getFields()){FieldInfo f=(FieldInfo)value;if(f.getName().equals(name)){
            int mask=AccessFlag.PUBLIC|AccessFlag.PRIVATE|AccessFlag.PROTECTED|AccessFlag.STATIC;
            check(f.getDescriptor().equals(descriptor) && (f.getAccessFlags()&mask)==(flags&mask) && (f.getAccessFlags()&flags)==flags,"native field signature/access "+type+"."+name);return;
        }}
        if(c.getSuperclass()!=null&&!c.getSuperclass().equals("java.lang.Object")){field(c.getSuperclass(),name,descriptor,flags);return;}
        throw new AssertionError("native field missing "+type+"."+name);
    }
    static List<Integer> positions(MethodInfo m)throws Exception {List<Integer> out=new ArrayList<>();CodeIterator it=m.getCodeAttribute().iterator();while(it.hasNext())out.add(it.next());return out;}
    static List<String> tokens(MethodInfo m)throws Exception {List<String> out=new ArrayList<>();for(int at:positions(m))out.add(token(m,at));return out;}
    static String token(MethodInfo m,int at){CodeIterator it=m.getCodeAttribute().iterator();ConstPool cp=m.getConstPool();int op=it.byteAt(at);String key=Mnemonic.OPCODE[op];
        if(op==LDC||op==LDC_W){int ref=op==LDC?it.byteAt(at+1):it.u16bitAt(at+1);return cp.getTag(ref)==ConstPool.CONST_String?"ldc "+cp.getStringInfo(ref):cp.getTag(ref)==ConstPool.CONST_Class?"ldc_class "+cp.getClassInfo(ref):key;}
        if(op>=GETSTATIC&&op<=PUTFIELD){int ref=it.u16bitAt(at+1);return key+" "+cp.getFieldrefClassName(ref)+"."+cp.getFieldrefName(ref)+":"+cp.getFieldrefType(ref);}
        if(op>=INVOKEVIRTUAL&&op<=INVOKEINTERFACE){int ref=it.u16bitAt(at+1);return key+" "+(op==INVOKEINTERFACE?cp.getInterfaceMethodrefClassName(ref)+"."+cp.getInterfaceMethodrefName(ref)+cp.getInterfaceMethodrefType(ref):cp.getMethodrefClassName(ref)+"."+cp.getMethodrefName(ref)+cp.getMethodrefType(ref));}
        if(op==NEW||op==ANEWARRAY||op==CHECKCAST||op==INSTANCEOF)return key+" "+cp.getClassInfo(it.u16bitAt(at+1));
        return key;
    }
    static List<String> strings(MethodInfo m)throws Exception {List<String> out=new ArrayList<>();for(String token:tokens(m))if(token.startsWith("ldc "))out.add(token.substring(4));return out;}
    static List<String> calls(MethodInfo m)throws Exception {List<String> out=new ArrayList<>();for(String token:tokens(m))if(token.startsWith("invoke")&&!token.equals("invokedynamic"))out.add(token.substring(token.indexOf(' ')+1));return out;}
    static String hash(InputStream in)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;)digest.update(buffer,0,n);StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format("%02x",b&255));return out.toString();}
    void check(boolean yes,String message){assertions++;if(!yes)throw new AssertionError(message);}
}
