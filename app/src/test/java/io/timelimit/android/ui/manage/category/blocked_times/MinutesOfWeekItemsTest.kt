package io.timelimit.android.ui.manage.category.blocked_times

import org.junit.Assert.assertEquals
import org.junit.Test

class MinutesOfWeekItemsTest {
    @Test
    fun canGetAllItems() {
        for (i in 0 until MinuteOfWeekItems.itemsPerWeek) {
            MinuteOfWeekItems.getItemAtPosition(i)
        }
    }

    @Test
    fun reverseLookupReturnsSameItem() {
        for (i in 0 until MinuteOfWeekItems.itemsPerWeek) {
            val item = MinuteOfWeekItems.getItemAtPosition(i)
            val index = MinuteOfWeekItems.getPositionOfItem(item)

            assertEquals(item.toString(), i, index)
        }
    }
}