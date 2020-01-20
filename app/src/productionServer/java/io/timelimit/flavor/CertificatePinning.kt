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
package io.timelimit.flavor

import io.timelimit.android.BuildConfig
import okhttp3.CertificatePinner

object CertificatePinning {
    val configuration = CertificatePinner.Builder()
            .add(
                    BuildConfig.serverDomain,
                    "sha256/sRHdihwgkaib1P1gxX8HFszlD+7/gTfNvuAybgLPNis=",
                    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg="
            )
            // This is theoretically not required because the fallback happens at
            // the DNS query level => original domain is assumed for certificate verification.
            // However, in case this is changed in the future, then there is already
            // a host pinning for the other domain.
            .add(
                    BuildConfig.backupServerDomain,
                    "sha256/sRHdihwgkaib1P1gxX8HFszlD+7/gTfNvuAybgLPNis=",
                    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg="
            )
            .build()
}
