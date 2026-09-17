package communicationmod.observation;

import java.util.Map;

/** Explicit, optional installed-build bindings; callers supply only audited names. */
final class NativeMechanicAccess {
    private NativeMechanicAccess() { }
    static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name,false,NativeMechanicAccess.class.getClassLoader());
    }
    static Object field(Object owner,String name) throws ReflectiveOperationException {
        return (owner instanceof Class<?>?(Class<?>)owner:owner.getClass()).getField(name).get(owner instanceof Class<?>?null:owner);
    }
    static Object stat(String type,String name) throws ReflectiveOperationException {return field(type(type),name);}
    static Object spire(String type,String name,Object target) throws ReflectiveOperationException {
        Object field=stat(type,name);return field.getClass().getMethod("get",Object.class).invoke(field,target);
    }
    static Object call(Object owner,String name) throws ReflectiveOperationException {
        return (owner instanceof Class<?>?(Class<?>)owner:owner.getClass()).getMethod(name).invoke(owner instanceof Class<?>?null:owner);
    }
    static int number(Object value) {return ((Number)value).intValue();}
    static boolean is(Object value,String base) {
        for(Class<?> c=value==null?null:value.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals(base))return true;
        return false;
    }
    interface Read {void run() throws ReflectiveOperationException;}
    static void panel(Map<String,Object> out,String name,Read read) {
        try {read.run();} catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            out.put(name+"_unavailable_reason","native_binding_unavailable");
        }
    }
}
