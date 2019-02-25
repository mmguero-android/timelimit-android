package io.timelimit.android.flavors

import android.app.Activity
import android.content.Intent
import io.timelimit.android.R

class GoogleSignInUtil(activity: Activity) {
    fun getSignInIntent(): Intent {
        throw UnsupportedOperationException()
    }

    fun processActivityResult(data: Intent?): String? {
        return null
    }
}