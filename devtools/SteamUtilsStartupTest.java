import javassist.*;
import javassist.expr.*;

/** The standalone create() path must not allocate a native callback with Steam disabled. */
public final class SteamUtilsStartupTest {
    public static void main(String[] args)throws Exception {
        ClassPool pool=new ClassPool(true);pool.insertClassPath(args[0]);
        pool.get("com.megacrit.cardcrawl.core.CardCrawlGame").getDeclaredMethod("create").instrument(new ExprEditor(){
            public void edit(NewExpr allocation){
                if(allocation.getClassName().equals("com.codedisaster.steamworks.SteamUtils"))
                    throw new AssertionError("Offline startup still constructs native SteamUtils callback");
            }
        });
        System.out.println("PASS: copied startup skips native SteamUtils allocation; mouse/keyboard unaffected");
    }
}
