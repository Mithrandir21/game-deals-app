package pm.bam.gamedeals.feature.appupdate.ui

/** What the app shell should render for the minimum-version gate. */
sealed interface AppUpdateState {

    /** Nothing to show — the normal state, and the state every failure path resolves to. */
    data object Hidden : AppUpdateState

    /**
     * This build is below the published floor.
     *
     * @property minimumVersion the floor, shown to the user and stamped on analytics.
     * @property blocking `true` for a hard gate the user cannot dismiss; `false` for a nudge with a "Later".
     */
    data class Prompt(val minimumVersion: String, val blocking: Boolean) : AppUpdateState
}
