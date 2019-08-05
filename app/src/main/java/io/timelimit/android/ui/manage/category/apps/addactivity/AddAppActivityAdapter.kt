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
package io.timelimit.android.ui.manage.category.apps.addactivity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.data.model.AppActivity
import io.timelimit.android.databinding.FragmentAddCategoryActivitiesItemBinding
import io.timelimit.android.extensions.toggle
import kotlin.properties.Delegates

class AddAppActivityAdapter: RecyclerView.Adapter<ViewHolder>() {
    var data: List<AppActivity>? by Delegates.observable(null as List<AppActivity>?) { _, _, _ -> notifyDataSetChanged() }
    val selectedActiviities = mutableSetOf<String>()

    private val itemHandlers = object: ItemHandlers {
        override fun onActivityClicked(activity: AppActivity) {
            selectedActiviities.toggle(activity.activityClassName)

            notifyDataSetChanged()
        }
    }

    init {
        setHasStableIds(true)
    }

    private fun getItem(position: Int): AppActivity {
        return data!![position]
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).activityClassName.hashCode().toLong()
    }

    override fun getItemCount(): Int = this.data?.size ?: 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            FragmentAddCategoryActivitiesItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
            ).apply { handlers = itemHandlers }
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.apply {
            binding.item = item
            binding.checked = selectedActiviities.contains(item.activityClassName)
            binding.executePendingBindings()
        }
    }
}

class ViewHolder(val binding: FragmentAddCategoryActivitiesItemBinding): RecyclerView.ViewHolder(binding.root)

interface ItemHandlers {
    fun onActivityClicked(activity: AppActivity)
}