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
package io.timelimit.android.ui.login

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.data.model.User
import io.timelimit.android.ui.list.TextViewHolder
import kotlin.properties.Delegates

class LoginUserAdapter : RecyclerView.Adapter<TextViewHolder>() {
    var data: List<User>? by Delegates.observable(null as List<User>?) { _, _, _ -> notifyDataSetChanged() }
    var listener: LoginUserAdapterListener? by Delegates.observable(null as LoginUserAdapterListener?) { _, _, _ -> notifyDataSetChanged() }

    init {
        setHasStableIds(true)
    }

    fun getItem(position: Int): User {
        return data!![position]
    }

    override fun getItemCount(): Int {
        val data = this.data

        if (data == null) {
            return 0
        } else {
            return data.size
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder {
        return TextViewHolder(parent)
    }

    override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
        val item = getItem(position)
        val listener = this.listener

        holder.textView.text = item.name

        if (listener !=null) {
            holder.textView.setOnClickListener { listener.onUserClicked(item) }
        } else {
            holder.textView.setOnClickListener(null)
        }
    }
}

interface LoginUserAdapterListener {
    fun onUserClicked(user: User)
}
