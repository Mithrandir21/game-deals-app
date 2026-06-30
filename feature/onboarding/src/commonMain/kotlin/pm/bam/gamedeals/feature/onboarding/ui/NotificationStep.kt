package pm.bam.gamedeals.feature.onboarding.ui

/**
 * The notification step's visible state under the forced-choice model. Pulled out of the composable so the
 * precedence — which has had regressions — is unit-testable:
 *  - until the user makes a choice this run the step is [Choose] (Allow / Not now), so a replay always
 *    re-asks rather than reading a stale persisted opt-in as already decided;
 *  - a revoked OS permission must never read as [Active] just because Allow was tapped — once the live
 *    [permissionGranted] drops, the step falls back to [Blocked] (the system-settings deep-link).
 */
internal enum class NotificationStep {
    /** No choice made yet this run — offer Allow / Not now. */
    Choose,

    /** Tapped Allow and the OS permission is granted — alerts are genuinely active. */
    Active,

    /** Tapped Allow but the OS prompt was refused or suppressed — deep-link to system settings. */
    Blocked,

    /** Tapped "Not now" — alerts left off (changeable later in Account). */
    Declined,
}

internal fun notificationStep(
    decided: Boolean,
    declined: Boolean,
    permissionGranted: Boolean,
): NotificationStep = when {
    !decided -> NotificationStep.Choose
    declined -> NotificationStep.Declined
    permissionGranted -> NotificationStep.Active // Allow tapped and the permission is on
    else -> NotificationStep.Blocked // Allow tapped but the permission is off/refused
}
