/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.User
import io.timelimit.android.databinding.NewLoginFragmentBinding
import io.timelimit.android.extensions.setOnEnterListenr
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.parent.key.ScannedKey
import io.timelimit.android.ui.view.KeyboardViewListener

class NewLoginFragment: DialogFragment() {
    companion object {
        const val SHOW_ON_LOCKSCREEN = "showOnLockscreen"

        private const val SELECTED_USER_ID = "selectedUserId"
        private const val USER_LIST = 0
        private const val PARENT_AUTH = 1
        private const val CHILD_MISSING_PASSWORD = 2
        private const val CHILD_ALREADY_CURRENT_USER = 3
        private const val CHILD_AUTH = 4
        private const val CHILD_LOGIN_REQUIRES_PREMIUM = 5
        private const val BLOCKED_LOGIN_TIME = 6
        private const val UNVERIFIED_TIME = 7
    }

    private val model: LoginDialogFragmentModel by lazy {
        ViewModelProviders.of(this).get(LoginDialogFragmentModel::class.java)
    }
    private val inputMethodManager: InputMethodManager by lazy {
        context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState?.containsKey(SELECTED_USER_ID) == true) {
            model.selectedUserId.value = savedInstanceState.getString(SELECTED_USER_ID)
        }

        if (savedInstanceState == null) {
            model.tryDefaultLogin(getActivityViewModel(activity!!))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = object: BottomSheetDialog(context!!, theme) {
        override fun onBackPressed() {
            if (!model.goBack()) {
                super.onBackPressed()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                if (arguments?.getBoolean(SHOW_ON_LOCKSCREEN, false) == true) {
                    window!!.addFlags(
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        model.selectedUserId.value?.let { outState.putString(SELECTED_USER_ID, it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = NewLoginFragmentBinding.inflate(inflater, container, false)

        val adapter = LoginUserAdapter()

        adapter.listener = object: LoginUserAdapterListener {
            override fun onUserClicked(user: User) {
                // reset parent password view
                binding.enterPassword.checkAssignMyself.isChecked = false
                binding.enterPassword.checkDontAskAgain.isChecked = false
                binding.enterPassword.password.setText("")

                // go to the next step
                model.startSignIn(user)
            }

            override fun onScanCodeRequested() {
                CodeLoginDialogFragment().apply {
                    setTargetFragment(this@NewLoginFragment, 0)
                }.show(parentFragmentManager)
            }
        }

        binding.userList.recycler.adapter = adapter
        binding.userList.recycler.layoutManager = LinearLayoutManager(context)

        binding.enterPassword.apply {
            showKeyboardButton.setOnClickListener {
                showCustomKeyboard = !showCustomKeyboard

                if (showCustomKeyboard) {
                    inputMethodManager.hideSoftInputFromWindow(password.windowToken, 0)
                } else {
                    inputMethodManager.showSoftInput(password, 0)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    password.showSoftInputOnFocus = !showCustomKeyboard
                }
            }

            fun go() {
                model.tryParentLogin(
                        password = password.text.toString(),
                        keepSignedIn = checkDontAskAgain.isChecked,
                        model = getActivityViewModel(activity!!),
                        setAsDeviceUser = checkAssignMyself.isChecked
                )
            }

            keyboard.listener = object: KeyboardViewListener {
                override fun onItemClicked(content: String) {
                    val start = Math.max(password.selectionStart, 0)
                    val end = Math.max(password.selectionEnd, 0)

                    password.text.replace(Math.min(start, end), Math.max(start, end), content, 0, content.length)
                }

                override fun onGoClicked() {
                    go()
                }
            }

            password.setOnEnterListenr { go() }
        }

        binding.childPassword.apply {
            password.setOnEnterListenr {
                model.tryChildLogin(
                        password = password.text.toString(),
                        model = getActivityViewModel(activity!!)
                )
            }
        }

        model.status.observe(viewLifecycleOwner, Observer { status ->
            when (status) {
                LoginDialogDone -> {
                    dismissAllowingStateLoss()
                }
                is UserListLoginDialogStatus -> {
                    if (binding.switcher.displayedChild != USER_LIST) {
                        binding.switcher.setInAnimation(context!!, R.anim.wizard_close_step_in)
                        binding.switcher.setOutAnimation(context!!, R.anim.wizard_close_step_out)
                        binding.switcher.displayedChild = USER_LIST
                    }

                    val users = status.usersToShow.map { LoginUserAdapterUser(it) }

                    adapter.data =  if (status.isLocalMode)
                        users + LoginUserAdapterScan
                    else
                        users

                    Threads.mainThreadHandler.post { binding.userList.recycler.requestFocus() }

                    null
                }
                is ParentUserLogin -> {
                    if (binding.switcher.displayedChild != PARENT_AUTH) {
                        binding.switcher.setInAnimation(context!!, R.anim.wizard_open_step_in)
                        binding.switcher.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                        binding.switcher.displayedChild = PARENT_AUTH
                    }

                    binding.enterPassword.password.isEnabled = !status.isCheckingPassword

                    if (!binding.enterPassword.showCustomKeyboard) {
                        binding.enterPassword.password.requestFocus()
                        inputMethodManager.showSoftInput(binding.enterPassword.password, 0)
                    }

                    binding.enterPassword.showKeepLoggedInOption = status.isConnectedMode

                    if (status.isAlreadyCurrentDeviceUser) {
                        binding.enterPassword.canNotKeepLoggedIn = false

                        binding.enterPassword.checkAssignMyself.setOnCheckedChangeListener { _, _ ->  }
                        binding.enterPassword.checkAssignMyself.isEnabled = false
                        binding.enterPassword.checkAssignMyself.isChecked = true
                    } else {
                        binding.enterPassword.checkAssignMyself.isEnabled = true

                        fun bindCanNotKeepLoggedIn() {
                            binding.enterPassword.canNotKeepLoggedIn = !binding.enterPassword.checkAssignMyself.isChecked
                        }

                        bindCanNotKeepLoggedIn()
                        binding.enterPassword.checkAssignMyself.setOnCheckedChangeListener { _, _ -> bindCanNotKeepLoggedIn() }
                    }

                    if (status.wasPasswordWrong) {
                        Toast.makeText(context!!, R.string.login_snackbar_wrong, Toast.LENGTH_SHORT).show()
                        binding.enterPassword.password.setText("")

                        model.resetPasswordWrong()
                    }

                    null
                }
                ParentUserLoginMissingTrustedTime -> {
                    if (binding.switcher.displayedChild != UNVERIFIED_TIME) {
                        binding.switcher.setInAnimation(context!!, R.anim.wizard_open_step_in)
                        binding.switcher.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                        binding.switcher.displayedChild = UNVERIFIED_TIME
                    }

                    null
                }
                ParentUserLoginBlockedTime -> {
                    if (binding.switcher.displayedChild != BLOCKED_LOGIN_TIME) {
                        binding.switcher.setInAnimation(context!!, R.anim.wizard_open_step_in)
                        binding.switcher.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                        binding.switcher.displayedChild = BLOCKED_LOGIN_TIME
                    }

                    null
                }
                is CanNotSignInChildHasNoPassword -> {
                    if (binding.switcher.displayedChild != CHILD_MISSING_PASSWORD) {
                        binding.switcher.setInAnimation(context!!, R.anim.wizard_open_step_in)
                        binding.switcher.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                        binding.switcher.displayedChild = CHILD_MISSING_PASSWORD
                    }

                    binding.childWithoutPassword.childName = status.childName

                    null
                }
                is ChildAlreadyDeviceUser -> {
                    if (binding.switcher.displayedChild != CHILD_ALREADY_CURRENT_USER) {
                        binding.switcher.setInAnimation(context!!, R.anim.wizard_open_step_in)
                        binding.switcher.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                        binding.switcher.displayedChild = CHILD_ALREADY_CURRENT_USER
                    }

                    null
                }
                is ChildUserLogin -> {
                    if (binding.switcher.displayedChild != CHILD_AUTH) {
                        binding.switcher.setInAnimation(context!!, R.anim.wizard_open_step_in)
                        binding.switcher.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                        binding.switcher.displayedChild = CHILD_AUTH
                    }

                    binding.childPassword.password.requestFocus()
                    inputMethodManager.showSoftInput(binding.childPassword.password, 0)

                    binding.childPassword.password.isEnabled = !status.isCheckingPassword

                    if (status.wasPasswordWrong) {
                        Toast.makeText(context!!, R.string.login_snackbar_wrong, Toast.LENGTH_SHORT).show()
                        binding.childPassword.password.setText("")

                        model.resetPasswordWrong()
                    }

                    null
                }
                ChildLoginRequiresPremiumStatus -> {
                    if (binding.switcher.displayedChild != CHILD_LOGIN_REQUIRES_PREMIUM) {
                        binding.switcher.setInAnimation(context!!, R.anim.wizard_open_step_in)
                        binding.switcher.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                        binding.switcher.displayedChild = CHILD_LOGIN_REQUIRES_PREMIUM
                    }

                    null
                }
            }.let { /* require handling all cases */ }
        })

        return binding.root
    }

    fun tryCodeLogin(code: ScannedKey) {
        model.tryCodeLogin(code, getActivityViewModel(activity!!))
    }
}
