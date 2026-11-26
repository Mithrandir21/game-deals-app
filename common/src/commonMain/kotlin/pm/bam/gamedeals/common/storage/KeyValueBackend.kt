package pm.bam.gamedeals.common.storage

internal interface KeyValueBackend {
    fun readString(key: String): String?
    fun writeString(key: String, value: String): Boolean
    fun contains(key: String): Boolean
    fun remove(key: String): Boolean
}
