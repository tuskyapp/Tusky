package com.keylesspalace.tusky.components.compose

import com.keylesspalace.tusky.components.compose.AddHashTagActivity.Companion.addHashtags
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AddHashTagActivityTest(private val input: String, private val want: String) {
    companion object {
        @Parameterized.Parameters(name = "Test {index}: addHashtags({0}) -> {1}")
        @JvmStatic
        fun data() = listOf(
            arrayOf("", ""),
            arrayOf(" ", " "),
            arrayOf("  ", "  "),
            arrayOf("a", "#a"),
            arrayOf(" a", " #a"),
            arrayOf(" a ", " #a "),
            arrayOf("  a", "  #a"),
            arrayOf("  a  ", "  #a  "),
            arrayOf("ab", "#ab"),
            arrayOf(" ab", " #ab"),
            arrayOf("foo bar", "#foo #bar"),
            arrayOf("foo  bar", "#foo  #bar"),
            arrayOf(" foo bar ", " #foo #bar "),
            arrayOf("  foo  bar  ", "  #foo  #bar  ")
        )
    }

    @Test
    fun `addHashtags_matchesExpectations`() {
        Assert.assertEquals(want, addHashtags(input))
    }
}
