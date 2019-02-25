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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.Database
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.sync.actions.RemoveCategoryAppsAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import io.timelimit.android.ui.manage.category.apps.add.AddCategoryAppsFragment
import kotlinx.android.synthetic.main.fragment_category_apps.*

class CategoryAppsFragment : Fragment() {
    companion object {
        fun newInstance(params: ManageCategoryFragmentArgs): CategoryAppsFragment = CategoryAppsFragment().apply {
            arguments = params.toBundle()
        }
    }

    private val params: ManageCategoryFragmentArgs by lazy { ManageCategoryFragmentArgs.fromBundle(arguments!!) }
    private val database: Database by lazy { DefaultAppLogic.with(context!!).database }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }
    private val model: CategoryAppsModel by lazy {
        ViewModelProviders.of(this).get(CategoryAppsModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_category_apps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = AppAdapter()

        adapter.handlers = object: Handlers {
            override fun onAppClicked(app: AppEntry) {
                if (auth.tryDispatchParentAction(
                        RemoveCategoryAppsAction(
                                categoryId = params.categoryId,
                                packageNames = listOf(app.packageName)
                        )
                )) {
                    Snackbar.make(view, getString(R.string.category_apps_item_removed_toast, app.title), Snackbar.LENGTH_SHORT)
                            .setAction(R.string.generic_undo) {
                                auth.tryDispatchParentAction(
                                        AddCategoryAppsAction(
                                                categoryId = params.categoryId,
                                                packageNames = listOf(app.packageName)
                                        )
                                )
                            }
                            .show()
                }
            }

            override fun onAddAppsClicked() {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    AddCategoryAppsFragment.newInstance(params).show(fragmentManager!!)
                }
            }
        }

        recycler.layoutManager = LinearLayoutManager(context!!)
        recycler.adapter = adapter

        model.init(params)
        model.appEntries.observe(this, Observer { adapter.data = it })
    }
}
