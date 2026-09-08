import java.lang.reflect.*;
import java.util.*;

public final class RenderedWordsTest {
    public static void main(String[] args) throws Exception {
        Class<?> type;
        try { type=Class.forName("communicationmod.observation.RenderedWords"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Rendered word visibility projection missing", missing); }
        Object words=type.getConstructor().newInstance();
        Method append=type.getMethod("append",Object.class,String.class,int.class,float.class,float.class,boolean.class);
        Object id=new Object();
        for (float alpha : new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})
            append.invoke(words,new Object(),"invisible",0,alpha,1f,false);
        append.invoke(words,new Object(),"zero scale",0,1f,0f,false);
        equal(type.getMethod("text").invoke(words),"");
        append.invoke(words,id,"안녕",0,1f,1f,false);
        append.invoke(words,id,"안녕",0,1f,1f,false);
        append.invoke(words,new Object(),"여행자",0,1f,1f,false);
        append.invoke(words,new Object(),"다음 줄",1,1f,1f,false);
        equal(type.getMethod("text").invoke(words),"안녕 여행자\n다음 줄");
        Method complete=type.getMethod("isComplete",boolean.class,int.class);
        equal(complete.invoke(words,false,3),false);
        equal(complete.invoke(words,true,4),false); // Scanner done, last word still transparent.
        equal(complete.invoke(words,true,3),true); // Duplicate render overload did not count twice.
        words=type.getConstructor().newInstance();
        append.invoke(words,new Object(),"你",0,1f,1f,true);
        append.invoke(words,new Object(),"好",0,1f,1f,true);
        equal(type.getMethod("text").invoke(words),"你好");
        for(int i=0;i<600;i++) append.invoke(words,new Object(),"字",0,1f,1f,true);
        equal(type.getMethod("truncated").invoke(words),true);
        equal(complete.invoke(words,true,512),false);
        Method visible;
        try { visible=Class.forName("communicationmod.observation.DialogueVisibility").getMethod(
            "allows",String.class,boolean.class,boolean.class,float.class,float.class); }
        catch(ClassNotFoundException missing) { throw new AssertionError("Dialogue overlay visibility policy missing",missing); }
        equal(visible.invoke(null,"NONE",false,false,0f,0f),true);
        equal(visible.invoke(null,"SHOP",true,false,0f,0f),true);
        equal(visible.invoke(null,"MAP",true,false,0f,0f),false);
        equal(visible.invoke(null,"NONE",true,false,0f,0f),false);
        equal(visible.invoke(null,"SHOP",true,true,0f,0f),false);
        equal(visible.invoke(null,"NONE",false,false,1f,0f),false);
        equal(visible.invoke(null,"NONE",false,false,0f,0.1f),false);
        equal(visible.invoke(null,"NONE",false,false,Float.NaN,0f),false);
        equal(visible.invoke(null,"CUSTOM_UNKNOWN",false,false,0f,0f),false);
        equal(((String)type.getMethod("text").invoke(words)).length()<=512,true);
        words=type.getConstructor().newInstance();
        append.invoke(words,new Object(),String.join("",Collections.nCopies(9000,"a")),0,1f,1f,false);
        equal(((String)type.getMethod("text").invoke(words)).length(),8193);
        equal(type.getMethod("truncated").invoke(words),true);
        System.out.println("PASS: rendered-word visibility, identity, lines, CJK joining and bounds (JDK-only)");
    }
    private static void equal(Object actual,Object expected) {
        if(!Objects.equals(actual,expected)) throw new AssertionError("expected <"+expected+"> but was <"+actual+">");
    }
}
