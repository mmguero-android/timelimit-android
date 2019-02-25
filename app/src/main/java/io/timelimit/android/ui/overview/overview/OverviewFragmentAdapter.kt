/*
 * TimeLimit Copyright <C> 2019 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.android.ui.overview.overview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.*
import kotlin.properties.Delegates

class OverviewFragmentAdapter : RecyclerView.Adapter<OverviewFragmentViewHolder>() {
    init {
        setHasStableIds(true)
    }

    var data: List<OverviewFragmentItem>? by Delegates.observable(null as List<OverviewFragmentItem>?) { _, _, _ -> notifyDataSetChanged() }
    var handlers: OverviewFragmentHandlers? = null

    fun getItem(index: Int): OverviewFragmentItem {
        return data!![index]
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)

        return when (item) {
            is OverviewFragmentItemDevice -> "device ${item.device.id}".hashCode().toLong()
            is OverviewFragmentItemUser -> "user ${item.user.id}".hashCode().toLong()
            else -> item.hashCode().toLong()
        }
    }

    override fun getItemCount(): Int {
        val data = this.data

        if (data == null) {
            return 0
        } else {
            return data.size
        }
    }

    private fun getItemType(item: OverviewFragmentItem): OverviewFragmentViewType = when(item) {
        is OverviewFragmentHeaderUsers -> OverviewFragmentViewType.Header
        is OverviewFragmentHeaderDevices -> OverviewFragmentViewType.Header
        is OverviewFragmentItemUser -> OverviewFragmentViewType.UserItem
        is OverviewFragmentItemDevice -> OverviewFragmentViewType.DeviceItem
        is OverviewFragmentActionAddUser -> OverviewFragmentViewType.AddUserItem
        is OverviewFragmentActionAddDevice -> OverviewFragmentViewType.AddDeviceItem
        is OverviewFragmentHeaderIntro -> OverviewFragmentViewType.Introduction
        is OverviewFragmentHeaderFinishSetup -> OverviewFragmentViewType.FinishSetup
        is OverviewFragmentItemMessage -> OverviewFragmentViewType.ServerMessage
    }

    override fun getItemViewType(position: Int) = getItemType(getItem(position)).ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when(viewType) {
        OverviewFragmentViewType.Header.ordinal ->
            HeaderViewHolder(
                    GenericListHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )

        OverviewFragmentViewType.UserItem.ordinal ->
            UserViewHolder(
                    FragmentOverviewUserItemBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    )
            )

        OverviewFragmentViewType.DeviceItem.ordinal ->
            DeviceViewHolder(
                    FragmentOverviewDeviceItemBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    )
            )

        OverviewFragmentViewType.AddUserItem.ordinal ->
            AddUserViewHolder(
                    AddItemViewBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    ).apply {
                        label = parent.context.getString(R.string.add_user_title)

                        root.setOnClickListener {
                            handlers?.onAddUserClicked()
                        }
                    }.root
            )

        OverviewFragmentViewType.AddDeviceItem.ordinal -> AddDeviceViewHolder(
                AddItemViewBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ).apply {
                    label = parent.context.getString(R.string.overview_add_device)

                    root.setOnClickListener {
                        handlers?.onAddDeviceClicked()
                    }
                }.root
        )

        OverviewFragmentViewType.Introduction.ordinal -> IntroViewHolder(
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.fragment_overview_intro, parent, false)
        )

        OverviewFragmentViewType.FinishSetup.ordinal -> FinishSetupViewHolder(
                FragmentOverviewFinishSetupBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ).apply {
                    btnGo.setOnClickListener { handlers?.onFinishSetupClicked() }
                }.root
        )

        OverviewFragmentViewType.ServerMessage.ordinal -> ServerMessageViewHolder(
                FragmentOverviewServerMessageBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                )
        )

        else -> throw IllegalStateException()
    }

    override fun onBindViewHolder(holder: OverviewFragmentViewHolder, position: Int) {
        val item = getItem(position)

        when (item) {
            is OverviewFragmentHeaderUsers -> {
                if (holder !is HeaderViewHolder) {
                    throw IllegalStateException()
                }

                holder.header.text = holder.itemView.context.getString(R.string.overview_header_users)
                holder.header.executePendingBindings()
            }
            is OverviewFragmentHeaderDevices -> {
                if (holder !is HeaderViewHolder) {
                    throw IllegalStateException()
                }

                holder.header.text = holder.itemView.context.getString(R.string.overview_header_devices)
                holder.header.executePendingBindings()
            }
            is OverviewFragmentItemUser -> {
                if (holder !is UserViewHolder) {
                    throw IllegalStateException()
                }

                val binding = holder.binding

                binding.username = item.user.name
                binding.areTimeLimitsDisabled = item.limitsTemporarilyDisabled
                binding.isTemporarilyBlocked = item.temporarilyBlocked
                binding.isParent = item.user.type == UserType.Parent
                binding.isChild = item.user.type == UserType.Child

                binding.card.setOnClickListener {
                    this.handlers?.onUserClicked(item.user)
                }

                binding.executePendingBindings()
            }
            is OverviewFragmentItemDevice -> {
                if (holder !is DeviceViewHolder) {
                    throw IllegalStateException()
                }

                val binding = holder.binding

                binding.deviceTitle = item.device.name
                binding.currentDeviceUserTitle = item.deviceUser?.name
                binding.hasManipulation = item.device.hasAnyManipulation
                binding.isCurrentDevice = item.isCurrentDevice
                binding.isMissingRequiredPermission = item.isMissingRequiredPermission
                binding.didUninstall = item.device.didReportUninstall
                binding.isPasswordDisabled = item.device.isUserKeptSignedIn
                binding.isConnected = item.isConnected
                binding.isUsingOlderVersion = item.device.currentAppVersion < BuildConfig.VERSION_CODE
                binding.executePendingBindings()

                binding.card.setOnClickListener {
                    this.handlers?.onDeviceClicked(item.device)
                }
            }
            is OverviewFragmentActionAddUser -> { /* nothing to do */ }
            is OverviewFragmentActionAddDevice-> { /* nothing to do */ }
            is OverviewFragmentHeaderIntro -> { /* nothing to do */ }
            is OverviewFragmentHeaderFinishSetup -> { /* nothing to do */ }
            is OverviewFragmentItemMessage -> {
                holder as ServerMessageViewHolder

                holder.binding.text = item.message
                holder.binding.executePendingBindings()
            }
        }.let {  }
    }
}

