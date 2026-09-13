import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/** JDK-only map planning fixtures, never creates game objects or edits saves. */
public final class MapAnnotationsTest {
    public static void main(String[] args)throws Exception {
        Class<?> type;
        try {type=Class.forName("communicationmod.map.MapAnnotations");}
        catch(ClassNotFoundException missing){throw new AssertionError("Missing map ink and semantic route model",missing);}
        Object model=type.newInstance();
        type.getMethod("reset",String.class).invoke(model,"map-a");
        Method revision=type.getMethod("revision"),route=type.getMethod("route");
        Method set=type.getMethod("setRoute",List.class,String.class,long.class,Predicate.class,BiPredicate.class);
        Set<String> nodes=new HashSet<>(Arrays.asList("1,14","2,13","2,12","boss"));
        Predicate<String> known=nodes::contains;
        BiPredicate<String,String> edge=(a,b)->(a.equals("1,14")&&b.equals("2,13"))||(a.equals("2,13")&&b.equals("2,12"))||(a.equals("2,12")&&b.equals("boss"));
        long r=(Long)revision.invoke(model);
        set.invoke(model,Arrays.asList("1,14","2,13","2,12","boss"),"mcp",r,known,edge);
        check(route.invoke(model).equals(Arrays.asList("1,14","2,13","2,12","boss")),"reverse route order retained");
        long changed=(Long)revision.invoke(model);check(changed>r,"revision advances on edit");
        reject(()->set.invoke(model,Arrays.asList("1,14","boss"),"mcp",changed,known,edge));
        reject(()->set.invoke(model,Arrays.asList("missing"),"mcp",changed,known,edge));
        reject(()->set.invoke(model,Arrays.asList("1,14","1,14"),"mcp",changed,known,edge));
        reject(()->set.invoke(model,Arrays.asList("2,13"),"mcp",r,known,edge));
        check(revision.invoke(model).equals(changed),"invalid edits atomic");
        set.invoke(model,route.invoke(model),"mcp",changed,known,edge);
        check(revision.invoke(model).equals(changed),"no-op route edit keeps revision");
        check(type.getMethod("next",String.class).invoke(model,"2,13").equals("2,12"),"current position gives next planned node");
        check(type.getMethod("next",String.class).invoke(model,"boss")==null,"route completed");
        check(type.getMethod("next",String.class).invoke(model,"off-route")==null,"do not invent next step off route");
        Method undo=type.getMethod("undo");undo.invoke(model);check(((List<?>)route.invoke(model)).isEmpty(),"undo route");
        Method stroke=type.getMethod("commitStroke",List.class);
        stroke.invoke(model,Arrays.asList(new float[]{1,2},new float[]{101,2}));
        check(((List<?>)type.getMethod("strokes").invoke(model)).size()==1,"ink stroke retained");
        Method erase=type.getMethod("erase",float.class,float.class,float.class);
        check(erase.invoke(model,50f,3f,3f).equals(true),"erase hits segment middle, not just sampled endpoints");
        check(((List<?>)type.getMethod("strokes").invoke(model)).isEmpty(),"stroke erased");
        undo.invoke(model);check(((List<?>)type.getMethod("strokes").invoke(model)).size()==1,"erase can be undone");
        type.getMethod("reset",String.class).invoke(model,"map-a");check(((List<?>)type.getMethod("strokes").invoke(model)).size()==1,"same map survives reopen");
        type.getMethod("reset",String.class).invoke(model,"map-b");check(((List<?>)type.getMethod("strokes").invoke(model)).isEmpty(),"new act/map clears ink");
        Method to=type.getMethod("toMap",float.class,float.class,float.class,float.class,float.class);
        Method from=type.getMethod("toScreen",float.class,float.class,float.class,float.class,float.class);
        float[] point=(float[])to.invoke(null,800f,420f,1920f,1.5f,-300f);
        float[] screen=(float[])from.invoke(null,point[0],point[1],1280f,1f,-100f);
        check(Math.abs(screen[0]-(640f-160f/1.5f))<0.001f && Math.abs(screen[1]-380f)<0.001f,"ink follows scroll and scale");
        reject(()->stroke.invoke(model,Arrays.asList(new float[]{Float.NaN,1})));
        List<float[]> huge=new ArrayList<>();for(int i=0;i<20000;i++)huge.add(new float[]{i,1});
        reject(()->stroke.invoke(model,huge));
        System.out.println("PASS: route revisions/atomicity/next/undo, reverse graph, ink/erase/bounds and scroll/scale transforms");
    }
    interface Throwing {void run()throws Exception;}
    private static void reject(Throwing run)throws Exception {try{run.run();throw new AssertionError("Expected rejection");}catch(InvocationTargetException e){if(!(e.getCause() instanceof IllegalArgumentException))throw e;}}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
