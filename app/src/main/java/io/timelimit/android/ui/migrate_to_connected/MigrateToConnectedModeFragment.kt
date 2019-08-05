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
package io.timelimit.android.ui.migrate_to_connected

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.MigrateToConnectedModeFragmentBinding
import io.timelimit.android.ui.authentication.AuthenticateByMailFragment
import io.timelimit.android.ui.authentication.AuthenticateByMailFragmentListener
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.getActivityViewModel

class MigrateToConnectedModeFragment : Fragment(), AuthenticateByMailFragmentListener {
    companion object {
        private const val PAGE_READY = 0
        private const val PAGE_AUTH = 1
        private const val PAGE_WORKING = 2
        private const val PAGE_EXISTING_ACCOUNT = 3
        private const val PAGE_DONE = 4
    }

    private val model: MigrateToConnectedModeModel by lazy {
        ViewModelProviders.of(this).get(MigrateToConnectedModeModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? { // Inflate the layout for this fragment
        val binding = MigrateToConnectedModeFragmentBinding.inflate(inflater, container, false)
        val navigation = Navigation.findNavController(container!!)
        val auth = getActivityViewModel(activity!!)

        auth.authenticatedUser.observe(this, Observer {
            binding.isParentSignedIn = it?.second?.type == UserType.Parent
        })

        binding.parentAuthButton.setOnClickListener {
            (activity as ActivityViewModelHolder).showAuthenticationScreen()
        }

        binding.goButton.setOnClickListener {
            var name = binding.parentName.text.toString()

            if (name.isBlank())
                name = getString(R.string.setup_username_parent)

            model.doMigration(
                    model = auth,
                    parentFirstName = name
            )
        }

        model.status.observe(this, Observer { status ->
            when (status) {
                LeaveScreenMigrationStatus -> {
                    navigation.popBackStack()

                    null
                }
                WaitingForAuthMigrationStatus -> binding.flipper.displayedChild = PAGE_AUTH
                WaitingForConfirmationByParentMigrationStatus -> binding.flipper.displayedChild = PAGE_READY
                WorkingMigrationStatus -> binding.flipper.displayedChild = PAGE_WORKING
                DoneMigrationStatus -> binding.flipper.displayedChild = PAGE_DONE
                ConflictAlreadyHasAccountMigrationStatus -> binding.flipper.displayedChild = PAGE_EXISTING_ACCOUNT
            }.let { /* require handling all cases */ }
        })

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                    .replace(R.id.mail_auth_container, AuthenticateByMailFragment.newInstance(
                            hideSignInWithGoogleButton = true
                    ))
                    .commit()
        }
    }

    override fun onLoginSucceeded(mailAuthToken: String) {
        model.onLoginSucceeded(mailAuthToken)
    }
}
