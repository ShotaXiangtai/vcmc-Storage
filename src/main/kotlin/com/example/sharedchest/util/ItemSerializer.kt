package com.example.sharedchest.util

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64

object ItemSerializer {

    /**
     * ItemStack 配列を Base64 文字列にシリアライズする
     * null スロットも保持する
     */
    @Throws(IOException::class)
    fun serialize(items: Array<ItemStack?>): String {
        ByteArrayOutputStream().use { baos ->
            BukkitObjectOutputStream(baos).use { boos ->
                boos.writeInt(items.size)
                for (item in items) {
                    boos.writeObject(item)
                }
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray())
        }
    }

    /**
     * Base64 文字列から ItemStack 配列をデシリアライズする
     * slots に指定したサイズで配列を返す
     */
    @Throws(IOException::class)
    fun deserialize(base64: String, slots: Int): Array<ItemStack?> {
        val data = Base64.getDecoder().decode(base64)
        ByteArrayInputStream(data).use { bais ->
            BukkitObjectInputStream(bais).use { bois ->
                val savedSize = bois.readInt()
                val items = arrayOfNulls<ItemStack>(slots)
                val readCount = minOf(savedSize, slots)
                for (i in 0 until readCount) {
                    try {
                        items[i] = bois.readObject() as? ItemStack
                    } catch (e: ClassNotFoundException) {
                        throw IOException("スロット $i のアイテムデシリアライズに失敗しました", e)
                    }
                }
                for (i in readCount until savedSize) {
                    try {
                        bois.readObject()
                    } catch (_: ClassNotFoundException) {
                    }
                }
                return items
            }
        }
    }
}
