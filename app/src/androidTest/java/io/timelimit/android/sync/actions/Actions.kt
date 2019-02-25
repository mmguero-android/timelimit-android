package io.timelimit.android.sync.actions

import org.json.JSONObject
import org.junit.Test

class Actions {
    @Test
    fun decrementCategoryExtraTimeShouldBeSerializedAndParsedCorrectly() {
        val originalAction = DecrementCategoryExtraTimeAction(categoryId = "abcdef", extraTimeToSubtract = 1000 * 30)

        val serializedAction = SerializationUtil.serializeAction(originalAction)
        val parsedAction = ActionParser.parseAppLogicAction(JSONObject(serializedAction))

        assert(parsedAction == originalAction)
    }
}