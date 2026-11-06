package com.example.sharedchest.util

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64

/**
 * ItemStack 配列と、データベースに保存する文字列の変換。
 *
 * 保存は以前の形式のまま行う。1.0.0 は新しい形式を読めず、空のチェストとして開いて中身を消してしまうので、
 * 1.0.0 のサーバーが残っている間は切り替えられない。
 * 読み込みは Paper の NBT 形式にも対応しているので、全サーバーが 1.1.0 以上になれば次のバージョンで安全に切り替えられる。
 */
object ItemSerializer {
    /** Paper の NBT 形式の印。 */
    private const val NBT_PREFIX = "v2:"

    fun isLegacyFormat(data: String): Boolean = !data.startsWith(NBT_PREFIX)

    @Suppress("DEPRECATION")
    fun serialize(items: Array<ItemStack?>): String {
        val bytes = ByteArrayOutputStream()
        BukkitObjectOutputStream(bytes).use { output ->
            output.writeInt(items.size)
            items.forEach(output::writeObject)
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    /**
     * 文字列から ItemStack 配列へ戻す。保存されていたスロットはすべて返す。
     * 空の文字列は、まだ何も保存していないチェストなので空の配列を返す。
     *
     * @throws Exception データが壊れているとき。空のチェストとして開くと保存時に中身が消えるので、呼び出し側で開くのをやめること。
     */
    fun deserialize(data: String): Array<ItemStack?> {
        if (data.isEmpty()) return emptyArray()
        if (isLegacyFormat(data)) return deserializeLegacy(data)
        val bytes = Base64.getDecoder().decode(data.removePrefix(NBT_PREFIX))
        return ItemStack.deserializeItemsFromBytes(bytes)
            .map { item -> item.takeUnless { it.isEmpty } }
            .toTypedArray()
    }

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
