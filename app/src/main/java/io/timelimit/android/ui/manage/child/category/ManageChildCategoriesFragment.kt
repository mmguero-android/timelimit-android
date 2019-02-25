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
package io.timelimit.android.ui.manage.child.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.child.ManageChildFragmentArgs
import io.timelimit.android.ui.manage.child.ManageChildFragmentDirections
import io.timelimit.android.ui.manage.child.category.create.CreateCategoryDialogFragment
import kotlinx.android.synthetic.main.fragment_manage_child_categories.*

class ManageChildCategoriesFragment : Fragment() {
    companion object {
        fun newInstance(params: ManageChildFragmentArgs) = ManageChildCategoriesFragment().apply {
            arguments = params.toBundle()
        }
    }

    private val params: ManageChildFragmentArgs by lazy { ManageChildFragmentArgs.fromBundle(arguments!!) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val model: ManageChildCategoriesModel by lazy {
        ViewModelProviders.of(this).get(ManageChildCategoriesModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manage_child_categories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = Adapter()
        val navigation = Navigation.findNavController(view)

        adapter.handlers = object: Handlers {
            override fun onCategoryClicked(category: Category) {
                navigation.safeNavigate(
                        ManageChildFragmentDirections.actionManageChildFragmentToManageCategoryFragment(
                                params.childId,
                                category.id
                        ),
                        R.id.manageChildFragment
                )
            }

            override fun onCreateCategoryClicked() {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    CreateCategoryDialogFragment.newInstance(params)
                            .show(fragmentManager!!)
                }
            }
        }

        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(context)

        model.init(params.childId)
        model.listContent.observe(this, Observer { adapter.categories = it })

        ItemTouchHelper(object: ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (adapter.categories!![viewHolder.adapterPosition] == CategoriesIntroductionHeader) {
                    return makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.END) or
                            makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.END)
                } else {
                    return 0
                }
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = throw IllegalStateException()

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val database = logic.database

                Threads.database.submit {
                    database.config().setHintsShownSync(HintsToShow.CATEGORIES_INTRODUCTION)
                }
            }
        }).attachToRecyclerView(recycler)
    }
}
