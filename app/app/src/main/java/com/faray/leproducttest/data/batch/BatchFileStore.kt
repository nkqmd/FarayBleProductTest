package com.faray.leproducttest.data.batch

import android.content.Context
import java.io.File

class BatchFileStore(context: Context) {

    private val batchRoot = File(context.filesDir, "batch")

    fun macListFile(batchId: String): File {
        return File(File(batchRoot, batchId), "${batchId}_mac_list.txt")
    }
}
