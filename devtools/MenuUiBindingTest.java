import javassist.*;
import javassist.expr.*;
import java.util.*;

/** Actual installed signatures and dispatch bytecode, without initializing the game. */
public final class MenuUiBindingTest {
    public static void main(String[] args)throws Exception {
        ClassPool pool=new ClassPool(true);for(String path:args)pool.insertClassPath(path);
        CtClass ui;
        try{ui=pool.get("communicationmod.observation.MenuUi");}
        catch(NotFoundException missing){throw new AssertionError("Missing allowlisted live menu binding",missing);}
        String pkg="com.megacrit.cardcrawl.screens.";
        for(String[] pair:new String[][]{{"mainMenu.MenuButton","label"},{"mainMenu.MenuButton","hidden"},{"mainMenu.MenuButton","x"},{"mainMenu.MenuButton","targetX"},
            {"mainMenu.MainMenuPanelButton","header"},{"mainMenu.MainMenuPanelButton","description"},{"mainMenu.MainMenuPanelButton","result"},{"mainMenu.MainMenuPanelButton","animTimer"}})
            pool.get(pkg+pair[0]).getDeclaredField(pair[1]);
        pool.get(pkg+"mainMenu.MainMenuPanelButton").getDeclaredMethod("buttonEffect");
        pool.get(pkg+"charSelect.CharacterOption").getDeclaredMethod("updateHitbox");
        CtClass nativeOption=pool.get(pkg+"charSelect.CharacterOption");
        for(String field:new String[]{"flavorText","hp"})check(nativeOption.getDeclaredField(field).getType().getName().equals("java.lang.String"),"native text field: "+field);
        check(nativeOption.getDeclaredField("gold").getType()==CtClass.intType,"native starting gold type");
        check(nativeOption.getDeclaredField("infoX").getType()==CtClass.floatType,"native introduction visibility type");
        characterText(pool);
        String sourceCalls=calls(ui).toString();
        check(sourceCalls.contains("MenuButton.buttonEffect"),"use real Play handler");
        check(sourceCalls.contains("MainMenuScreen.hideMenuButtons"),"preserve post-click main button hiding");
        check(sourceCalls.contains("MenuPanelScreen.hide"),"panel hide before its effect");
        check(!sourceCalls.contains("CommandExecutor") && !sourceCalls.contains("getDeclaredFields"),"no legacy executor or broad field dump");
        check(calls(pool.get(pkg+"mainMenu.MainMenuPanelButton")).toString().contains("RedirectPlayNormal.Prefix"),"installed Downfall mode redirect preserved");
        check(calls(pool.get(pkg+"charSelect.CharacterOption")).toString().contains("CharacterSelectScreen.justSelected"),"installed character UI selection route exists");
        check(calls(pool.get("communicationmod.observation.LocalObserver")).toString().contains("MenuControlSession.receive"),"opt-in real menu receive wired on game thread");
        System.out.println("PASS: installed menu labels/animation signatures, native Play/Downfall redirect/character-selection handlers; no game execution");
    }
    /** Public intro coverage without initializing the game; native field signatures are checked above. */
    private static void characterText(ClassPool nativePool)throws Exception {
        ClassPool fixtures=new ClassPool(true);
        Map<String,byte[]> definitions=new HashMap<>();
        CtClass option=fixtures.makeClass("com.megacrit.cardcrawl.screens.charSelect.CharacterOption");
        for(String field:new String[]{"public boolean selected=true;","public boolean locked;","public String name=\"Guardian\";","private String flavorText=\"#gFold NL defend.\";","private String hp=\"70/70\";","private int gold=99;","private float infoX;"})option.addField(CtField.make(field,option));
        option.addConstructor(CtNewConstructor.defaultConstructor(option));definitions.put(option.getName(),option.toBytecode());
        CtClass settings=fixtures.makeClass("com.megacrit.cardcrawl.core.Settings");settings.addField(CtField.make("public static float scale=1f;",settings));definitions.put(settings.getName(),settings.toBytecode());
        CtClass converter=fixtures.makeClass("communicationmod.GameStateConverter");
        converter.addMethod(CtNewMethod.copy(nativePool.get(converter.getName()).getDeclaredMethod("removeTextFormatting"),converter,null));definitions.put(converter.getName(),converter.toBytecode());
        CtClass production=nativePool.get("communicationmod.observation.MenuUi");definitions.put(production.getName(),production.toBytecode());production.defrost();
        ClassLoader loader=new ClassLoader(MenuUiBindingTest.class.getClassLoader()){
            protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                synchronized(getClassLoadingLock(name)){Class<?> type=findLoadedClass(name);if(type==null){byte[] bytes=definitions.get(name);type=bytes==null?super.loadClass(name,false):defineClass(name,bytes,0,bytes.length);}if(resolve)resolveClass(type);return type;}
            }
        };
        Class<?> optionType=loader.loadClass(option.getName()),ui=loader.loadClass(production.getName());
        Object selected=optionType.getConstructor().newInstance();
        java.lang.reflect.Method capture=ui.getDeclaredMethod("selectedCharacter",optionType);capture.setAccessible(true);
        com.google.gson.JsonObject info=(com.google.gson.JsonObject)capture.invoke(null,selected);
        check(info.get("description").getAsString().equals("Fold  defend."),"selected localized description is formatted, not discarded");
        check(info.get("name").getAsString().equals("Guardian")&&info.get("hp").getAsString().equals("70/70")&&info.get("gold").getAsInt()==99,"current native public starting info");
        optionType.getField("locked").setBoolean(selected,true);check(capture.invoke(null,selected)==null,"locked intro withheld");
        optionType.getField("locked").setBoolean(selected,false);optionType.getField("selected").setBoolean(selected,false);check(capture.invoke(null,selected)==null,"unselected intro withheld");
        optionType.getField("selected").setBoolean(selected,true);java.lang.reflect.Field x=optionType.getDeclaredField("infoX");x.setAccessible(true);x.setFloat(selected,-601f);check(capture.invoke(null,selected)==null,"offscreen intro withheld");
        x.setFloat(selected,-600f);check(capture.invoke(null,selected)!=null,"native visibility boundary preserved");
        check(calls(production).contains("communicationmod.observation.MenuUi.selectedCharacter"),"capture invokes introduction collector");
    }

    private static List<String> calls(CtClass type)throws Exception {
        List<String> out=new ArrayList<>();for(CtBehavior b:type.getDeclaredBehaviors())b.instrument(new ExprEditor(){public void edit(MethodCall c){out.add(c.getClassName()+"."+c.getMethodName());}});return out;
    }
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
}
