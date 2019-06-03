package io.timelimit.android.util

import android.os.Build

object AndroidVersion {
    // TODO: replace this by the correct constant
    val qOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P + 1
}