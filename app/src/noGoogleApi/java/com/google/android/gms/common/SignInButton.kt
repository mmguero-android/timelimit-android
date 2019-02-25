package com.google.android.gms.common

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import io.timelimit.android.R

class SignInButton(context: Context, attributeSet: AttributeSet): Button(context, attributeSet) {
    init {
        setText(R.string.authenticate_by_mail_btn_google)
        isEnabled = false
    }
}
