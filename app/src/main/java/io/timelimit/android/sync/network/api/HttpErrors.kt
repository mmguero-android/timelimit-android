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
package io.timelimit.android.sync.network.api

import okhttp3.Response
import java.io.IOException

sealed class HttpError: IOException()
class BadRequestHttpError: HttpError()
class UnauthorizedHttpError: HttpError()
class ConflictHttpError: HttpError()
class ForbiddenHttpError: HttpError()
class GoneHttpError: HttpError()
class NotFoundHttpError: HttpError()
class TooManyRequestsHttpError(): HttpError()

fun Response.assertSuccess() {
    if (!this.isSuccessful) {
        val code = this.code()

        when (code) {
            400 -> throw BadRequestHttpError()
            401 -> throw UnauthorizedHttpError()
            403 -> throw ForbiddenHttpError()
            404 -> throw NotFoundHttpError()
            409 -> throw ConflictHttpError()
            410 -> throw GoneHttpError()
            429 -> throw TooManyRequestsHttpError()
            else -> throw IOException("server returned $code")
        }
    }
}
