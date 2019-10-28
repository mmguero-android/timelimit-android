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
package io.timelimit.android.ui.manage.category.apps.add

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.data.model.App
import io.timelimit.android.databinding.FragmentAddCategoryAppsItemBinding
import io.timelimit.android.logic.DefaultAppLogic
import kotlin.properties.Delegates

class AddAppAdapter: RecyclerView.Adapter<ViewHolder>() {
    var data: List<App>? by Delegates.observable(null as List<App>?) { _, _, _ -> notifyDataSetChanged() }
    var listener: AddAppAdapterListener? = null
    var categoryTitleByPackageName: Map<String, String> by Delegates.observable(emptyMap()) { _, _, _ -> notifyDataSetChanged() }
    var selectedApps: Set<String> by Delegates.observable(emptySet()) { _, _, _ -> notifyDataSetChanged() }

    init {
        setHasStableIds(true)
    }

    private fun getItem(position: Int): App {
        return data!![position]
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).hashCode().toLong()
    }

    override fun getItemCount(): Int {
        val data = this.data

        if (data == null) {
            return 0
        } else {
            return data.size
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            FragmentAddCategoryAppsItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
            )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.apply {
            binding.item = item
            binding.checked = selectedApps.contains(item.packageName)
            binding.currentCategoryTitle = categoryTitleByPackageName[item.packageName]
            binding.handlers = listener
            binding.executePendingBindings()

            binding.icon.setImageDrawable(
                    DefaultAppLogic.with(holder.itemView.context)
                            .platformIntegration.getAppIcon(item.packageName)
            )
        }
    }
}

class ViewHolder(val binding: FragmentAddCategoryAppsItemBinding): RecyclerView.ViewHolder(binding.root)

interface AddAppAdapterListener {
    fun onAppClicked(app: App)
    fun onAppLongClicked(app: App): Boolean
}