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
package io.timelimit.android.ui.setup.device

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.model.AppRecommendation
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.FragmentSetupDeviceBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.manage.device.manage.advanced.ManageDeviceBackgroundSync
import io.timelimit.android.ui.mustread.MustReadFragment
import io.timelimit.android.ui.overview.main.MainFragmentDirections
import io.timelimit.android.ui.setup.SetupNetworkTimeVerification

class SetupDeviceFragment : Fragment() {
    companion object {
        private const val PAGE_READY = 0
        private const val PAGE_REQUIRE_AUTH = 1
        private const val PAGE_WORKING = 2
        const val NEW_PARENT = ":np"
        const val NEW_CHILD = ":nc"
        val NEW_USER = setOf(NEW_PARENT, NEW_CHILD)

        private const val STATUS_SELECTED_USER = "a"
        private const val STATUS_SELECTED_APPS_TO_NOT_WHITELIST = "b"
        private const val STATUS_ALLOWED_APPS_CATEGORY = "c"
    }

    private val selectedUser = MutableLiveData<String>()
    private val selectedAppsToNotWhitelist = mutableSetOf<String>()
    private var allowedAppsCategory = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            val oldSelectedUser = savedInstanceState.getString(STATUS_SELECTED_USER)

            if (oldSelectedUser != null) {
                selectedUser.value = oldSelectedUser
            }

            selectedAppsToNotWhitelist.clear()
            selectedAppsToNotWhitelist.addAll(
                    savedInstanceState.getStringArrayList(STATUS_SELECTED_APPS_TO_NOT_WHITELIST)!!
            )

            allowedAppsCategory = savedInstanceState.getString(STATUS_ALLOWED_APPS_CATEGORY)!!
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(STATUS_SELECTED_USER, selectedUser.value)
        outState.putStringArrayList(STATUS_SELECTED_APPS_TO_NOT_WHITELIST, ArrayList(selectedAppsToNotWhitelist))
        outState.putString(STATUS_ALLOWED_APPS_CATEGORY, allowedAppsCategory)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentSetupDeviceBinding.inflate(inflater, container, false)
        val logic = DefaultAppLogic.with(context!!)
        val activity = activity as ActivityViewModelHolder
        val model = ViewModelProviders.of(this).get(SetupDeviceModel::class.java)
        val navigation = Navigation.findNavController(container!!)

        binding.needsParent.authBtn.setOnClickListener {
            activity.showAuthenticationScreen()
        }

        val isParentAuthenticatedLive = activity.getActivityViewModel().authenticatedUser.map { it?.second?.type == UserType.Parent }

        binding.flipper.displayedChild = PAGE_WORKING
        mergeLiveData(isParentAuthenticatedLive, model.status).observe(this, Observer { (isParent, status) ->
            if (status == SetupDeviceModelStatus.Ready) {
                if (isParent == true) {
                    binding.flipper.displayedChild = PAGE_READY
                } else {
                    binding.flipper.displayedChild = PAGE_REQUIRE_AUTH
                }
            } else {
                binding.flipper.displayedChild = PAGE_WORKING

                when (status) {
                    SetupDeviceModelStatus.Done -> {
                        runAsync {
                            if (BuildConfig.storeCompilant) {
                                MustReadFragment.newInstance(R.string.must_read_child_manipulation).show(fragmentManager!!)
                            }

                            val ownDeviceId = logic.deviceId.waitForNullableValue()!!

                            navigation.popBackStack()
                            navigation.safeNavigate(
                                    MainFragmentDirections.actionOverviewFragmentToManageDeviceFragment(
                                            ownDeviceId
                                    ),
                                    R.id.overviewFragment
                            )
                        }
                    }
                    SetupDeviceModelStatus.Working -> { /* nothing to do */ }
                    null -> { /* nothing to do */ }
                    else -> throw IllegalStateException()
                }
            }
        })

        logic.database.user().getAllUsersLive().observe(this, Observer { users ->
            // ID to label
            val items = mutableListOf<Pair<String, String>>()

            // prepare the list content
            users.forEach { user ->
                items.add(user.id to user.name)
            }

            items.add(NEW_PARENT to getString(R.string.setup_device_new_parent))
            items.add(NEW_CHILD to getString(R.string.setup_device_new_child))

            // select the first item if nothing is selected currently
            if (items.find { (id) -> id == selectedUser.value } == null) {
                selectedUser.value = items.first().first
            }

            // build the views
            val views = items.map { (id, label) ->
                AppCompatRadioButton(context).apply {
                    setOnClickListener { selectedUser.value = id }
                    text = label
                    tag = id
                }
            }

            // apply the views
            binding.selectUserRadioGroup.removeAllViews()
            views.forEach { view -> binding.selectUserRadioGroup.addView(view) }
            views.find { it.tag == selectedUser.value }?.isChecked = true
        })

