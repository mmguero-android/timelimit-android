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
package io.timelimit.android.ui.manage.parent.key

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.timelimit.android.R

abstract class ScanKeyDialogFragment: DialogFragment() {
    companion object {
        private const val CAN_NOT_SCAN = "canNotScan"
        private const val REQ_SCAN = 1
    }

    private var canNotScan = false

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            try {
                startActivityForResult(
                        Intent()
                                .setPackage("de.markusfisch.android.binaryeye")
                                .setAction("com.google.zxing.client.android.SCAN"),
                        REQ_SCAN
                )
            } catch (ex: ActivityNotFoundException) {
                canNotScan = true
            }
        } else {
            canNotScan = savedInstanceState.getBoolean(CAN_NOT_SCAN)
        }
    }

    final override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (canNotScan) {
            return AlertDialog.Builder(context!!, theme)
                    .setTitle(R.string.scan_key_missing_title)
                    .setMessage(R.string.scan_key_missing_text)
                    .setNegativeButton(R.string.generic_cancel, null)
                    .setPositiveButton(R.string.scan_key_missing_install) { _, _ ->
                        try {
                            startActivity(
                                    Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("market://details?id=de.markusfisch.android.binaryeye")

                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (ex: ActivityNotFoundException) {
                            Toast.makeText(context!!, R.string.error_general, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .create()
        } else {
            return super.onCreateDialog(savedInstanceState)
        }
    }

    final override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(CAN_NOT_SCAN, canNotScan)
    }

    final override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_SCAN && (!canNotScan)) {
            if (resultCode == Activity.RESULT_OK) {
                val key = ScannedKey.tryDecode(data?.getStringExtra("SCAN_RESULT") ?: "")

                handleResult(key)
            }

            dismiss()
        }
    }

    abstract fun handleResult(key: ScannedKey?)
}