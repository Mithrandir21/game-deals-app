package pm.bam.gamedeals.domain.scheduling

/**
 * Platform port for (de)registering the periodic background poll that drives notification delivery —
 * Android WorkManager `PeriodicWorkRequest` / iOS `BGTaskScheduler`. [schedule] is idempotent (safe to
 * call on every app start to re-arm after reboot/update); [cancel] tears the periodic work down (on
 * opt-out or logout). Bound per-platform in `domainAndroidModule` / `domainIosModule`.
 */
interface NotificationScheduler {
    fun schedule()
    fun cancel()
}
