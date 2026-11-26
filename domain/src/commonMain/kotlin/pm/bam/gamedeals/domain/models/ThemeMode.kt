package pm.bam.gamedeals.domain.models

/**
 * User-selectable app theme. [SYSTEM] follows the OS dark/light setting; [LIGHT]/[DARK] force a scheme.
 * Persisted by [pm.bam.gamedeals.domain.repositories.settings.SettingsRepository] as the enum [name].
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}
