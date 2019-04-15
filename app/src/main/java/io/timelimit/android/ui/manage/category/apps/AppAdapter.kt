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
package io.timelimit.android.ui.manage.category.apps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.databinding.AddItemViewBinding
import io.timelimit.android.databinding.FragmentCategoryAppsItemBinding
import io.timelimit.android.logic.DefaultAppLogic
import kotlin.properties.Delegates

class AppAdapter: RecyclerView.Adapter<ViewHolder>() {
    companion object {
        private const val TYPE_ITEM = 1
        private const val TYPE_ADD = 2
    }

    var data: List<AppEntry>? by Delegates.observable(null as List<AppEntry>?) { _, _, _ -> notifyDataSetChanged() }
    var handlers: Handlers? by Delegates.observable(null as Handlers?) { _, _, _ -> notifyDataSetChanged() }

    init {
        setHasStableIds(true)
    }

    private fun getItem(position: Int): AppEntry {
        return data!![position]
    }

    override fun getItemId(position: Int) = when {
        position == data!!.size -> 1
        else -> getItem(position).hashCode().toLong()
    }

    override fun getItemCount(): Int {
        val data = this.data

        if (data == null) {
            return 0
        } else {
            return data.size + 1
        }
    }

    override fun getItemViewType(position: Int) = when {
        position == data!!.size -> TYPE_ADD
        else -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
        TYPE_ADD -> {
            ViewHolder(
                    AddItemViewBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    ).apply {
                        label = parent.context.getString(R.string.category_apps_add_dialog_btn_positive)
                        wide = true

                        root.setOnClickListener { handlers?.onAddAppsClicked() }
                    }.root
            )
        }
        TYPE_ITEM -> {
            AppViewHolder(
                    FragmentCategoryAppsItemBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    )
            )
        }
        else -> throw IllegalStateException()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position == data!!.size) {
            // nothing to do
        } else {
            val item = getItem(position)
            val binding = (holder as AppViewHolder).binding

            binding.item = item
            binding.handlers = handlers
            binding.executePendingBindings()

            // TODO: bind icon more modular
            binding.icon.setImageDrawable(
                    DefaultAppLogic.with(binding.root.context)
                            .platformIntegration.getAppIcon(item.packageNameWithoutActivityName)
            )
        }
    }
}

open class ViewHolder(view: View): RecyclerView.ViewHolder(view)
class AppViewHolder(val binding: FragmentCategoryAppsItemBinding): ViewHolder(binding.root)

data class AppEntry(val title: String, val packageName: String, val packageNameWithoutActivityName: String)

interface Handlers {
    fun onAppClicked(app: AppEntry)
    fun onAddAppsClicked()
}
