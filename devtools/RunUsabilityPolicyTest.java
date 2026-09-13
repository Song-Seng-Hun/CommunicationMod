import java.lang.reflect.*;
public final class RunUsabilityPolicyTest {
    public static void main(String[] args)throws Exception {
        Class<?> type;
        try{type=Class.forName("communicationmod.observation.RunUiPolicy");}
        catch(ClassNotFoundException missing){throw new AssertionError("Missing run usability policy",missing);}
        Method reward=type.getMethod("cardReward",boolean.class,boolean.class,boolean.class,int.class);
        for(int count:new int[]{1,3,4,8,20})check((Boolean)reward.invoke(null,true,false,false,count),"native candidate count "+count);
        check(!(Boolean)reward.invoke(null,false,false,false,3),"custom screen rejected");
        check(!(Boolean)reward.invoke(null,true,true,false,3),"touch mode rejected");
        check(!(Boolean)reward.invoke(null,true,false,true,3),"voting mode rejected");
        for(int count:new int[]{0,129})check(!(Boolean)reward.invoke(null,true,false,false,count),"invalid candidate count");
        Method proceed=type.getMethod("proceed",boolean.class,boolean.class,boolean.class);
        check((Boolean)proceed.invoke(null,true,false,false),"visible native proceed including unclaimed rewards and elite/boss rooms");
        check(!(Boolean)proceed.invoke(null,false,false,false),"hidden proceed");
        check(!(Boolean)proceed.invoke(null,true,true,false),"pending click");
        check(!(Boolean)proceed.invoke(null,true,false,true),"animation");
        Method potion;
        try{potion=type.getMethod("potionUse",boolean.class,boolean.class,boolean.class,boolean.class,boolean.class);}
        catch(NoSuchMethodException missing){throw new AssertionError("Missing potion eligibility policy",missing);}
        check((Boolean)potion.invoke(null,true,true,false,false,false),"non-target potion outside combat");
        check((Boolean)potion.invoke(null,true,true,true,true,true),"target potion in combat");
        check(!(Boolean)potion.invoke(null,true,true,true,false,true),"target potion outside combat");
        check(!(Boolean)potion.invoke(null,true,true,true,true,false),"missing or dead target");
        check(!(Boolean)potion.invoke(null,false,true,false,true,true),"empty potion slot");
        check(!(Boolean)potion.invoke(null,true,false,false,true,true),"game forbids potion");
        System.out.println("PASS: native card selectors and visible proceed without reward/room restrictions");
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
