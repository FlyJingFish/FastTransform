package io.github.flyjingfish.fast_transform.utils

import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import java.io.File

object RecordFrom {
    var lastFromFiles : List<File> ?= null
    fun setFrom(dexTask: DexArchiveBuilderTask,files : Array<File>){
        setFrom(dexTask,files.toList())
    }

    fun setFrom(dexTask: DexArchiveBuilderTask,files : List<File>){
        lastFromFiles = files
        dexTask.projectClasses.setFrom(files)
    }

    fun setLastFrom(dexTask: DexArchiveBuilderTask){
        lastFromFiles?.let {
            setFrom(dexTask,it)
        }
    }
}