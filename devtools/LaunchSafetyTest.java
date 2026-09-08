import java.lang.reflect.InvocationTargetException;

/** No game jars are on this test's classpath: it cannot initialize Steam. */
public final class LaunchSafetyTest {
    public static void main(String[] args) throws Exception {
        // A property is deliberately not accepted as proof of isolation.
        System.setProperty("communicationmod.offline", "true");
        if (communicationmod.safety.AutomationSafety.isAutomationAllowed()) {
            throw new AssertionError("Unverified automation enabled");
        }
        try {
            communicationmod.safety.AutomationSafety.requireAutomationAllowed();
            throw new AssertionError("Unverified command accepted");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("COMM-SAFETY-BLOCK")) throw expected;
        }
        for (String launcher : new String[] {"DevLoader", "DevGameLauncher"}) {
            try {
                Class.forName(launcher).getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
                throw new AssertionError(launcher + " allowed an unverified runtime");
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (!(cause instanceof IllegalStateException)
                        || !cause.getMessage().contains("COMM-SAFETY-BLOCK")) {
                    throw new AssertionError(launcher + " reached game loading before the safety gate", cause);
                }
            }
        }
        System.out.println("PASS: both launchers refuse before loading any game/Steam class");
    }
}
