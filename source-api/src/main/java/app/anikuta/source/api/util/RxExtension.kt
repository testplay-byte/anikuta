package app.anikuta.source.api.util

import rx.Observable
import app.anikuta.core.util.lang.awaitSingle

suspend fun <T> Observable<T>.awaitSingle(): T = awaitSingle()
