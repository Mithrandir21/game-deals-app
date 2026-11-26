package pm.bam.gamedeals.remote.logic

import com.skydoves.sandwich.ApiResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LoggingInterface

/** A logged success must describe the response's shape only, never its content. */
class ApiResponseLogTest {

    private data class Note(val gameId: String, val text: String)
    private data class User(val username: String)

    private val messages = mutableListOf<String>()
    private val logger = object : Logger {
        override fun log(level: LogLevel, tag: String?, throwable: Throwable?, messageProvider: () -> String) {
            messages += messageProvider()
        }
        override fun fatalThrowable(throwable: Throwable, tag: String?) = Unit
        override fun addLoggerListener(loggingInterface: LoggingInterface) = Unit
        override fun removeLoggerListener(loggingInterface: LoggingInterface) = Unit
    }

    @Test
    fun a_list_response_is_logged_as_its_size() {
        ApiResponse.Success(listOf(Note("g1", "my private note"))).log(logger)

        assertEquals(listOf("Success: 1 items"), messages)
    }

    @Test
    fun an_object_response_is_logged_as_its_type_without_its_fields() {
        ApiResponse.Success(User("alice")).log(logger)

        assertEquals(listOf("Success: User"), messages)
        assertFalse(messages.single().contains("alice"))
    }
}
