import javassist.*;
import javassist.expr.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.Pattern;

/** Read-only installed-bytecode inventory; never loads or initializes a game class. */
public final class AuditMechanicReferences {
    public static void main(String[] args)throws Exception {
        Pattern query=Pattern.compile(args[0]);ClassPool pool=new ClassPool(true);
        for(int i=1;i<args.length;i++)pool.insertClassPath(args[i]);
        try(JarFile jar=new JarFile(args[1])) {
            Enumeration<JarEntry> entries=jar.entries();
            while(entries.hasMoreElements()) {
                String path=entries.nextElement().getName();
                if(!path.endsWith(".class") || path.startsWith("charbosses/") || path.startsWith("reskinContent/"))continue;
                CtClass type=pool.get(path.substring(0,path.length()-6).replace('/','.'));
                if(type.getName().startsWith("com.") || type.getName().startsWith("org."))continue;
                for(CtBehavior method:type.getDeclaredBehaviors()) {
                    boolean selected=query.matcher(method.getLongName()).find();
                    Set<String> refs=new TreeSet<>();
                    method.instrument(new ExprEditor(){
                        public void edit(FieldAccess f){String key=f.getClassName()+"."+f.getFieldName();if(selected || query.matcher(key).find())refs.add((f.isWriter()?"write ":"read ")+key);}
                        public void edit(MethodCall m){String key=m.getClassName()+"."+m.getMethodName();if(selected || query.matcher(key).find())refs.add("call "+key);}
                    });
                    if(!refs.isEmpty())System.out.println(method.getLongName()+" => "+refs);
                }
                for(CtMethod method:type.getDeclaredMethods())if(method.getName().equals("getCost")||method.getName().equals("renderEnergy"))System.out.println("COST OVERRIDE "+method.getLongName());
                for(CtClass iface:type.getInterfaces())if(iface.getName().contains("AlternateCardCost"))System.out.println("ALTERNATE COST "+type.getName()+" implements "+iface.getName());
                type.detach();
            }
        }
    }
}