        val isNewUser = selectedUser.map { NEW_USER.contains(it) }
        val isParentUser = selectedUser.switchMap {
            if (it == NEW_CHILD) {
                liveDataFromValue(false)
            } else if (it == NEW_PARENT) {
                liveDataFromValue(true)
            } else {
                logic.database.user().getUserByIdLive(it).map {user ->
                    user?.type == UserType.Parent
                }
            }
        }

        isNewUser.observe(this, Observer { binding.isAddingNewUser = it })
        isParentUser.observe(this, Observer { binding.isAddingChild = !it })

        val categoriesOfTheSelectedUser = selectedUser.switchMap { user ->
            if (NEW_USER.contains(user)) {
                liveDataFromValue(emptyList())
            } else {
                logic.database.category().getCategoriesByChildId(user)
            }
        }

        val appsAssignedToTheUser = categoriesOfTheSelectedUser.switchMap { categories ->
            logic.database.categoryApp().getCategoryApps(categories.map { it.id }).map { categoryApps ->
                categoryApps.map { it.packageName }.toSet()
            }
        }

        val recommendWhitelistLocalApps = logic.platformIntegration.getLocalApps(IdGenerator.generateId()).filter { it.recommendation == AppRecommendation.Whitelist }

        val appsToWhitelist = appsAssignedToTheUser.map { assignedApps ->
            recommendWhitelistLocalApps.filterNot { app -> assignedApps.contains(app.packageName) }
        }

        appsToWhitelist.observe(this, Observer { apps ->
            binding.areThereAnyApps = apps.isNotEmpty()

            binding.suggestedAllowedApps.removeAllViews()

            apps.forEach { app ->
                binding.suggestedAllowedApps.addView(
                        CheckBox(context).apply {
                            text = app.title
                            isChecked = !selectedAppsToNotWhitelist.contains(app.packageName)

                            setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) {
                                    selectedAppsToNotWhitelist.remove(app.packageName)
                                } else {
                                    selectedAppsToNotWhitelist.add(app.packageName)
                                }
                            }
                        }
                )
            }
        })

        categoriesOfTheSelectedUser.observe(this, Observer { categories ->
            // id to title
            val items = mutableListOf<Pair<String, String>>()

            categories.forEach { category ->
                items.add(category.id to category.title)
            }

            if (items.isEmpty()) {
                allowedAppsCategory = ""
                binding.areThereAnyCategories = false
            } else {
                if (items.find { (id) -> id == allowedAppsCategory } == null) {
                    // use the one with the lowest blocked times
                    allowedAppsCategory = categories.sortedBy { it.blockedMinutesInWeek.dataNotToModify.cardinality() }.first().id
                }

                binding.areThereAnyCategories = true

                val views = items.map { (id, label) ->
                    AppCompatRadioButton(context!!).apply {
                        text = label
                        tag = id
                        setOnClickListener { allowedAppsCategory = id }
                    }
                }

                binding.allowedAppsCategory.removeAllViews()
                views.forEach { view -> binding.allowedAppsCategory.addView(view) }
                views.find { it.tag == allowedAppsCategory }?.isChecked = true
            }
        })

        val selectedName = MutableLiveData<String>().apply { value = binding.newUserName.text.toString() }
        binding.newUserName.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(p0: Editable?) = Unit
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                selectedName.value = binding.newUserName.text.toString()
            }
        })

        val hasSelectedName = selectedName.map { it.isNotBlank() }
        val isNameRequired = isNewUser
        val validationOfName = hasSelectedName.or(isNameRequired.invert())
        val isPasswordRequired = isNewUser.and(isParentUser)
        val isPasswordValid = binding.setPasswordView.passwordOk
        val isPasswordEmpty = binding.setPasswordView.passwordEmpty
        val validationOfPassword = isPasswordValid.or(
                isPasswordRequired.invert()
                        .and(isPasswordEmpty)
        )
        val validationOfAll = validationOfName.and(validationOfPassword)

        validationOfAll.observe(this, Observer { binding.confirmBtn.isEnabled = it })

        ManageDeviceBackgroundSync.bind(
                view = binding.backgroundSync,
                isThisDevice = liveDataFromValue(true),
                lifecycleOwner = this,
                activityViewModel = activity.getActivityViewModel()
        )

        binding.confirmBtn.setOnClickListener {
            model.doSetup(
                    userId = selectedUser.value!!,
                    username = binding.newUserName.text.toString(),
                    password = binding.setPasswordView.password.value!!,
                    allowedAppsCategory = allowedAppsCategory,
                    appsToNotWhitelist = selectedAppsToNotWhitelist,
                    model = activity.getActivityViewModel(),
                    networkTime = SetupNetworkTimeVerification.readSelection(binding.networkTimeVerification)
            )
        }

        return binding.root
    }
}
