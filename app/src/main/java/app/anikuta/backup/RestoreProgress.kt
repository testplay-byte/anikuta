package app.anikuta.backup

/**
 * Live progress events emitted during a restore operation (Step 3 of the
 * restore flow — the live-progress screen).
 *
 * Emitted by [BackupManager.restoreBackupWithOptions] via its `onProgress`
 * callback. The UI collects these into a list to show a live log + progress bar.
 *
 * ## State machine
 * ```
 * Decoding → Restoring → Complete
 *                  ↘ Error
 * ```
 *
 * - [Decoding]: the backup file is being read + parsed (Step 1, fast).
 * - [Restoring]: data is being written back to app state (Step 3, the long part).
 *   Emitted multiple times with increasing `current` / updated `message`.
 * - [Complete]: restore finished successfully.
 * - [Error]: restore failed fatally.
 */
sealed class RestoreProgress {
    /** The backup file is being read + decoded. */
    data class Decoding(val message: String) : RestoreProgress()

    /** Data is being written back to app state. Emitted repeatedly with updates. */
    data class Restoring(
        val total: Int,
        val current: Int,
        val message: String,
    ) : RestoreProgress() {
        /** Progress fraction 0..1 (0 if total is 0). */
        val fraction: Float get() = if (total <= 0) 0f else current.toFloat() / total
    }

    /** Restore finished successfully. */
    data class Complete(val summary: String) : RestoreProgress()

    /** Restore failed. */
    data class Error(val message: String) : RestoreProgress()
}
