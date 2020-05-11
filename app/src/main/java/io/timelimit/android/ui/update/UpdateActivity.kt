package io.timelimit.android.ui.update

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import io.timelimit.android.R
import io.timelimit.android.databinding.UpdateActivityBinding
import io.timelimit.android.logic.DefaultAppLogic

class UpdateActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = DataBindingUtil.setContentView<UpdateActivityBinding>(this, R.layout.update_activity)

        UpdateView.bind(
                view = binding.update,
                lifecycleOwner = this,
                fragmentManager = supportFragmentManager,
                appLogic = DefaultAppLogic.with(this)
        )
    }
}