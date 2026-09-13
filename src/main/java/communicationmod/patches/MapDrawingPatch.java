package communicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.map.*;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;
import javassist.*;
import javassist.expr.*;

/** The same raw hooks are used by normal MTS and by the isolated packaged runtime. */
public final class MapDrawingPatch {
    @SpirePatch(clz=DungeonMapScreen.class,method="update")
    public static class Update {@SpireRawPatch public static void Raw(CtBehavior method)throws CannotCompileException {
        method.insertBefore("{communicationmod.map.MapDrawing.beforeUpdate(this);}");
    }}
    @SpirePatch(clz=DungeonMapScreen.class,method="render")
    public static class Render {@SpireRawPatch public static void Raw(CtBehavior method)throws CannotCompileException {
        method.insertAfter("{communicationmod.map.MapDrawing.render($1);}");
    }}
    @SpirePatch(clz=MapRoomNode.class,method="update")
    public static class Node {@SpireRawPatch public static void Raw(CtBehavior method)throws CannotCompileException {guard(method);note(method,"noteNode");}}
    @SpirePatch(clz=DungeonMap.class,method="update")
    public static class Boss {@SpireRawPatch public static void Raw(CtBehavior method)throws CannotCompileException {guard(method);note(method,"noteBoss");}}
    @SpirePatch(clz=DungeonMapScreen.class,method="updateMouse")
    public static class Mouse {@SpireRawPatch public static void Raw(CtBehavior method)throws CannotCompileException {guard(method);}}
    @SpirePatch(clz=DungeonMapScreen.class,method="updateYOffset")
    public static class Scroll {@SpireRawPatch public static void Raw(CtBehavior method)throws CannotCompileException {guard(method);}}
    @SpirePatch(clz=DungeonMapScreen.class,method="updateControllerInput")
    public static class Controller {@SpireRawPatch public static void Raw(CtBehavior method)throws CannotCompileException {
        method.insertBefore("{if(communicationmod.map.MapDrawing.blocksNavigation())return;}");
    }}
    @SpirePatch(clz=CardCrawlGame.class,method="startOver",paramtypez={})
    public static class Reset {@SpireRawPatch public static void Raw(CtBehavior method)throws CannotCompileException {
        method.insertBefore("{communicationmod.map.MapPlanner.reset();}");
    }}
    private static void note(CtBehavior method,String callback)throws CannotCompileException {
        final int[] count={0};method.instrument(new ExprEditor(){public void edit(MethodCall call)throws CannotCompileException {
            if(call.getClassName().equals("com.megacrit.cardcrawl.helpers.Hitbox")&&call.getMethodName().equals("update")) {
                count[0]++;call.replace("{$proceed($$);communicationmod.map.MapDrawing."+callback+"(this);}");
            }
        }});
        if(count[0]!=1)throw new CannotCompileException("Expected one map hitbox refresh for "+callback);
    }
    private static void guard(CtBehavior method)throws CannotCompileException {
        method.instrument(new ExprEditor(){
            public void edit(FieldAccess field)throws CannotCompileException {
                if(!field.isReader())return;
                String owner=field.getClassName(),name=field.getFieldName();
                boolean mouse=owner.equals("com.megacrit.cardcrawl.helpers.input.InputHelper")
                    &&(name.equals("isMouseDown")||name.equals("justClickedLeft")||name.equals("justReleasedClickLeft"));
                boolean node=owner.equals("com.megacrit.cardcrawl.screens.DungeonMapScreen")&&name.equals("clicked");
                boolean hover=owner.equals("com.megacrit.cardcrawl.helpers.Hitbox")&&name.equals("hovered");
                if(mouse||node||hover)field.replace("{$_=communicationmod.map.MapDrawing.blocksNavigation()?false:$proceed($$);}");
            }
            public void edit(MethodCall call)throws CannotCompileException {
                if(call.getClassName().equals("com.megacrit.cardcrawl.helpers.controller.CInputAction")&&call.getMethodName().equals("isJustPressed"))
                    call.replace("{$_=communicationmod.map.MapDrawing.blocksNavigation()?false:$proceed($$);}");
            }
        });
    }
}
