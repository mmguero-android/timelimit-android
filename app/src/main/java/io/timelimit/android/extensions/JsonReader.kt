package io.timelimit.android.extensions

import android.util.JsonReader

fun <T> JsonReader.parseList(parseItem: (JsonReader) -> T): List<T> {
    val result = mutableListOf<T>()

    this.beginArray()
    while (this.hasNext()) {
        result.add(parseItem(this))
    }
    this.endArray()

    return result
}