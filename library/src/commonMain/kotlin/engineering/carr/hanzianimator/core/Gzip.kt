package engineering.carr.hanzianimator.core

/**
 * Decompresses a gzip (RFC 1952) byte stream.
 *
 * The bundled stroke-data shards are stored gzip-compressed; each platform
 * provides its own decoder (JVM/Android `GZIPInputStream`, iOS zlib, browser
 * `DecompressionStream`). Declared `suspend` because the browser API is
 * asynchronous.
 */
internal expect suspend fun gunzip(bytes: ByteArray): ByteArray
