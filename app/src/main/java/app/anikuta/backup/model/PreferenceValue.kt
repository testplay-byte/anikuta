package app.anikuta.backup.model

import app.anikuta.core.preference.PreferenceStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

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
 * ## Aniyomi wire-format compatibility (CRITICAL)
 * This sealed class is **wire-compatible with aniyomi's `PreferenceValue`**.
 *
 * kotlinx-serialization-protobuf encodes sealed-class subclasses using
 * **fully-qualified class-name discriminators** (NOT declaration-order indices).
 * The discriminator is stored as field 1 (a String) of the polymorphic wrapper,
 * and the actual value as field 2.
 *
 * Aniyomi's subclasses live in package `eu.kanade.tachiyomi.data.backup.models`,
 * so the discriminator written by aniyomi is, e.g.,
 * `"eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue"`.
 *
 * Our subclasses live in `app.anikuta.backup.model` — a different FQCN. To make
 * kotlinx-serialization match during decode, each subclass has `@SerialName`
 * set to aniyomi's FQCN. This makes our backups readable by aniyomi AND lets us
 * read aniyomi backups.
 *
 * **Do NOT change the @SerialName values** without also updating aniyomi compat.
 *
 * Reference: `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupPreference.kt`
 *
 * ## Discovery
 * This was discovered during on-device testing (2026-07-18): a real aniyomi
 * backup failed to decode with "protobuf decode failed". Root cause was that
 * our classes used the default FQCN (`app.anikuta.backup.model.*`) while
 * aniyomi writes `eu.kanade.tachiyomi.data.backup.models.*`. The @SerialName
 * annotation bridges this.
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
@SerialName("eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue")
data class IntPreferenceValue(val value: Int) : PreferenceValue() {
    override fun restoreInto(store: PreferenceStore, key: String): Boolean {
        val current = store.getAll()[key] ?: return false
        if (current !is Int) return false
        store.getInt(key).set(value)
        return true
    }
}

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue")
data class LongPreferenceValue(val value: Long) : PreferenceValue() {
    override fun restoreInto(store: PreferenceStore, key: String): Boolean {
        val current = store.getAll()[key] ?: return false
        if (current !is Long) return false
        store.getLong(key).set(value)
        return true
    }
}

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue")
data class FloatPreferenceValue(val value: Float) : PreferenceValue() {
    override fun restoreInto(store: PreferenceStore, key: String): Boolean {
        val current = store.getAll()[key] ?: return false
        if (current !is Float) return false
        store.getFloat(key).set(value)
        return true
    }
}

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue")
data class StringPreferenceValue(val value: String) : PreferenceValue() {
    override fun restoreInto(store: PreferenceStore, key: String): Boolean {
        val current = store.getAll()[key] ?: return false
        if (current !is String) return false
        store.getString(key).set(value)
        return true
    }
}

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue")
data class BooleanPreferenceValue(val value: Boolean) : PreferenceValue() {
    override fun restoreInto(store: PreferenceStore, key: String): Boolean {
        val current = store.getAll()[key] ?: return false
        if (current !is Boolean) return false
        store.getBoolean(key).set(value)
        return true
    }
}

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue")
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
