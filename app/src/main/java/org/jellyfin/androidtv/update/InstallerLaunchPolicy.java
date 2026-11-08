package org.jellyfin.androidtv.update;

/** Only a missing handler permits trying another standard installer action. Never retry a denial. */
public final class InstallerLaunchPolicy {
    private InstallerLaunchPolicy() {}

    public enum Action { VIEW, INSTALL_PACKAGE }
    public enum Result { OPENED, NO_HANDLER, BLOCKED, FAILED }

    public interface Launcher {
        Result launch(Action action);
    }

    public static Result launch(Launcher launcher) {
        Result result = launcher.launch(Action.VIEW);
        if (result == Result.NO_HANDLER) return launcher.launch(Action.INSTALL_PACKAGE);
        return result;
    }
}
