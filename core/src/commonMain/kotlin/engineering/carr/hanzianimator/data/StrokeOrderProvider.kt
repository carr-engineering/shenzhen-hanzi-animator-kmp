package engineering.carr.hanzianimator.data

import engineering.carr.hanzianimator.core.StrokeOrder

/**
 * Supplies render-ready, normalized stroke data on demand.
 * Applications can use their existing network client, custom assets or test doubles.
 * A missing character returns null; source failures may throw and cancellation must propagate.
 */
public fun interface StrokeOrderProvider {
    /** Returns data for one Unicode scalar [character], or null if unavailable. */
    public suspend fun strokeOrder(character: String): StrokeOrder?
}
