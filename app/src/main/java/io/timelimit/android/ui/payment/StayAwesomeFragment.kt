/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
package io.timelimit.android.ui.payment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.databinding.StayAwesomeFragmentBinding
import io.timelimit.android.databinding.StayAwesomeFragmentItemBinding
import io.timelimit.android.livedata.liveDataFromNullableValue
import io.timelimit.android.ui.MainActivity
import io.timelimit.android.ui.main.FragmentWithCustomTitle

class StayAwesomeFragment : Fragment(), FragmentWithCustomTitle {
    private val model: StayAwesomeModel by viewModels()
    private val activityModel get() = (activity as MainActivity).purchaseModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = StayAwesomeFragmentBinding.inflate(inflater, container, false)

        model.status.observe(viewLifecycleOwner, Observer { status ->
            when (status!!) {
                LoadingStayAwesomeStatus -> binding.flipper.displayedChild = 0
                NotSupportedByDeviceStayAwesomeStatus -> binding.flipper.displayedChild = 1
                NotSupportedByAppStayAwesomeStatus -> binding.flipper.displayedChild = 2
                is ReadyStayAwesomeStatus -> {
                    status as ReadyStayAwesomeStatus

                    binding.list.removeAllViews()

                    status.items.forEach { item ->
                        val view = StayAwesomeFragmentItemBinding.inflate(
                                LayoutInflater.from(context),
                                binding.list,
                                false
                        )

                        view.title = item.title
                        view.price = item.price
                        view.isBought = item.bought

                        if (!item.bought) {
                            view.card.setOnClickListener {
                                activityModel.startPurchase(item.id, checkAtBackend = false, requireActivity())
                            }
                        }

                        binding.list.addView(view.root)
                    }

                    binding.flipper.displayedChild = 3
                }
            }.let {/* require handling all paths */}

            binding.info.visibility = if (status is ReadyStayAwesomeStatus) View.VISIBLE else View.GONE
        })

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        model.load(activityModel)
    }

    override fun getCustomTitle(): LiveData<String?> = liveDataFromNullableValue("${getString(R.string.about_sal)} < ${getString(R.string.main_tab_overview)}")
}
