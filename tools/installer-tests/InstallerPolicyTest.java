import org.jellyfin.androidtv.update.InstallerLaunchPolicy;
import org.jellyfin.androidtv.update.InstallerHandoffGate;
import org.jellyfin.androidtv.update.UpdateFocusPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class InstallerPolicyTest {
    private static int assertions;
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("Assertion " + assertions + " failed");
    }
    public static void main(String[] args) {
        // Exhaustive primary/fallback outcomes. No fallback on success, denial or unknown error.
        for (InstallerLaunchPolicy.Result primary : InstallerLaunchPolicy.Result.values()) {
            for (InstallerLaunchPolicy.Result fallback : InstallerLaunchPolicy.Result.values()) {
                List<InstallerLaunchPolicy.Action> calls = new ArrayList<>();
                InstallerLaunchPolicy.Result result = InstallerLaunchPolicy.launch(action -> {
                    calls.add(action);
                    return action == InstallerLaunchPolicy.Action.VIEW ? primary : fallback;
                });
                boolean missing = primary == InstallerLaunchPolicy.Result.NO_HANDLER;
                check(result == (missing ? fallback : primary));
                check(calls.equals(missing
                    ? Arrays.asList(InstallerLaunchPolicy.Action.VIEW, InstallerLaunchPolicy.Action.INSTALL_PACKAGE)
                    : Arrays.asList(InstallerLaunchPolicy.Action.VIEW)));
            }
        }
        // Every updater state has a next action, never a disabled download or removed cancel.
        for (boolean candidate : new boolean[] {false, true}) {
            for (boolean apk : new boolean[] {false, true}) {
                check(UpdateFocusPolicy.preferred(true, apk, candidate).equals(UpdateFocusPolicy.CANCEL));
                check(UpdateFocusPolicy.preferred(false, apk, candidate).equals(apk
                    ? UpdateFocusPolicy.INSTALL : candidate ? UpdateFocusPolicy.DOWNLOAD : UpdateFocusPolicy.CHECK));
            }
        }
        InstallerHandoffGate gate = new InstallerHandoffGate();
        long departed = gate.begin();
        gate.invalidate(); // onPause, even without onStop; onResume must not grant again.
        check(!gate.claim(departed));
        long retry = gate.begin(); // Explicit retry after returning.
        check(!gate.claim(departed)); // A late old completion cannot consume the new attempt.
        check(gate.claim(retry));
        check(!gate.claim(retry)); // No duplicate completion or automatic retry on return.
        long cancelled = gate.begin();
        gate.invalidate();
        long next = gate.begin();
        check(!gate.claim(cancelled));
        check(gate.claim(next));
        check(!gate.claim(next));
        System.out.println("Installer/focus policy: " + assertions + " assertions passed");
    }
}
