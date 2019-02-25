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
package io.timelimit.android.ui.manage.parent.link

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.databinding.LinkParentMailFragmentBinding
import io.timelimit.android.ui.authentication.AuthenticateByMailFragment
import io.timelimit.android.ui.authentication.AuthenticateByMailFragmentListener

class LinkParentMailFragment : Fragment(), AuthenticateByMailFragmentListener {
    companion object {
        private const val PAGE_READY = 0
        private const val PAGE_LOGIN = 1
        private const val PAGE_BUSY = 2
        private const val PAGE_ALREADY_LINKED = 3
    }

    val model: LinkParentMailViewModel by lazy {
        ViewModelProviders.of(this).get(LinkParentMailViewModel::class.java)
    }

    val args: LinkParentMailFragmentArgs by lazy {
        LinkParentMailFragmentArgs.fromBundle(arguments!!)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = LinkParentMailFragmentBinding.inflate(inflater, container, false)
        val navigation = Navigation.findNavController(container!!)

        model.status.observe(this, Observer {
            status ->

            when (status!!) {
                LinkParentMailViewModelStatus.WaitForAuthentication -> binding.flipper.displayedChild = PAGE_LOGIN
                LinkParentMailViewModelStatus.WaitForConfirmationWithPassword -> binding.flipper.displayedChild = PAGE_READY
                LinkParentMailViewModelStatus.ShouldLeaveScreen -> {
                    navigation.popBackStack()

                    null
                }
                LinkParentMailViewModelStatus.Working -> binding.flipper.displayedChild = PAGE_BUSY
                LinkParentMailViewModelStatus.AlreadyLinked -> binding.flipper.displayedChild = PAGE_ALREADY_LINKED
            }.let {  }
        })

        model.mailAddress.observe(this, Observer {
            mailAddress ->

            binding.mailAddress = mailAddress
            binding.alreadyLinkedView.mailAddress = mailAddress
        })

        binding.linkButton.setOnClickListener {
            model.doLinking(
                    password = binding.userPasswordField.text.toString(),
                    parentUserId = args.parentId
            )
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                    .replace(R.id.mail_auth_container, AuthenticateByMailFragment())
                    .commit()
        }
    }

    override fun onLoginSucceeded(mailAuthToken: String) {
        model.setMailAuthToken(mailAuthToken)
    }
}
