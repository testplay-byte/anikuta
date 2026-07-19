package app.anikuta.ui.theme

import androidx.compose.ui.graphics.Color

// Brand seed: lime green (#b3f55b) — user-selected brand color (2026-07-18).
// This is a bright yellowish-green (HCT: hue≈84, chroma≈72, tone≈85).
// The full tonal palette is derived below for light + dark schemes.

// ---- Light scheme ----
val Primary = Color(0xFF4A6200)        // tone 40 — dark lime, readable with white text
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFCDFF6E) // tone 90 — light lime
val OnPrimaryContainer = Color(0xFF141D00)

val Secondary = Color(0xFF596248)       // muted olive
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFDDE7CB)
val OnSecondaryContainer = Color(0xFF171F0A)

val Tertiary = Color(0xFF3B6653)        // muted teal-green
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFBDECD5)
val OnTertiaryContainer = Color(0xFF002115)

val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)

val Background = Color(0xFFFDFBF4)
val OnBackground = Color(0xFF1B1C16)
val Surface = Color(0xFFFDFBF4)
val OnSurface = Color(0xFF1B1C16)
val SurfaceVariant = Color(0xFFE1E4D3)
val OnSurfaceVariant = Color(0xFF44483B)
val Outline = Color(0xFF757969)

// ---- Dark scheme ----
// Dark primary IS the user's #b3f55b (tone 80).
val DarkPrimary = Color(0xFFB3F55B)
val DarkOnPrimary = Color(0xFF243300)
val DarkPrimaryContainer = Color(0xFF364A00)  // tone 30
val DarkOnPrimaryContainer = Color(0xFFCDFF6E)

val DarkSecondary = Color(0xFFC0CAAE)
val DarkOnSecondary = Color(0xFF2B331E)
val DarkSecondaryContainer = Color(0xFF414A32)
val DarkOnSecondaryContainer = Color(0xFFDDE7CB)

val DarkTertiary = Color(0xFFA2D0B9)
val DarkOnTertiary = Color(0xFF073828)
val DarkTertiaryContainer = Color(0xFF224E3E)
val DarkOnTertiaryContainer = Color(0xFFBDECD5)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF1B1C16)
val DarkOnBackground = Color(0xFFE4E3D6)
val DarkSurface = Color(0xFF1B1C16)
val DarkOnSurface = Color(0xFFE4E3D6)
val DarkSurfaceVariant = Color(0xFF44483B)
val DarkOnSurfaceVariant = Color(0xFFC5C9B8)
val DarkOutline = Color(0xFF8F9383)
