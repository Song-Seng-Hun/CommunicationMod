import javassist.*;
import javassist.expr.*;
import java.util.*;

/** Actual installed-method patch insertion and observation/action bindings, not gameplay. */
public final class MapPlannerBindingTest {
    public static void main(String[] args)throws Exception {
        ClassPool pool=new ClassPool(true);for(String arg:args)pool.insertClassPath(arg);
        CtClass drawing;
        try{drawing=pool.get("communicationmod.map.MapDrawing");}
        catch(NotFoundException missing){throw new AssertionError("Missing human/MCP map planner UI",missing);}
        CtClass planner=pool.get("communicationmod.map.MapPlanner");
        check(calls(planner).contains("communicationmod.map.MapAnnotations.setRoute"),"MCP edits the semantic route model");
        check(!calls(drawing).contains("communicationmod.ChoiceScreenUtils.makeMapChoice")&&!calls(planner).contains("communicationmod.ChoiceScreenUtils.makeMapChoice"),"planning must never execute movement");
        check(calls(pool.get("communicationmod.observation.RunUi")).contains("communicationmod.map.MapDrawing.actions"),"route actions exposed by v2");
        check(calls(pool.get("communicationmod.GameStateConverter")).contains("communicationmod.map.MapDrawing.observation"),"human plan and position exposed to MCP");
        for(String[] binding:new String[][]{
            {"Update","com.megacrit.cardcrawl.screens.DungeonMapScreen","update"},
            {"Render","com.megacrit.cardcrawl.screens.DungeonMapScreen","render"},
            {"Node","com.megacrit.cardcrawl.map.MapRoomNode","update"},
            {"Boss","com.megacrit.cardcrawl.map.DungeonMap","update"},
            {"Mouse","com.megacrit.cardcrawl.screens.DungeonMapScreen","updateMouse"},
            {"Scroll","com.megacrit.cardcrawl.screens.DungeonMapScreen","updateYOffset"},
            {"Controller","com.megacrit.cardcrawl.screens.DungeonMapScreen","updateControllerInput"}}) {
            CtMethod method=pool.get(binding[1]).getDeclaredMethod(binding[2]);
            Class.forName("communicationmod.patches.MapDrawingPatch$"+binding[0]).getMethod("Raw",CtBehavior.class).invoke(null,method);
            check(calls(method).stream().anyMatch(c->c.startsWith("communicationmod.map.MapDrawing.")),"missing hook "+binding[0]);
        }
        // Scalar metadata only: geometry stays local, even in full observations.
        check(!calls(planner.getDeclaredMethod("observation")).contains("communicationmod.map.MapAnnotations.strokes"),"observation must not serialize ink");
        inputFixture();
        System.out.println("PASS: human/MCP route binding, no movement, scalar observation, actual update/render/input hook insertion");
    }
    private static void inputFixture()throws Exception {
        ClassPool pool=new ClassPool(true);Map<String,byte[]> bytes=new HashMap<>();
        CtClass input=pool.makeClass("com.megacrit.cardcrawl.helpers.input.InputHelper");
        for(String field:new String[]{"justClickedLeft","justReleasedClickLeft","isMouseDown","scrolledDown"})input.addField(CtField.make("public static boolean "+field+"=true;",input));
        bytes.put(input.getName(),input.toBytecode());
        CtClass drawing=pool.makeClass("communicationmod.map.MapDrawing");
        drawing.addField(CtField.make("public static boolean blocked;",drawing));drawing.addMethod(CtNewMethod.make("public static boolean blocksNavigation(){return blocked;}",drawing));
        bytes.put(drawing.getName(),drawing.toBytecode());
        CtClass fixture=pool.makeClass("InputGuardFixture");fixture.addConstructor(CtNewConstructor.defaultConstructor(fixture));
        fixture.addField(CtField.make("public int controllerCalls;",fixture));
        fixture.addMethod(CtNewMethod.make("public int mouse(){int result=0;if(com.megacrit.cardcrawl.helpers.input.InputHelper.justClickedLeft)result+=1;if(com.megacrit.cardcrawl.helpers.input.InputHelper.justReleasedClickLeft)result+=2;if(com.megacrit.cardcrawl.helpers.input.InputHelper.isMouseDown)result+=4;if(com.megacrit.cardcrawl.helpers.input.InputHelper.scrolledDown)result+=8;return result;}",fixture));
        fixture.addMethod(CtNewMethod.make("public void controller(){controllerCalls++;}",fixture));
        Class.forName("communicationmod.patches.MapDrawingPatch$Mouse").getMethod("Raw",CtBehavior.class).invoke(null,fixture.getDeclaredMethod("mouse"));
        Class.forName("communicationmod.patches.MapDrawingPatch$Controller").getMethod("Raw",CtBehavior.class).invoke(null,fixture.getDeclaredMethod("controller"));
        bytes.put(fixture.getName(),fixture.toBytecode());
        ClassLoader loader=new ClassLoader(null){protected Class<?> findClass(String name)throws ClassNotFoundException {byte[] b=bytes.get(name);if(b==null)throw new ClassNotFoundException(name);return defineClass(name,b,0,b.length);}};
        Class<?> test=loader.loadClass("InputGuardFixture"),owner=loader.loadClass("communicationmod.map.MapDrawing");Object instance=test.newInstance();
        check(test.getMethod("mouse").invoke(instance).equals(15),"normal mouse and wheel unchanged");
        owner.getField("blocked").setBoolean(null,true);
        check(test.getMethod("mouse").invoke(instance).equals(8),"editor consumes mouse but preserves wheel");
        test.getMethod("controller").invoke(instance);check(test.getField("controllerCalls").getInt(instance)==0,"controller cannot travel while drawing");
        owner.getField("blocked").setBoolean(null,false);test.getMethod("controller").invoke(instance);
        check(test.getField("controllerCalls").getInt(instance)==1,"controller restored when editor closes");
        check(loader.loadClass("com.megacrit.cardcrawl.helpers.input.InputHelper").getField("isMouseDown").getBoolean(null),"guard never rewrites global input");
    }
    private static Set<String> calls(CtClass type)throws Exception {Set<String> out=new HashSet<>();for(CtBehavior method:type.getDeclaredBehaviors())out.addAll(calls(method));return out;}
    private static Set<String> calls(CtBehavior method)throws Exception {Set<String> out=new HashSet<>();method.instrument(new ExprEditor(){public void edit(MethodCall call){out.add(call.getClassName()+"."+call.getMethodName());}});return out;}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
