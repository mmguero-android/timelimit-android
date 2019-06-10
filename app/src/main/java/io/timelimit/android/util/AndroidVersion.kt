package io.timelimit.android.util

import android.os.Build

object AndroidVersion {
    val qOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}