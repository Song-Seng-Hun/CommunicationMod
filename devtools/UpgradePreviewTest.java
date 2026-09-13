import java.lang.reflect.*;
import java.util.*;

/** Tests the real preview service through a small card model; no engine/native classes. */
public final class UpgradePreviewTest {
    static final class Card {
        int count, damage=12, cost=2, copies, limit=99;
        boolean visible=true, branch, fail, alias, wrong, extra;
        Card copy() { Card c=new Card(); c.count=count;c.damage=damage;c.cost=cost;c.limit=limit;return c; }
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        Class<?> service,adapter;
        try { service=Class.forName("communicationmod.observation.UpgradePreview");
            adapter=Class.forName("communicationmod.observation.UpgradePreview$Adapter"); }
        catch(ClassNotFoundException absent){throw new AssertionError("Missing next-upgrade comparison service",absent);}
        Object engine=service.getConstructor(int.class).newInstance(2);
        Object port=Proxy.newProxyInstance(adapter.getClassLoader(),new Class<?>[]{adapter},(p,m,a)->{
            Card c=(Card)a[0];
            switch(m.getName()) {
                case "visible":return c.visible;
                case "unavailableReason":return c.branch?"upgrade_choice_required":null;
                case "snapshot":return snapshot(c);
                case "canUpgrade":return c.count<c.limit;
                case "copy":c.copies++;if(c.fail)throw new IllegalStateException("fixture failure");if(c.alias)return c;Card d=c.copy();if(c.wrong)d.damage=0;return d;
                case "upgrade":c.damage+=4+c.count;c.count++;c.cost=1;return null;
                default:throw new AssertionError(m.getName());
            }
        });
        Method describe=service.getMethod("describe",Object.class,String.class,adapter);
        Card c=new Card();
        Map<String,Object> result=(Map<String,Object>)describe.invoke(engine,c,"state-1",port);
        check(result.get("status").equals("available"),"ordinary upgrade available");
        Map<String,Object> after=(Map<String,Object>)result.get("after");
        check(after.get("upgrades").equals(1)&&after.get("base_damage").equals(16),"one actual next step");
        check(c.count==0&&c.damage==12&&c.cost==2,"original was not upgraded");
        check(Boolean.TRUE.equals(result.get("can_upgrade_after")),"further upgrades possible");
        check(((List<?>)result.get("changed_fields")).contains("description"),"changed effect text identified");
        Map<?,?> delta=(Map<?,?>)((Map<?,?>)result.get("numeric_changes")).get("base_cost");
        check(delta.get("delta").equals(-1L),"cost reduction delta");
        describe.invoke(engine,c,"state-1",port);check(c.copies==1,"unchanged decision reuses preview");
        // A caller must not be able to corrupt the cached snapshot.
        after.put("base_damage",999);
        result=(Map<String,Object>)describe.invoke(engine,c,"state-1",port);
        check(((Map<?,?>)result.get("after")).get("base_damage").equals(16),"cached result isolated from consumer mutation");
        c.count=3;c.damage=27;
        result=(Map<String,Object>)describe.invoke(engine,c,"state-1",port);
        check(((Map<?,?>)result.get("after")).get("base_damage").equals(34),"repeat upgrade uses current +3 not base template");
        check(((Map<?,?>)result.get("after")).get("upgrades").equals(4),"repeat count advances once");
        check(c.copies==2&&c.count==3&&c.damage==27,"source fingerprint invalidates without mutation");
        describe.invoke(engine,c,"state-2",port);check(c.copies==3,"decision context invalidates");
        Card max=new Card();max.limit=0;
        result=(Map<String,Object>)describe.invoke(engine,max,"state-2",port);
        check(result.get("status").equals("not_upgradable")&&max.copies==0,"maxed card does not copy");
        Card hidden=new Card();hidden.visible=false;hidden.fail=true;
        result=(Map<String,Object>)describe.invoke(engine,hidden,"state-2",port);
        check(result.get("status").equals("unavailable")&&!result.containsKey("after")&&hidden.copies==0,"hidden gate before copy");
        Card branch=new Card();branch.branch=true;
        result=(Map<String,Object>)describe.invoke(engine,branch,"state-2",port);
        check(result.get("reason").equals("upgrade_choice_required")&&branch.copies==0,"no implicit random branch");
        for(String mode:Arrays.asList("fail","alias","wrong")) {
            Card broken=new Card();Field f=Card.class.getDeclaredField(mode);f.setBoolean(broken,true);
            result=(Map<String,Object>)describe.invoke(engine,broken,"state-2",port);
            check(result.get("status").equals("unavailable")&&!result.containsKey("after"),"failed preview explicit: "+mode);
            check(broken.count==0&&broken.damage==12,"bad copy never upgraded: "+mode);
            describe.invoke(engine,broken,"state-2",port);check(broken.copies==1,"failed preview is not retried every frame: "+mode);
        }
        Card modified=new Card();modified.extra=true;
        result=(Map<String,Object>)describe.invoke(engine,modified,"state-2",port);
        check(result.get("status").equals("unavailable"),"copy losing an existing card effect must not become a predicted upgrade");
        // A finite cache releases prior cards rather than growing for an endless run.
        describe.invoke(engine,new Card(),"state-2",port);describe.invoke(engine,new Card(),"state-2",port);
        describe.invoke(engine,c,"state-2",port);check(c.copies==4,"bounded cache evicts old cards");
        System.out.println("PASS: next/repeated/cost/max/hidden/branch/failure/source-preservation/cache contracts");
    }
    static Map<String,Object> snapshot(Card c) {
        Map<String,Object> s=new LinkedHashMap<>();s.put("id","fixture");s.put("upgrades",c.count);
        s.put("base_damage",c.damage);s.put("base_cost",c.cost);s.put("description","Damage "+c.damage+" at cost "+c.cost);
        s.put("exhausts",c.extra);
        s.put("description_complete",true);s.put("displayed_cost_complete",false);return s;
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