enum class OverviewFragmentViewType {
    Header,
    UserItem,
    DeviceItem,
    AddUserItem,
    AddDeviceItem,
    Introduction,
    FinishSetup,
    ServerMessage
}

sealed class OverviewFragmentViewHolder(view: View): RecyclerView.ViewHolder(view)
class HeaderViewHolder(val header: GenericListHeaderBinding): OverviewFragmentViewHolder(header.root)
class AddUserViewHolder(view: View): OverviewFragmentViewHolder(view)
class UserViewHolder(val binding: FragmentOverviewUserItemBinding): OverviewFragmentViewHolder(binding.root)
class DeviceViewHolder(val binding: FragmentOverviewDeviceItemBinding): OverviewFragmentViewHolder(binding.root)
class AddDeviceViewHolder(view: View): OverviewFragmentViewHolder(view)
class IntroViewHolder(view: View): OverviewFragmentViewHolder(view)
class FinishSetupViewHolder(view: View): OverviewFragmentViewHolder(view)
class ServerMessageViewHolder(val binding: FragmentOverviewServerMessageBinding): OverviewFragmentViewHolder(binding.root)

interface OverviewFragmentHandlers {
    fun onAddUserClicked()
    fun onAddDeviceClicked()
    fun onUserClicked(user: User)
    fun onDeviceClicked(device: Device)
    fun onFinishSetupClicked()
}
