/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.anikuta.player

import dev.icerock.moko.resources.StringResource
import app.anikuta.player.settings.DecoderPreferences
import app.anikuta.core.preference.Preference
// TODO: MR
// TODO: AYMR

/**
 * Results of the set as art feature.
 */
enum class SetAsArt {
    Success,
    AddToLibraryFirst,
    Error,
}

enum class ArtType {
    Cover,
    Background,
    Thumbnail,
}

enum class PlayerOrientation(val titleRes: StringResource) {
    Free("TODO"),
    Video("TODO"),
    Portrait("TODO"),
    ReversePortrait("TODO"),
    SensorPortrait("TODO"),
    Landscape("TODO"),
    ReverseLandscape("TODO"),
    SensorLandscape("TODO"),
}

enum class VideoAspect(val titleRes: StringResource) {
    Crop("TODO"),
    Fit("TODO"),
    Stretch("TODO"),
}

/**
 * Action performed by a button, like double tap or media controls
 */
enum class SingleActionGesture(val stringRes: StringResource) {
    None(stringRes = "TODO"),
    Seek(stringRes = "TODO"),
    PlayPause(stringRes = "TODO"),
    Switch(stringRes = "TODO"),
    Custom(stringRes = "TODO"),
}

/**
 * Key codes sent through the `Custom` option in gestures
 */
enum class CustomKeyCodes(val keyCode: String) {
    DoubleTapLeft("0x10001"),
    DoubleTapCenter("0x10002"),
    DoubleTapRight("0x10003"),
    MediaPrevious("0x10004"),
    MediaPlay("0x10005"),
    MediaNext("0x10006"),
}

enum class Decoder(val title: String, val value: String) {
    AutoCopy("Auto", "auto-copy"),
    Auto("Auto", "auto"),
    SW("SW", "no"),
    HW("HW", "mediacodec-copy"),
    HWPlus("HW+", "mediacodec"),
}

fun getDecoderFromValue(value: String): Decoder {
    return Decoder.entries.first { it.value == value }
}

enum class Debanding {
    None,
    CPU,
    GPU,
}

enum class Sheets {
    None,
    PlaybackSpeed,
    SubtitleTracks,
    AudioTracks,
    QualityTracks,
    Chapters,
    More,
    Screenshot,
}

enum class Panels {
    None,
    SubtitleSettings,
    SubtitleDelay,
    AudioDelay,
    VideoFilters,
}

sealed class Dialogs {
    data object None : Dialogs()
    data object EpisodeList : Dialogs()
    data class IntegerPicker(
        val defaultValue: Int,
        val minValue: Int,
        val maxValue: Int,
        val step: Int,
        val nameFormat: String,
        val title: String,
        val onChange: (Int) -> Unit,
        val onDismissRequest: () -> Unit,
    ) : Dialogs()
}

sealed class PlayerUpdates {
    data object None : PlayerUpdates()
    data object DoubleSpeed : PlayerUpdates()
    data object AspectRatio : PlayerUpdates()
    data class ShowText(val value: String) : PlayerUpdates()
    data class ShowTextResource(val textResource: StringResource) : PlayerUpdates()
}

enum class VideoFilters(
    val titleRes: StringResource,
    val preference: (DecoderPreferences) -> Preference<Int>,
    val mpvProperty: String,
) {
    BRIGHTNESS(
        "TODO",
        { it.brightnessFilter() },
        "brightness",
    ),
    SATURATION(
        "TODO",
        { it.saturationFilter() },
        "saturation",
    ),
    CONTRAST(
        "TODO",
        { it.contrastFilter() },
        "contrast",
    ),
    GAMMA(
        "TODO",
        { it.gammaFilter() },
        "gamma",
    ),
    HUE(
        "TODO",
        { it.hueFilter() },
        "hue",
    ),
}
