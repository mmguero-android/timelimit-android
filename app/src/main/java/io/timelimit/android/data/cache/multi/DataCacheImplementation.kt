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
package io.timelimit.android.data.cache.multi

internal class ListenerHolder<K, V> (val listener: DataCacheListener<K, V>) {
    var closed = false
}

internal class DataCacheElement<K, IV, EV> (var value: IV) {
    var users = 1
    val listeners = mutableListOf<ListenerHolder<K, EV>>()
}

// thread safe, but most likely slower than possible
fun <K, IV, EV> DataCacheHelperInterface<K, IV, EV>.createCache(): DataCache<K, EV> {
    val helper = this
    val elements = mutableMapOf<K, DataCacheElement<K, IV, EV>>()
    val updateLock = Object()
    val closeLock = Object()
    var isClosed = false

    fun assertNotClosed() {
        if (isClosed) {
            throw IllegalStateException()
        }
    }

    fun updateSync(key: K, item: DataCacheElement<K, IV, EV>) {
        synchronized(updateLock) {
            assertNotClosed()

            val oldValue = item.value
            val newValue = helper.updateItemSync(key, oldValue)

            if (newValue !== oldValue) {
                item.value = newValue

                val listeners = synchronized(closeLock) { item.listeners.toList() }

                listeners.forEach {
                    if (!it.closed) {
                        it.listener.onElementUpdated(key, helper.prepareForUser(oldValue), helper.prepareForUser(newValue))
                    }
                }
            }
        }
    }

    fun updateSync() {
        synchronized(updateLock) {
            assertNotClosed()

            elements.forEach { updateSync(it.key, it.value) }
        }
    }

    fun openSync(key: K, listener: DataCacheListener<K, EV>?): EV {
        synchronized(updateLock) {
            assertNotClosed()

            val oldItemToReturn = synchronized(closeLock) {
                elements[key]?.also { oldItem -> oldItem.users++ }
            }

            if (oldItemToReturn != null) {
                updateSync(key, oldItemToReturn)

                synchronized(closeLock) {
                    if (listener != null) {
                        if (oldItemToReturn.listeners.find { it.listener === listener } == null) {
                            oldItemToReturn.listeners.add(ListenerHolder(listener))
                        }
                    }
                }

                return helper.prepareForUser(oldItemToReturn.value)
            }

            val value = helper.openItemSync(key)

            synchronized(closeLock) {
                elements[key] = DataCacheElement<K, IV, EV>(value).also {
                    if (listener != null) {
                        it.listeners.add(ListenerHolder(listener))
                    }
                }
            }

            return helper.prepareForUser(value)
        }
    }

    fun close(key: K, listener: DataCacheListener<K, EV>?) {
        synchronized(closeLock) {
            assertNotClosed()

            val item = elements[key] ?: throw IllegalStateException()

            item.listeners.removeAll { if (it.listener === listener) { it.closed = true; true } else false }

            item.users--

            if (item.users < 0) {
                throw IllegalStateException()
            }

            if (item.users == 0) {
                if (item.listeners.isNotEmpty()) {
                    throw IllegalStateException()
                }

                helper.disposeItemFast(key, item.value)
                elements.remove(key)
            }
        }
    }

    fun close() {
        synchronized(updateLock) {
            synchronized(closeLock) {
                assertNotClosed()

                elements.entries.forEach { it.value.listeners.clear(); helper.disposeItemFast(it.key, it.value.value) }
                elements.clear()

                isClosed = true

                helper.close()
            }
        }
    }

    val ownerInterface = object: DataCacheOwnerInterface { override fun updateSync() = helper.wrapOpenOrUpdate { updateSync() } }
    val userInterface = object: DataCacheUserInterface<K, EV> {
        override fun openSync(key: K, listener: DataCacheListener<K, EV>?): EV = helper.wrapOpenOrUpdate { openSync(key, listener) }
        override fun close(key: K, listener: DataCacheListener<K, EV>?) = close(key, listener)
        override fun close() = close()
    }

    return DataCache(ownerInterface, userInterface)
}