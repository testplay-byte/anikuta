package app.anikuta.backup

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore

/**
 * Preferences for the automatic backup feature.
 *
 * The user configures:
 *  - **Frequency**: how often to auto-backup (in hours). 0 = disabled.
 *  - **Retention**: max number of backup files to keep. Older ones auto-deleted.
 *  - **Last backup timestamp**: when the last auto-backup ran (app-state, not backed up).
 *  - **Auto-backup directory**: SAF URI where backups are written.
 *
 * Per user decision #4 (2026-07-18): "allow the user to select the auto backup
 * frequency and also allow the user to configure the number of backups so only
 * that number of backups will be stored and the older ones will automatically
 * get deleted."
 *
 * Related files:
 *  - [BackupAutoJob] — the WorkManager periodic worker.
 *  - [BackupSettingsScreen] — the settings UI for these prefs.
 */
class AutoBackupPreferences(
    private val preferenceStore: PreferenceStore,
) {

    companion object {
        /** SharedPreferences key for auto-backup enabled + frequency (hours). 0 = disabled. */
        private const val KEY_FREQUENCY_HOURS = "auto_backup_frequency_hours"
        /** SharedPreferences key for max number of backup files to keep. */
        private const val KEY_MAX_BACKUPS = "auto_backup_max_backups"
        /** SharedPreferences key for the SAF directory URI (app-state — not backed up). */
        private const val KEY_DIRECTORY_URI = Preference.appStateKey("auto_backup_directory_uri")
        /** SharedPreferences key for the last auto-backup timestamp (app-state — not backed up). */
        private const val KEY_LAST_BACKUP_TIMESTAMP = Preference.appStateKey("auto_backup_last_timestamp")
        /** SharedPreferences key for the backup format ("anikuta" or "tachibk"). */
        private const val KEY_FORMAT = "auto_backup_format"

        /** Default frequency: 0 = disabled (user must opt in). */
        const val DEFAULT_FREQUENCY_HOURS = 0
        /** Default retention: keep 4 backups (matches aniyomi's MAX_AUTO_BACKUPS). */
        const val DEFAULT_MAX_BACKUPS = 4
        /** Default format: anikuta (our own format — most complete). */
        const val DEFAULT_FORMAT = "anikuta"
    }

    /** Auto-backup frequency in hours. 0 = disabled. */
    fun frequencyHours(): Preference<Int> =
        preferenceStore.getInt(KEY_FREQUENCY_HOURS, DEFAULT_FREQUENCY_HOURS)

    /** Max number of backup files to keep (older ones auto-deleted). */
    fun maxBackups(): Preference<Int> =
        preferenceStore.getInt(KEY_MAX_BACKUPS, DEFAULT_MAX_BACKUPS)

    /** The SAF directory URI where auto-backups are written (app-state — not backed up). */
    fun directoryUri(): Preference<String> =
        preferenceStore.getString(KEY_DIRECTORY_URI, "")

    /** The format for auto-backups ("anikuta" or "tachibk"). */
    fun format(): Preference<String> =
        preferenceStore.getString(KEY_FORMAT, DEFAULT_FORMAT)

    /** Timestamp (epoch ms) of the last auto-backup (app-state — not backed up). */
    fun lastBackupTimestamp(): Preference<Long> =
        preferenceStore.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)

    /** Whether auto-backup is enabled (frequency > 0). */
    fun isEnabled(): Boolean = frequencyHours().get() > 0
}
