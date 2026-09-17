package communicationmod.observation;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import java.lang.reflect.*;
import java.util.*;

/** A synchronous, opt-in click delivered only inside an audited native input handler. */
public final class NativeUiInput {
    private static Hitbox target;
    private static final Map<Hitbox,boolean[]> saved=new IdentityHashMap<>();
    private NativeUiInput() { }
    public static void click(Hitbox box,Runnable handler) {
        run(box,handler,false);
    }
    public static void press(Hitbox box,Runnable handler) {
        run(box,handler,true);
    }
    private static void run(Hitbox box,Runnable handler,boolean press) {
        if(!Boolean.getBoolean("communicationmod.play_control") || Settings.isTouchScreen || Settings.isControllerMode
            || target!=null || box==null || box.clicked || box.clickStarted)throw new IllegalStateException("Native input unavailable or pending");
        boolean left=InputHelper.justClickedLeft,right=InputHelper.justClickedRight,released=InputHelper.justReleasedClickLeft;
        if(left || right || released)throw new IllegalStateException("Human input pending");
        target=box;
        try {
            InputHelper.justClickedLeft=press;InputHelper.justClickedRight=false;InputHelper.justReleasedClickLeft=false;
            afterHitbox(box);handler.run();
        } finally {
            target=null;
            for(Map.Entry<Hitbox,boolean[]> entry:saved.entrySet()) {
                Hitbox h=entry.getKey();boolean[] s=entry.getValue();
                h.hovered=s[0];h.clicked=s[1];h.clickStarted=s[2];h.justHovered=s[3];
            }
            saved.clear();InputHelper.justClickedLeft=left;InputHelper.justClickedRight=right;InputHelper.justReleasedClickLeft=released;
        }
    }
    /** Copied-runtime Hitbox.update postfix. Inert during human play and ordinary observation. */
    public static void afterHitbox(Hitbox box) {
        if(target==null)return;
        if(!saved.containsKey(box))saved.put(box,new boolean[]{box.hovered,box.clicked,box.clickStarted,box.justHovered});
        box.hovered=box==target;box.clicked=box==target;box.clickStarted=false;box.justHovered=false;
    }
    public static Object field(Object owner,String name) {
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try {
            Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);
        }catch(NoSuchFieldException missing){}catch(IllegalAccessException failure){throw new IllegalStateException("Unreadable native UI field: "+name,failure);}
        throw new IllegalStateException("Unsupported native UI field: "+name);
    }
    public static void invoke(Object owner,String name) {
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try {
            Method method=type.getDeclaredMethod(name);method.setAccessible(true);method.invoke(owner);return;
        }catch(NoSuchMethodException missing){}catch(InvocationTargetException failure){
            Throwable cause=failure.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;
            if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException("Native UI handler failed: "+name,cause);
        }catch(IllegalAccessException failure){throw new IllegalStateException("Native UI handler inaccessible: "+name,failure);}
        throw new IllegalStateException("Unsupported native UI handler: "+name);
    }
    public static boolean visible(Object button) {
        return !(Boolean)field(button,"isHidden") && Math.abs((Float)field(button,"current_x")-(Float)field(button,"target_x"))<0.5f;
    }
}
