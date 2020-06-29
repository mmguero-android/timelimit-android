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

package io.timelimit.android.data.cache.single

internal class ListenerHolder<V> (val listener: SingleItemDataCacheListener<V>) {
    var closed = false
}

internal class DataCacheElement<IV, EV> (var value: IV) {
    var users = 1
    val listeners = mutableListOf<ListenerHolder<EV>>()
}

// thread safe, but most likely slower than possible
fun <IV, EV> SingleItemDataCacheHelperInterface<IV, EV>.createCache(): SingleItemDataCache<EV> {
    val helper = this
    val updateLock = Object()
    val closeLock = Object()

    var element: DataCacheElement<IV, EV>? = null

    fun updateSync() {
        synchronized(updateLock) {
            val item = element ?: return@synchronized

            val oldValue = item.value
            val newValue = helper.updateItemSync(item.value)

            if (newValue !== oldValue) {
                item.value = newValue

                val listeners = synchronized(closeLock) { item.listeners.toList() }

                listeners.forEach {
                    if (!it.closed) {
                        it.listener.onElementUpdated(helper.prepareForUser(oldValue), helper.prepareForUser(newValue))
                    }
                }
            }
        }
    }

    fun openSync(listener: SingleItemDataCacheListener<EV>?): EV {
        synchronized(updateLock) {
            val oldItemToReturn = synchronized(closeLock) {
                element?.also { oldItem -> oldItem.users++ }
            }

            if (oldItemToReturn != null) {
                updateSync()

                synchronized(closeLock) {
                    if (listener != null) {
                        if (oldItemToReturn.listeners.find { it.listener === listener } == null) {
                            oldItemToReturn.listeners.add(ListenerHolder(listener))
                        }
                    }
                }

                return helper.prepareForUser(oldItemToReturn.value)
            } else {
                val value = helper.openItemSync()

                synchronized(closeLock) {
                    element = DataCacheElement<IV, EV>(value).also {
                        if (listener != null) {
                            it.listeners.add(ListenerHolder(listener))
                        }
                    }
                }

                return helper.prepareForUser(value)
            }
        }
    }

    fun close(listener: SingleItemDataCacheListener<EV>?) {
        synchronized(closeLock) {
            val item = element ?: throw IllegalStateException()

            item.listeners.removeAll { if (it.listener === listener) { it.closed = true; true } else false }

            item.users--

            if (item.users < 0) {
                throw IllegalStateException()
            }

            if (item.users == 0) {
                if (item.listeners.isNotEmpty()) {
                    throw IllegalStateException()
                }

                helper.disposeItemFast(item.value)
                element = null
            }
        }
    }

    val ownerInterface = object: SingleItemDataCacheOwnerInterface { override fun updateSync() = helper.wrapOpenOrUpdate { updateSync() } }
    val userInterface = object: SingleItemDataCacheUserInterface<EV> {
        override fun openSync(listener: SingleItemDataCacheListener<EV>?): EV = helper.wrapOpenOrUpdate { openSync(listener) }
        override fun close(listener: SingleItemDataCacheListener<EV>?) = close(listener)
    }

    return SingleItemDataCache(ownerInterface, userInterface)
}