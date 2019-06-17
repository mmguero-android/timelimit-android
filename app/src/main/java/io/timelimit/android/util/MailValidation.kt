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
package io.timelimit.android.util

import org.apache.commons.text.similarity.LevenshteinDistance

object MailValidation {
    private val mailProviders = listOf(
            "gmail.com",
            "googlemail.com",
            "web.de",
            "gmx.de",
            "freenet.de",
            "mailbox.org",
            "posteo.de"
    )

    fun seemsMailAddressValid(address: String): Boolean = address.contains("@")
    fun getDomain(address: String) = address.split("@").last()
    fun seemsDomainValid(domain: String) = domain.contains(".")
    fun suggestAlternativeDomain(domain: String): String? {
        val suggestion = mailProviders.map { provider ->
            provider to LevenshteinDistance.getDefaultInstance().apply(domain, provider)
        }.sortedBy { it.second }.filter { it.second <= 5 }.firstOrNull()?.first

        if (suggestion == domain) {
            return null
        } else {
            return suggestion
        }
    }
}