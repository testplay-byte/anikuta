package app.anikuta.backup.prefs

import app.anikuta.backup.model.BackupPreference
import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.core.util.system.logcat
import logcat.LogPriority

/**
 * Restores typed [BackupPreference] entries back into the [PreferenceStore].
 *
 * ## Type-guard pattern (same as aniyomi)
 * A preference is only written if the key **already exists** on this device
 * with a matching type. This prevents a backup from a newer app version from
 * writing unknown keys that could crash an older version. Cross-device restore
 * of prefs is best-effort by design.
 *
 * The actual type-check + write logic lives in
 * [app.anikuta.backup.model.PreferenceValue.restoreInto] — each sealed
 * subclass checks `store.getAll()[key]` and only writes if the type matches.
 *
 * ## Exclusion rules
 * Same as [PreferenceCollector]: `__APP_STATE_*`, `__PRIVATE_*` (unless
 * `includePrivate`), and [PreferenceCollector.DEDICATED_STORE_KEYS] are
 * skipped to avoid clobbering dedicated-store restores.
 *
 * ## Logging
 * Every restore logs: total attempted, restored count, skipped count (with
 * reasons). Individual failures are logged at WARN. This makes it easy to
 * debug restore issues via logcat (tag: `PrefRestorer`).
 *
 * ## Usage
 * ```
 * val restorer = PreferenceRestorer(preferenceStore)
 * val (restored, skipped) = restorer.restore(entries)
 * // restored: Int  — how many were written
 * // skipped: Int   — how many were skipped (missing key / type mismatch)
 * ```
 */
class PreferenceRestorer(
    private val preferenceStore: PreferenceStore,
) {

    companion object {
        private const val TAG = "PrefRestorer"
    }

    /**
     * Restore a list of [BackupPreference] entries into the store.
     *
     * @param entries the preferences to restore.
     * @param includePrivate if `true`, `__PRIVATE_*` keys are restored.
     *   Default `false`.
     * @return a [Result] with restored + skipped counts.
     */
    fun restore(
        entries: List<BackupPreference>,
        includePrivate: Boolean = false,
    ): Result {
        var restored = 0
        var skippedMissing = 0
        var skippedTypeMismatch = 0
        var skippedPrivate = 0
        var skippedDedicated = 0
        var skippedAppState = 0
        val errors = mutableListOf<String>()

        for (entry in entries) {
            try {
                when {
                    Preference.isAppState(entry.key) -> {
                        skippedAppState++
                    }
                    Preference.isPrivate(entry.key) && !includePrivate -> {
                        skippedPrivate++
                    }
                    entry.key in PreferenceCollector.DEDICATED_STORE_KEYS -> {
                        skippedDedicated++
                    }
                    else -> {
                        val current = preferenceStore.getAll()[entry.key]
                        if (current == null) {
                            skippedMissing++
                            logcat(LogPriority.DEBUG) { "Skip '${entry.key}': not present on this device" }
                        } else {
                            val written = entry.value.restoreInto(preferenceStore, entry.key)
                            if (written) {
                                restored++
                            } else {
                                skippedTypeMismatch++
                                logcat(LogPriority.WARN) {
                                    "Skip '${entry.key}': type mismatch (backup=${entry.value::class.simpleName}, device=${current::class.simpleName})"
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                errors.add("${entry.key}: ${e.message}")
                logcat(LogPriority.ERROR, e) { "Failed to restore preference '${entry.key}'" }
            }
        }

        logcat(LogPriority.DEBUG) {
            "Restore prefs: $restored restored, " +
                "$skippedMissing missing, $skippedTypeMismatch type-mismatch, " +
                "$skippedPrivate private, $skippedDedicated dedicated, $skippedAppState app-state"
        }

        return Result(
            restored = restored,
            skipped = skippedMissing + skippedTypeMismatch + skippedPrivate + skippedDedicated + skippedAppState,
            errors = errors,
        )
    }

    /**
     * Result of a preference restore operation.
     *
     * @property restored how many preferences were successfully written.
     * @property skipped how many were skipped (for any reason).
     * @property errors per-key error messages (empty if none).
     */
    data class Result(
        val restored: Int,
        val skipped: Int,
        val errors: List<String>,
    )
}
