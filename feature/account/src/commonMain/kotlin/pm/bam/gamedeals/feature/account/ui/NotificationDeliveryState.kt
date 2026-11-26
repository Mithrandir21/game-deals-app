package pm.bam.gamedeals.feature.account.ui

/**
 * Pure derivations for the Account hub's "Background alerts" toggle, pulled out of the composable because
 * the precedence has regressed before (a revoked permission reading as "on"; a stale denial keeping the
 * "blocked" rationale up after the permission was granted in system settings).
 */

/** The switch position: on only when opted in AND the OS still permits posting. */
internal fun backgroundAlertsActive(optedIn: Boolean, permissionGranted: Boolean): Boolean =
    optedIn && permissionGranted

/**
 * Whether to show the "blocked — open settings" rationale: the OS permission is off while the user wants
 * alerts (already opted in, or just refused the in-app prompt). Gated on the live permission, so returning
 * from system settings with it granted clears the rationale regardless of the sticky [permissionDenied].
 */
internal fun backgroundAlertsBlocked(
    optedIn: Boolean,
    permissionGranted: Boolean,
    permissionDenied: Boolean,
): Boolean = !permissionGranted && (optedIn || permissionDenied)
