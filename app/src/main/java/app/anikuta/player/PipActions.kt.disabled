import androidx.compose.ui.res.stringResource
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

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import dev.icerock.moko.resources.StringResource
// TODO: import R
import app.anikuta.core.i18n.stringResource
// TODO: MR
// TODO: AYMR

fun createPipActions(
    context: Context,
    isPaused: Boolean,
    replaceWithPrevious: Boolean,
    playlistCount: Int,
    playlistPosition: Int,
): ArrayList<RemoteAction> = arrayListOf(
    if (replaceWithPrevious) {
        createPipAction(
            context,
            android.R.drawable.ic_media_play
            "TODO",
            PIP_PREVIOUS,
            PIP_PREVIOUS,
            playlistPosition != 0,
        )
    } else {
        createPipAction(
            context,
            android.R.drawable.ic_media_play
            "TODO"10,
            PIP_SKIP,
            PIP_SKIP,
        )
    },
    if (isPaused) {
        createPipAction(
            context,
            android.R.drawable.ic_media_play
            "TODO",
            PIP_PLAY,
            PIP_PLAY,
        )
    } else {
        createPipAction(
            context,
            android.R.drawable.ic_media_play
            "TODO",
            PIP_PAUSE,
            PIP_PAUSE,
        )
    },
    createPipAction(
        context,
        android.R.drawable.ic_media_play
        "TODO",
        PIP_NEXT,
        PIP_NEXT,
        playlistPosition != playlistCount - 1,
    ),
)

fun createPipAction(
    context: Context,
    @DrawableRes icon: Int,
    titleRes: StringResource,
    requestCode: Int,
    controlType: Int,
    isEnabled: Boolean = true,
): RemoteAction {
    val action = RemoteAction(
        Icon.createWithResource(context, icon),
        context.stringResource(titleRes),
        context.stringResource(titleRes),
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(PIP_INTENTS_FILTER).putExtra(PIP_INTENT_ACTION, controlType).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        ),
    )
    action.isEnabled = isEnabled
    return action
}

const val PIP_INTENTS_FILTER = "pip_control"
const val PIP_INTENT_ACTION = "media_control"
const val PIP_PAUSE = 1
const val PIP_PLAY = 2
const val PIP_PREVIOUS = 3
const val PIP_NEXT = 4
const val PIP_SKIP = 5
