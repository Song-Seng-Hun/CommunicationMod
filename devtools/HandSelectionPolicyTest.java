import java.lang.reflect.Method;

public final class HandSelectionPolicyTest {
    public static void main(String[] args) throws Exception {
        Class<?> policy;
        try { policy=Class.forName("communicationmod.observation.HandSelectionPolicy"); }
        catch(ClassNotFoundException e) { throw new AssertionError("Missing hand selection constraints",e); }
        Method select=policy.getMethod("canSelect",int.class,int.class);
        Method confirm=policy.getMethod("canConfirm",int.class,int.class,boolean.class,boolean.class);
        check((Boolean)select.invoke(null,2,0),"first selection");
        check(!(Boolean)select.invoke(null,2,2),"selection cap");
        check((Boolean)select.invoke(null,1,1),"native single-card replacement");
        check(!(Boolean)select.invoke(null,0,0),"invalid cap");
        check(!(Boolean)confirm.invoke(null,2,1,false,false),"exact count");
        check((Boolean)confirm.invoke(null,2,2,false,false),"exact complete");
        check((Boolean)confirm.invoke(null,2,1,true,false),"up to");
        check(!(Boolean)confirm.invoke(null,2,0,true,false),"zero disallowed");
        check((Boolean)confirm.invoke(null,2,0,true,true),"zero allowed");
        check(!(Boolean)confirm.invoke(null,2,3,true,true),"over cap");
        System.out.println("PASS: hand selection cap, replacement, exact/up-to/zero constraints");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
