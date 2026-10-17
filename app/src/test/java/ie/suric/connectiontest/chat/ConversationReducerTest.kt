package ie.suric.connectiontest.chat

import org.junit.Assert.*
import org.junit.Test

class ConversationReducerTest {
    @Test fun userMessageStartsRequestAndClearsDraft() {
        val next = ConversationReducer.appendUser(ConversationState(draft = "hello"), "hello")
        assertEquals(listOf(ChatMessage("user", "hello")), next.messages)
        assertEquals("", next.draft)
        assertTrue(next.isLoading)
    }
    @Test fun streamedDeltasAccumulateAsOneAssistantMessage() {
        val state = ConversationReducer.appendDelta(ConversationReducer.appendDelta(ConversationState(), "rose"), "mary")
        assertEquals(listOf(ChatMessage("assistant", "rosemary")), state.messages)
    }
    @Test fun failureStopsLoadingAndPreservesRetryableQuestion() {
        val failed = ConversationReducer.failed(ConversationReducer.appendUser(ConversationState(), "question"), "offline")
        assertFalse(failed.isLoading)
        assertEquals("offline", failed.error)
        assertEquals("question", failed.messages.single().text)
    }
}
