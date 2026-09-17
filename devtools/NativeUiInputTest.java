import javassist.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Execute the real production input scope with field-only graphics/input fixtures. */
public final class NativeUiInputTest {
    public static void main(String[] args)throws Exception {
        ClassPool p=new ClassPool(true);Map<String,byte[]> bytes=new HashMap<>();
        fixture(p,bytes,"com.megacrit.cardcrawl.helpers.Hitbox",new String[]{"public boolean hovered;","public boolean clicked;","public boolean clickStarted;","public boolean justHovered;"});
        fixture(p,bytes,"com.megacrit.cardcrawl.helpers.input.InputHelper",new String[]{"public static boolean justClickedLeft;","public static boolean justClickedRight;","public static boolean justReleasedClickLeft;"});
        fixture(p,bytes,"com.megacrit.cardcrawl.core.Settings",new String[]{"public static boolean isTouchScreen;","public static boolean isControllerMode;"});
        String name="communicationmod.observation.NativeUiInput";
        Path file=Paths.get(args[0],name.replace('.','/')+".class");
        if(!Files.exists(file))throw new AssertionError("Missing native UI input scope");
        bytes.put(name,Files.readAllBytes(file));
        ClassLoader loader=new ClassLoader(NativeUiInputTest.class.getClassLoader()) {
            protected Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException {
                synchronized(getClassLoadingLock(n)){Class<?> c=findLoadedClass(n);byte[] b=bytes.get(n);
                    if(c==null&&b!=null)c=defineClass(n,b,0,b.length);
                    if(c==null)c=super.loadClass(n,resolve);if(resolve)resolveClass(c);return c;}
            }
        };
        Class<?> input=loader.loadClass(name),hitbox=loader.loadClass("com.megacrit.cardcrawl.helpers.Hitbox"),flags=loader.loadClass("com.megacrit.cardcrawl.helpers.input.InputHelper");
        Method click=input.getMethod("click",hitbox,Runnable.class),hook=input.getMethod("afterHitbox",hitbox);
        Object target=hitbox.newInstance(),other=hitbox.newInstance();
        hitbox.getField("hovered").setBoolean(other,true);
        System.setProperty("communicationmod.play_control","true");
        int[] called={0};
        click.invoke(null,target,(Runnable)()->{try{
            hook.invoke(null,target);hook.invoke(null,other);
            check(hitbox.getField("clicked").getBoolean(target),"target click delivered");
            check(!hitbox.getField("hovered").getBoolean(other),"other UI input suppressed");
            check(!flags.getField("justClickedRight").getBoolean(null),"no popup input");
            called[0]++;
        }catch(Exception e){throw new RuntimeException(e);}});
        check(called[0]==1,"one native handler call");
        check(!hitbox.getField("clicked").getBoolean(target),"no deferred click");
        check(hitbox.getField("hovered").getBoolean(other),"other hover restored");
        check(!flags.getField("justClickedLeft").getBoolean(null),"mouse restored");
        Method press;
        try{press=input.getMethod("press",hitbox,Runnable.class);}catch(NoSuchMethodException missing){throw new AssertionError("Missing native press handler for merchant and upgrade choices",missing);}
        press.invoke(null,target,(Runnable)()->{try{check(flags.getField("justClickedLeft").getBoolean(null),"press-only handler sees mouse press");}catch(Exception e){throw new RuntimeException(e);}});
        check(!flags.getField("justClickedLeft").getBoolean(null),"press restored");
        try{click.invoke(null,target,(Runnable)()->{throw new IllegalArgumentException("handler failure");});throw new AssertionError("failure swallowed");}
        catch(InvocationTargetException expected){check(expected.getCause() instanceof IllegalArgumentException,"original failure preserved");}
        hook.invoke(null,other);check(!hitbox.getField("clicked").getBoolean(other),"hook idle after failure");
        hitbox.getField("clicked").setBoolean(target,true);
        expectRejected(click,target,()->called[0]++);
        check(called[0]==1,"pending human click not executed");hitbox.getField("clicked").setBoolean(target,false);
        System.clearProperty("communicationmod.play_control");expectRejected(click,target,()->called[0]++);
        System.out.println("PASS: native input scope, one handler, inactive/pending guards, cleanup on success/failure");
    }
    static void expectRejected(Method method,Object target,Runnable action)throws Exception{try{method.invoke(null,target,action);throw new AssertionError("unsafe click accepted");}catch(InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException,"input rejected");}}
    static void fixture(ClassPool p,Map<String,byte[]> bytes,String name,String[] fields)throws Exception{CtClass c=p.makeClass(name);for(String f:fields)c.addField(CtField.make(f,c));c.addConstructor(CtNewConstructor.defaultConstructor(c));bytes.put(name,c.toBytecode());}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
