package com.example.sharedchest.i18n

import kotlin.test.Test
import kotlin.test.assertNotNull

class TranslationFilesTest {
    @Test
    fun `どの言語にも日本語と同じ文面がある`() {
        val config = TranslationFilesCheck.resource("config.yml")
        val messages = assertNotNull(config.getConfigurationSection("messages"))
        TranslationFilesCheck.check(
            TranslationFilesCheck.texts(messages) + ("chest-title" to config.getString("chest.title").orEmpty()),
        )
    }
}
