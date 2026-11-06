package com.example.sharedchest

import com.example.sharedchest.i18n.Language
import com.example.sharedchest.i18n.Translations
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin

/** 受け取る人の言語で文面を作る。日本語は config.yml、ほかの言語は lang フォルダから読む。 */
class Messages(private val plugin: JavaPlugin) {
    private val translations = Translations(plugin)
    private val miniMessage = MiniMessage.miniMessage()

    fun reload() = translations.reload()

    fun send(target: Audience, key: String) {
        val language = Language.of(target)
        val template = translations.string(language, key) ?: plugin.config.getString("messages.$key") ?: key
        target.sendMessage(miniMessage.deserialize(template))
    }

    /** チェストのタイトル。日本語は config.yml の chest.title。 */
    fun chestTitle(language: Language): Component {
        val template = translations.string(language, "chest-title") ?: plugin.config.getString("chest.title").orEmpty()
        return miniMessage.deserialize(template)
    }
}
