package pm.bam.gamedeals.feature.onboarding.ui

/**
 * The notification step's visible state, derived from the three inputs the slide reacts to. Pulled out of
 * the composable so the precedence — which has had regressions — is unit-testable:
 *  - a revoked OS permission must never read as [Active] just because the opt-in flag is on;
 *  - granting the permission via system settings (so [permissionGranted] flips true on resume) must drop
 *    the [Blocked] deep-link even though the stale in-app [denied] flag lingers.
 */
internal enum class NotificationStep {
    /** Opt-in on AND permission granted — alerts are genuinely active. */
    Active,

    /** Permission granted, opt-in off — a single tap turns alerts on. */
    Enable,

    /** Permission off, not yet refused in-app — inform, and offer to turn on (which prompts). */
    Off,

    /** Permission refused and still off — the in-app prompt won't reappear; deep-link to OS settings. */
    Blocked,
}

internal fun notificationStep(
    enabled: Boolean,
    permissionGranted: Boolean,
    denied: Boolean,
): NotificationStep = when {
    enabled && permissionGranted -> NotificationStep.Active
    permissionGranted -> NotificationStep.Enable // permission is on, so only the opt-in is missing
    denied -> NotificationStep.Blocked // permission off and the prompt has already been refused
    else -> NotificationStep.Off
}
