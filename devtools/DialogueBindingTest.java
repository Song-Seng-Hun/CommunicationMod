import javassist.*;
import java.lang.reflect.Method;

/** Transform actual installed method bodies without defining or initializing game classes. */
public final class DialogueBindingTest {
    public static void main(String[] args) throws Exception {
        ClassPool pool = new ClassPool(true);
        pool.appendClassPath(args[0]); pool.appendClassPath(args[1]); pool.appendClassPath(args[2]);
        for (CtMethod method : pool.get("communicationmod.observation.DialogueObservation").getDeclaredMethods()) {
            method.instrument(new javassist.expr.ExprEditor() {
                @Override public void edit(javassist.expr.MethodCall call) {
                    if (call.getMethodName().equals("getCurrRoom"))
                        throw new AssertionError("Frame observer calls null-unsafe getCurrRoom in menus");
                }
            });
        }
        patch(pool, "Frame", "com.megacrit.cardcrawl.core.CardCrawlGame", "render");
        patch(pool, "Reset", "com.megacrit.cardcrawl.core.CardCrawlGame", "startOver");
        patch(pool, "Talk", "com.megacrit.cardcrawl.actions.animations.TalkAction", "update");
        patch(pool, "Neow", "com.megacrit.cardcrawl.neow.NeowEvent", "talk");
        patch(pool, "Merchant", "com.megacrit.cardcrawl.shop.ShopScreen", "createSpeech");
        patch(pool, "SpeechRender", "com.megacrit.cardcrawl.vfx.SpeechTextEffect", "render");
        CtClass speech = pool.get("com.megacrit.cardcrawl.vfx.SpeechTextEffect");
        apply("SpeechCreated", speech.getDeclaredConstructors()[0]);
        for (String kind : new String[]{"Generic", "Room"}) {
            String owner = "com.megacrit.cardcrawl.events." + (kind.equals("Generic") ? "GenericEventDialog" : "RoomEventDialog");
            patch(pool, kind + "Render", owner, "render");
            CtMethod update = pool.get(owner).getDeclaredMethod("updateBodyText", new CtClass[]{pool.get("java.lang.String"),
                pool.get("com.megacrit.cardcrawl.ui.DialogWord$AppearEffect")});
            apply(kind + "Body", update);
        }
        for (String kind : new String[]{"Speech", "Dialog"}) {
            CtClass words = pool.get("com.megacrit.cardcrawl.ui." + kind + "Word");
            for (CtMethod method : words.getDeclaredMethods("render")) apply(kind + "Word", method);
        }
        for (CtMethod method : pool.get("communicationmod.GameStateConverter").getDeclaredMethods()) {
            method.instrument(new javassist.expr.ExprEditor() {
                @Override public void edit(javassist.expr.FieldAccess field) {
                    if (field.getClassName().endsWith("UpdateBodyTextPatch") && field.isReader())
                        throw new AssertionError("Converter still reads unrevealed body cache");
                }
            });
        }
        checkRenderOrdering();
        requireCall(pool.get("communicationmod.GameStateConverter").getDeclaredMethod("getEventState"), "eventReading");
        requireCall(pool.get("communicationmod.ChoiceScreenUtils").getDeclaredMethod("makeEventChoice"), "claimEventChoice");
        requireCall(pool.get("communicationmod.CommandExecutor").getDeclaredMethod("isCommandAvailable"), "allowsEventCommand");
        final boolean[] dungeonGuard = {false};
        pool.get("communicationmod.observation.DialogueObservation").getDeclaredMethod("inEventContext").instrument(new javassist.expr.ExprEditor() {
            @Override public void edit(javassist.expr.MethodCall call) {
                if (call.getClassName().equals("communicationmod.CommandExecutor") && call.getMethodName().equals("isInDungeon")) dungeonGuard[0]=true;
            }
        });
        if (!dungeonGuard[0]) throw new AssertionError("Stale event map node could lock main-menu commands");
        System.out.println("PASS: actual frame/origin/render/word/body methods accept dialogue hooks; no game execution");
    }
    private static void requireCall(CtBehavior method, String name) throws Exception {
        final boolean[] seen = {false};
        method.instrument(new javassist.expr.ExprEditor() {
            @Override public void edit(javassist.expr.MethodCall call) {
                if (call.getClassName().equals("communicationmod.observation.DialogueObservation") && call.getMethodName().equals(name)) seen[0]=true;
            }
        });
        if (!seen[0]) throw new AssertionError("Missing event reading binding: "+method.getLongName()+" -> "+name);
    }
    private static void patch(ClassPool pool, String hook, String owner, String method) throws Exception {
        apply(hook, pool.get(owner).getDeclaredMethod(method));
    }
    private static void apply(String name, CtBehavior method) throws Exception {
        Class<?> hook;
        try { hook = Class.forName("communicationmod.patches.DialogueRenderPatch$" + name); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Dialogue render hook missing: " + name, missing); }
        Method raw = hook.getMethod("Raw", CtBehavior.class);
        raw.invoke(null, method);
        if (name.equals("GenericRender") || name.equals("RoomRender")) requireCall(method,"finishEventRender");
        if (method.getMethodInfo().getCodeAttribute() == null) throw new AssertionError("Missing executable hook");
        final boolean[] contained = {false};
        method.instrument(new javassist.expr.ExprEditor() {
            @Override public void edit(javassist.expr.MethodCall call) {
                if (call.getClassName().equals("communicationmod.observation.DialogueObservation")
                    && call.getMethodName().equals("failed")) contained[0] = true;
            }
        });
        if (!contained[0]) throw new AssertionError("Observer failure is not contained: " + name);
    }

    /** Execute only newly generated JDK fixtures in a parentless loader, never game code. */
    private static void checkRenderOrdering() throws Exception {
        ClassPool pool = new ClassPool(true);
        CtClass observer=pool.makeClass("communicationmod.observation.DialogueObservation");
        observer.addField(CtField.make("public static String trace=\"\";",observer));
        observer.addField(CtField.make("public static boolean fail;",observer));
        observer.addMethod(CtNewMethod.make("public static void enterRender(Object x,String kind){trace+=\"enter,\";}",observer));
        observer.addMethod(CtNewMethod.make("public static void finishRender(){trace+=\"capture,\";if(fail)throw new IllegalStateException();}",observer));
        observer.addMethod(CtNewMethod.make("public static void leaveRender(){trace+=\"leave,\";}",observer));
        observer.addMethod(CtNewMethod.make("public static void failed(Throwable e){trace+=\"contained,\";}",observer));
        CtClass fixture=pool.makeClass("RenderFixture");
        fixture.addField(CtField.make("public boolean fail;",fixture));
        fixture.addMethod(CtNewMethod.make("public void render(){communicationmod.observation.DialogueObservation.trace+=\"draw,\";if(fail)throw new IllegalArgumentException();}",fixture));
        apply("SpeechRender",fixture.getDeclaredMethod("render"));
        final java.util.Map<String,byte[]> bytes=new java.util.HashMap<>();
        bytes.put(observer.getName(),observer.toBytecode()); bytes.put(fixture.getName(),fixture.toBytecode());
        ClassLoader loader=new ClassLoader(null) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] code=bytes.get(name);
                if(code==null)throw new ClassNotFoundException("Fixture refuses non-JDK class: "+name);
                return defineClass(name,code,0,code.length);
            }
        };
        Class<?> observed=loader.loadClass(observer.getName()), rendered=loader.loadClass(fixture.getName());
        Object object=rendered.getConstructor().newInstance();
        rendered.getMethod("render").invoke(object);
        expect(observed.getField("trace").get(null),"enter,draw,capture,leave,");
        observed.getField("trace").set(null,""); rendered.getField("fail").set(object,true);
        try { rendered.getMethod("render").invoke(object); throw new AssertionError("Original render exception swallowed"); }
        catch(java.lang.reflect.InvocationTargetException expected) {
            if(!(expected.getCause() instanceof IllegalArgumentException))throw expected;
        }
        expect(observed.getField("trace").get(null),"enter,draw,leave,");
        observed.getField("trace").set(null,""); rendered.getField("fail").set(object,false);
        observed.getField("fail").set(null,true);
        rendered.getMethod("render").invoke(object);
        expect(observed.getField("trace").get(null),"enter,draw,capture,contained,leave,");
        System.out.println("PASS: generated JDK fixture proves capture-after-render, finally cleanup, original exception and observer containment");
    }
    private static void expect(Object actual,String expected) {
        if(!expected.equals(actual))throw new AssertionError("expected "+expected+" but was "+actual);
    }
}
