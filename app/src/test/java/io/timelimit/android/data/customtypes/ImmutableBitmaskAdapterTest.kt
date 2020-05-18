package io.timelimit.android.data.customtypes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.*

class ImmutableBitmaskAdapterTest {
    companion object {
        private val adapter = ImmutableBitmaskAdapter()
    }

    @Test
    fun testEmptyMap() {
        val emptySet = BitSet()

        assertEquals(emptySet.nextSetBit(0), -1)

        val serialized = adapter.toString(ImmutableBitmask(emptySet))
        val parsed = adapter.toImmutableBitmask(serialized).dataNotToModify

        assertEquals(parsed, emptySet)
    }

    // @Test
    fun testRandomMap() {
        val inputSet = BitSet()
        val random = Random()
        var lastIndex = 0

        for (i in 0..random.nextInt(3)) {
            val start = lastIndex
            val end = lastIndex + 1 + random.nextInt(2)
            lastIndex = end + 1 + random.nextInt(2)

            inputSet.set(start, end)
        }

        // input set should not be empty
        assertNotEquals(inputSet.nextSetBit(0), -1)

        val serialized = adapter.toString(ImmutableBitmask(inputSet))
        val parsed = adapter.toImmutableBitmask(serialized).dataNotToModify

        assertEquals(parsed, inputSet)
    }
}