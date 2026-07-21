package engineering.carr.hanzianimator.data

import engineering.carr.hanzianimator.core.StrokeOrder

/**
 * Supplies [StrokeOrder] data for characters on demand.
 *
 * The default implementation is [BundledStrokeOrderProvider], which reads the
 * stroke data packaged with the library. Implement this interface to source
 * data elsewhere (a network service, a custom asset bundle, a test fake).
 */
public interface StrokeOrderProvider {
    /**
     * Returns the [StrokeOrder] for [character], or `null` if it is unavailable.
     * [character] is expected to be a single character; only its first Unicode
     * code point is considered.
     */
    public suspend fun strokeOrder(character: String): StrokeOrder?
}
