package dev.goodwy.rphone.recordings

import android.content.Context
import android.os.storage.StorageManager
import java.io.File

/** Honor the app allocation budget without counting reclaimable cache as already writable. */
internal fun captureWritableBytes(context: Context, file: File): Long {
    val storage=context.getSystemService(StorageManager::class.java)
    val budget=storage.getAllocatableBytes(storage.getUuidForPath(file))
    val stats=android.system.Os.statvfs(file.absolutePath)
    return captureStorageBudget(stats.f_bavail,stats.f_frsize,budget)
}
internal fun captureStorageBudget(blocks: Long,blockSize: Long,allocatable: Long): Long {
    require(blocks>=0 && blockSize>0 && allocatable>=0)
    val free=if(blocks>Long.MAX_VALUE/blockSize)Long.MAX_VALUE else blocks*blockSize
    return minOf(free,allocatable)
}
