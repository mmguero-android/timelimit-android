package io.timelimit.android.ui.manage.category.blocked_times

import org.junit.Assert.assertEquals
import org.junit.Test

class MinutesOfWeekItemsTest {
    @Test
    fun canGetAllItems() {
        for (i in 0..(ItemUtils.itemsPerWeek - 1)) {
            ItemUtils.getItemAtPosition(i)
        }
    }

    @Test
    fun reverseLookupReturnsSameItem() {
        for (i in 0..(ItemUtils.itemsPerWeek - 1)) {
            val item = ItemUtils.getItemAtPosition(i)
            val index = ItemUtils.getPositionOfItem(item)

            assertEquals(item.toString(), i, index)
        }
    }
}