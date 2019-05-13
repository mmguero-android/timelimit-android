package io.timelimit.android.ui.manage.device.manage.feature

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.data.model.Device
import io.timelimit.android.databinding.ManageDeviceActivityLevelBlockingBinding
import io.timelimit.android.sync.actions.UpdateEnableActivityLevelBlocking
import io.timelimit.android.ui.main.ActivityViewModel

object ManageDeviceActivityLevelBlocking {
    fun bind(
            view: ManageDeviceActivityLevelBlockingBinding,
            auth: ActivityViewModel,
            deviceEntry: LiveData<Device?>,
            lifecycleOwner: LifecycleOwner
    ) {
        deviceEntry.observe(lifecycleOwner, Observer { device ->
            val enable = device?.enableActivityLevelBlocking ?: false

            view.checkbox.setOnCheckedChangeListener { _, _ ->  }
            view.checkbox.isChecked = enable
            view.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != enable) {
                    if (
                            device == null ||
                            (!auth.tryDispatchParentAction(
                                    UpdateEnableActivityLevelBlocking(
                                            deviceId = device.id,
                                            enable = isChecked
                                    )
                            ))
                    ) {
                        view.checkbox.isChecked = enable
                    }
                }
            }
        })
    }
}