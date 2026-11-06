package com.example.sharedchest.util

/** チェストの大きさの計算。 */
object ChestLayout {
    private const val SLOTS_PER_ROW = 9
    private const val MAX_ROWS = 6
    const val MAX_SLOTS = SLOTS_PER_ROW * MAX_ROWS

    fun slotsFor(configuredRows: Int): Int = configuredRows.coerceIn(1, MAX_ROWS) * SLOTS_PER_ROW

    /**
     * 開くチェストのスロット数。
     * 設定の行数を減らしても、入っているアイテムがはみ出して消えないよう、最後のアイテムが入る大きさにする。
     * 空けば次からは設定どおりの大きさになる。
     *
     * @param lastUsedSlot アイテムが入っている最後のスロット。無ければ -1。
     */
    fun sizeFor(configuredRows: Int, lastUsedSlot: Int): Int {
        val needed = (lastUsedSlot / SLOTS_PER_ROW + 1) * SLOTS_PER_ROW
        return maxOf(slotsFor(configuredRows), if (lastUsedSlot < 0) 0 else needed)
    }
}
