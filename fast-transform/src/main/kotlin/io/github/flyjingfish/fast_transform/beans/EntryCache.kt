package io.github.flyjingfish.fast_transform.beans


internal data class EntryCache(
    val jarEntryName: String,
    val byteArray: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EntryCache

        if (jarEntryName != other.jarEntryName) return false
        if (!byteArray.contentEquals(other.byteArray)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = jarEntryName.hashCode()
        result = 31 * result + byteArray.contentHashCode()
        return result
    }
}
