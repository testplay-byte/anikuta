@file:JvmName("PreferenceValue")

package app.anikuta.backup.model

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore
import kotlinx.serialization.Serializable

/**
 * A type-safe representation of a SharedPreferences value, for backup/restore.
 *
 * ## Why this exists
 * SharedPreferences values can be one of 6 JVM types: `Int`, `Long`, `Float`,
 * `String`, `Boolean`, or `Set<String>`. The old backup format (v1) stored every
 * value as `.toString()`, which is lossy — `Set<String>` became `"[a, b, c]"`
 * (not round-trippable) and there was no way to know if `"true"` was a Boolean
 * or a String. This sealed type preserves the original type so restore is exact.
 *
 * ## Aniyomi wire-format compatibility
 * This sealed class is **wire-compatible with aniyomi's `PreferenceValue`**.
 * kotlinx-serialization-protobuf encodes sealed-class discriminators by
 * declaration order (0-based) when no `@ProtoNumber` is present on the
 * subclasses. The declaration order here — `Int, Long, Float, String, Boolean,
 * StringSet` — **must exactly match** aniyomi's
 * `eu.kanade.tachiyomi.data.backup.models.PreferenceValue` so that backups
 * produced by anikuta can be read by aniyomi and vice-versa.
 *
 * **Do NOT reorder the subclasses below.** If you add a new type, append it
 * at the end (discriminator 6+) and document the aniyomi-compat impact.
 *
 * Reference: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupPreference.kt`
 */
@Serializable
sealed class PreferenceValue {
    /**
     * Wrap this typed value into the current device's [PreferenceStore].
     *
     * Implements aniyomi's "type-guard" pattern: a preference is only restored
     * if the key already exists on this device with a matching type. This
     * prevents a backup from a newer app version from writing unknown keys
     * that could crash an older version. Cross-device restore of prefs is
     * best-effort by design (same as aniyomi).
     *
     * @return `true` if the value was written, `false` if skipped (key missing
     *   or type mismatch on this device).
     */
    abstract fun restoreInto(store: PreferenceStore, key: String): Boolean
}

@Serializable
data class IntPreferenceValue(val value: Int) : PreferenceValue() {
    override fun restoreInto(store: PreferenceStore, key: String): Boolean {
        val current = store.getAll()[key] ?: return false
        if (current !is Int) return false
        store.getInt(key).set(value)
        return true
    }
}

@Serializable
data class LongPreferenceValue(val value: Long) : PreferenceValue() {
    override fun restoreInto(store: PreferenceStore, key: String): Boolean {
        val current = store.getAll()[key] ?: return false
        if (current !is Long) return false
        store.getLong(key).set(value)
        return true
    }
}

@Serializable
data class FloatPreferenceValue(val value: Float) : PreferenceValue() {
    override fun restoreInto(store: PreferenceStore, key: String): Boolean {
        val current = store.getAll()[key] ?: return false
        if (current !is Float) return false
        store.getFloat(key).set(value)
        return true
    }
}

@Serializable
data class StringPreferenceValue(val value: String) : PreferenceValue() {
    override fun restoreInto(store: PreferenceStore, key: String): Boolean {
        val current = store.getAll()[key] ?: return false
        if (current !is String) return false
        store.getString(key).set(value)
        return true
    }
}

@Serializable
data class BooleanPreferenceValue(val value: Boolean) : PreferenceValue() {
    override fun restoreInto(store: PreferenceStore, key: String): Boolean {
        val current = store.getAll()[key] ?: return false
        if (current !is Boolean) return false
        store.getBoolean(key).set(value)
        return true
    }
}

@Serializable
data class StringSetPreferenceValue(val value: Set<String>) : PreferenceValue() {
    override fun restoreInto(store: PreferenceStore, key: String): Boolean {
        val current = store.getAll()[key] ?: return false
        if (current !is Set<*>) return false
        @Suppress("UNCHECKED_CAST")
        store.getStringSet(key).set(value as Set<String>)
        return true
    }
}

/**
 * A single preference entry in a backup: the key + its typed value.
 *
 * Used by both the AniKuta format (JSON-serialized) and the Aniyomi format
 * (protobuf-serialized with `@ProtoNumber`). The proto numbers match aniyomi's
 * `BackupPreference` exactly for wire compatibility.
 */
@Serializable
data class BackupPreference(
    val key: String,
    val value: PreferenceValue,
)

/**
 * Converts a raw SharedPreferences value (from [PreferenceStore.getAll]) into
 * a typed [PreferenceValue], or `null` if the type is unsupported.
 *
 * Used by [app.anikuta.backup.prefs.PreferenceCollector].
 */
fun Any?.toPreferenceValue(): PreferenceValue? = when (this) {
    is Int -> IntPreferenceValue(this)
    is Long -> LongPreferenceValue(this)
    is Float -> FloatPreferenceValue(this)
    is String -> StringPreferenceValue(this)
    is Boolean -> BooleanPreferenceValue(this)
    is Set<*> -> {
        @Suppress("UNCHECKED_CAST")
        StringSetPreferenceValue(this as Set<String>)
    }
    else -> null
}
