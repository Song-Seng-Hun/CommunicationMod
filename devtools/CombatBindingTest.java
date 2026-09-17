import javassist.*;
import javassist.expr.*;

/** Reads/transforms actual bytecode only. Does not define/initialize any game class. */
public final class CombatBindingTest {
    public static void main(String[] args) throws Exception {
        ClassPool pool=new ClassPool(true);
        for(String path:args) pool.appendClassPath(path);
        CtClass binding;
        try { binding=pool.get("communicationmod.observation.CombatObservation"); }
        catch(NotFoundException e) { throw new AssertionError("Missing live combat readiness binding",e); }
        for(CtMethod method:binding.getDeclaredMethods()) method.instrument(new ExprEditor() {
            @Override public void edit(MethodCall call) {
                if(call.getMethodName().equals("getCurrRoom")) throw new AssertionError("Unsafe menu room lookup");
                if(call.getMethodName().equals("isDeadOn")) throw new AssertionError("Observation must not latch Dead On");
            }
        });
        CtMethod frame=pool.get("com.megacrit.cardcrawl.core.CardCrawlGame").getDeclaredMethod("render");
        Class.forName("communicationmod.patches.CombatReadinessPatch$Frame").getMethod("Raw",CtBehavior.class).invoke(null,frame);
        require(frame,"completeFrame");
        require(pool.get("communicationmod.CommandExecutor").getDeclaredMethod("isCommandAvailable"),"allowsLegacyCommand");
        require(pool.get("communicationmod.GameStateConverter").getDeclaredMethod("getCommunicationState"),"observation");
        require(pool.get("communicationmod.GameStateConverter").getDeclaredMethod("getCommunicationState"),"finishObservation");
        require(pool.get("communicationmod.GameStateConverter").getDeclaredMethod("getCombatState"),"handComplete");
        require(pool.get("communicationmod.GameStateConverter").getDeclaredMethod("convertCardToJson"),"handComplete");
        require(pool.get("communicationmod.GameStateListener").getDeclaredMethod("isWaitingForCommand"),"ready");
        CtMethod execute=pool.get("communicationmod.protocol.CombatActions").getDeclaredMethod("execute");
        require(execute,"claim");
        javassist.bytecode.CodeIterator instructions=execute.getMethodInfo().getCodeAttribute().iterator();
        int start=instructions.next();
        if(instructions.byteAt(start)!=javassist.bytecode.Opcode.INVOKESTATIC)throw new AssertionError("Combat dispatcher lacks first-instruction safety hold");
        javassist.bytecode.ConstPool constants=execute.getMethodInfo().getConstPool();
        int ref=instructions.u16bitAt(start+1);
        if(!constants.getMethodrefClassName(ref).equals("communicationmod.safety.AutomationSafety")
            || !constants.getMethodrefName(ref).equals("requireAutomationAllowed")) throw new AssertionError("Combat dispatcher bypasses automation hold");
        dispatchFixture(pool,execute);
        java.util.Set<String> fields=new java.util.HashSet<>();
        final boolean[] legacyBlock={false};
        binding.getDeclaredMethod("capture").instrument(new ExprEditor() {
            @Override public void edit(FieldAccess field) { if(field.isReader())fields.add(field.getFieldName()); }
            @Override public void edit(MethodCall call) {
                if(call.getClassName().equals("communicationmod.GameStateListener") && call.getMethodName().equals("isStateUpdateBlocked"))legacyBlock[0]=true;
            }
        });
        if(!legacyBlock[0])throw new AssertionError("Combat readiness ignores an existing state-update block");
        for(String name:new String[]{"currentAction","turnStartCurrentAction","preTurnActions","actions","cardQueue","monsterQueue","usingCard","limbo","isEndingTurn","endTurnQueued","turnHasEnded"})
            if(!fields.contains(name))throw new AssertionError("Missing pending-work check: "+name);
        // Audited Hermit UI predicate: isDeadOnPos must not write fields or call the latching predicate.
        CtMethod positional=pool.get("hermit.cards.AbstractHermitCard").getDeclaredMethod("isDeadOnPos");
        positional.instrument(new ExprEditor() {
            @Override public void edit(FieldAccess field) { if(field.isWriter())throw new AssertionError("Hermit position predicate became mutating"); }
            @Override public void edit(MethodCall call) {
                if(!java.util.Arrays.asList("hasPower","indexOf","size","abs").contains(call.getMethodName()))
                    throw new AssertionError("Re-audit changed Hermit position predicate: "+call.getMethodName());
            }
        });
        System.out.println("PASS: actual frame injection, state/command guards and Hermit read-only UI predicate (no game execution)");
    }
    /** Copy only the actual small dispatcher into a parentless JDK fixture with the real pure gate. */
    private static void dispatchFixture(ClassPool actual,CtMethod execute) throws Exception {
        ClassPool fixture=new ClassPool(true);
        CtClass gate=fixture.makeClass(new java.io.ByteArrayInputStream(actual.get("communicationmod.observation.CombatDecision").toBytecode()));
        CtClass safety=fixture.makeClass("communicationmod.safety.AutomationSafety");
        safety.addField(CtField.make("public static boolean allowed=false;",safety));
        safety.addMethod(CtNewMethod.make("public static void requireAutomationAllowed(){if(!allowed)throw new IllegalStateException(\"hold\");}",safety));
        CtClass binding=fixture.makeClass("communicationmod.observation.CombatObservation");
        binding.addField(CtField.make("public static communicationmod.observation.CombatDecision gate=new communicationmod.observation.CombatDecision();",binding));
        binding.addField(CtField.make("public static String key=\"A,B,C\";",binding));
        binding.addField(CtField.make("public static String reason=null;",binding));
        binding.addMethod(CtNewMethod.make("public static void validate(String id,String mode){gate.validate(id,mode,key,reason);}",binding));
        binding.addMethod(CtNewMethod.make("public static void claim(String id,String mode){gate.claim(id,mode,key,reason);}",binding));
        CtClass dispatch=fixture.makeClass("CombatDispatchFixture");
        CtMethod copied=CtNewMethod.copy(execute,dispatch,null); copied.setModifiers(Modifier.PUBLIC|Modifier.STATIC); dispatch.addMethod(copied);
        final java.util.Map<String,byte[]> bytes=new java.util.HashMap<>();
        for(CtClass type:new CtClass[]{gate,safety,binding,dispatch})bytes.put(type.getName(),type.toBytecode());
        ClassLoader loader=new ClassLoader(null) {
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
                byte[] code=bytes.get(name); if(code==null)throw new ClassNotFoundException("Non-JDK/game class refused: "+name);
                return defineClass(name,code,0,code.length);
            }
        };
        Class<?> observed=loader.loadClass(binding.getName()), guarded=loader.loadClass(safety.getName());
        Object decision=observed.getField("gate").get(null);
        java.lang.reflect.Method frame=decision.getClass().getMethod("completeFrame",String.class,String.class,String.class);
        frame.invoke(decision,"play","A,B,C",null); frame.invoke(decision,"play","A,B,C",null);
        String id=(String)decision.getClass().getMethod("issue").invoke(decision);
        java.lang.reflect.Method method=loader.loadClass(dispatch.getName()).getMethod("execute",String.class,String.class,Runnable.class,Runnable.class);
        int[] mutations={0}, validations={0};
        Runnable validate=()->validations[0]++, mutate=()->mutations[0]++;
        rejected(method,id,validate,mutate,IllegalStateException.class);
        if(validations[0]!=0||mutations[0]!=0)throw new AssertionError("Safety hold executed callbacks");
        guarded.getField("allowed").set(null,true);
        observed.getField("reason").set(null,"drawing");
        rejected(method,id,validate,mutate,IllegalArgumentException.class);
        if(mutations[0]!=0)throw new AssertionError("Partial draw reached mutation");
        observed.getField("reason").set(null,null);
        frame.invoke(decision,"play","A,B,C",null); frame.invoke(decision,"play","A,B,C",null);
        id=(String)decision.getClass().getMethod("issue").invoke(decision);
        Runnable changeDuringValidation=()->{try{observed.getField("key").set(null,"A,B,C,D");}catch(Exception e){throw new AssertionError(e);}};
        rejected(method,id,changeDuringValidation,mutate,IllegalArgumentException.class);
        if(mutations[0]!=0)throw new AssertionError("Dispatch did not recheck after card validation");
        frame.invoke(decision,"play","A,B,C,D",null); frame.invoke(decision,"play","A,B,C,D",null);
        id=(String)decision.getClass().getMethod("issue").invoke(decision);
        method.invoke(null,id,"play",validate,mutate);
        if(mutations[0]!=1)throw new AssertionError("Stable decision failed to dispatch once");
        rejected(method,id,validate,mutate,IllegalArgumentException.class);
        if(mutations[0]!=1)throw new AssertionError("Consumed decision replayed");
        System.out.println("PASS: actual dispatcher body with real pure gate: safety first, draw busy, TOCTOU recheck, consume before mutation, no replay");
    }
    private static void rejected(java.lang.reflect.Method method,String id,Runnable validate,Runnable mutate,Class<?> exception)throws Exception {
        try {method.invoke(null,id,"play",validate,mutate);}
        catch(java.lang.reflect.InvocationTargetException e){if(exception.isInstance(e.getCause()))return;throw e;}
        throw new AssertionError("Unsafe actual dispatcher accepted fixture input");
    }
    private static void require(CtMethod method,String name) throws Exception {
        final boolean[] found={false};
        method.instrument(new ExprEditor() {
            @Override public void edit(MethodCall call) {
                if(call.getClassName().equals("communicationmod.observation.CombatObservation") && call.getMethodName().equals(name)) found[0]=true;
            }
        });
        if(!found[0])throw new AssertionError("Missing combat boundary: "+method.getLongName()+" -> "+name);
    }
}
