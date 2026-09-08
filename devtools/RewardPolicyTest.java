import java.lang.reflect.Method;
public final class RewardPolicyTest {
    public static void main(String[] args)throws Exception {
        Class<?> p;try{p=Class.forName("communicationmod.observation.RewardPolicy");}
        catch(ClassNotFoundException e){throw new AssertionError("Missing extended reward policy",e);}
        Method allowed=p.getMethod("claimable",String.class,boolean.class,boolean.class,boolean.class,boolean.class);
        for(String type:new String[]{"GOLD","STOLEN_GOLD","CARD","RELIC","POTION","EMERALD_KEY","SAPPHIRE_KEY"}) {
            check((Boolean)allowed.invoke(null,type,true,false,false,true),type);
            check(!(Boolean)allowed.invoke(null,type,true,true,false,true),"pending "+type);
            check(!(Boolean)allowed.invoke(null,type,true,false,true,true),"ignored "+type);
            check(!(Boolean)allowed.invoke(null,type,false,false,false,true),"custom subclass "+type);
        }
        check(!(Boolean)allowed.invoke(null,"POTION",true,false,false,false),"full potion slots");
        check(!(Boolean)allowed.invoke(null,"GEM",true,false,false,true),"unknown type");
        System.out.println("PASS: standard reward allowlist, full potion slots, pending/ignored/custom guards");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
