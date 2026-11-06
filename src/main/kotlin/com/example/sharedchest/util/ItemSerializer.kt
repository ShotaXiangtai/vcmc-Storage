package com.example.sharedchest.util

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Base64

/** ItemStack 配列と、データベースに保存する文字列の変換。 */
object ItemSerializer {
    /** Paper の NBT 形式。アイテムの形式はサーバーの更新時に自動で移行される。 */
    private const val FORMAT_PREFIX = "v2:"

    fun isLegacyFormat(data: String): Boolean = !data.startsWith(FORMAT_PREFIX)

    fun serialize(items: Array<ItemStack?>): String =
        FORMAT_PREFIX + Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items))

    /**
     * 文字列から ItemStack 配列へ戻す。保存されていたスロットはすべて返す。
     * 空の文字列は、まだ何も保存していないチェストなので空の配列を返す。
     *
     * @throws Exception データが壊れているとき。空のチェストとして開くと保存時に中身が消えるので、呼び出し側で開くのをやめること。
     */
    fun deserialize(data: String): Array<ItemStack?> {
        if (data.isEmpty()) return emptyArray()
        if (isLegacyFormat(data)) return deserializeLegacy(data)
        val bytes = Base64.getDecoder().decode(data.removePrefix(FORMAT_PREFIX))
        return ItemStack.deserializeItemsFromBytes(bytes)
            .map { item -> item.takeUnless { it.isEmpty } }
            .toTypedArray()
    }

    /** 1.0.0 で保存した内容を読む。次に閉じたときに新しい形式で保存される。 */
    @Suppress("DEPRECATION")
    private fun deserializeLegacy(data: String): Array<ItemStack?> {
        ByteArrayInputStream(Base64.getDecoder().decode(data)).use { bytes ->
            BukkitObjectInputStream(bytes).use { input ->
                val size = input.readInt()
                return Array(size) { slot ->
                    try {
                        input.readObject() as? ItemStack
                    } catch (e: ClassNotFoundException) {
                        throw IOException("スロット $slot のアイテムを読み込めませんでした", e)
                    }
                }
            }
        }
    }
}
