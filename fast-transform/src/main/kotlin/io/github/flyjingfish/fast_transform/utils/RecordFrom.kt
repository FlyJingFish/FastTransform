package io.github.flyjingfish.fast_transform.utils

import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object RecordFrom {
    fun setFrom(dexTask: DexArchiveBuilderTask,files : Array<File>,runtimeProject: RuntimeProject){
        setFrom(dexTask,files.toList(),runtimeProject)
    }

    fun setFrom(dexTask: DexArchiveBuilderTask,files : List<File>,runtimeProject: RuntimeProject){
        val paths = files.map { it.absolutePath }
        val json = Gson().toJson(paths)
        runtimeProject.getJarCache().writeText(json)
        dexTask.projectClasses.setFrom(files)
    }

    fun setLastFrom(dexTask: DexArchiveBuilderTask,runtimeProject: RuntimeProject){
        val jarCache = runtimeProject.getJarCache()
        if (jarCache.exists()){
            val json = jarCache.readText()
            val listType = object : TypeToken<List<String>?>() {}.type
            val cache: List<String>? = Gson().fromJson(json, listType)
            if (!cache.isNullOrEmpty()){
                val files = cache.map { File(it) }
                setFrom(dexTask,files,runtimeProject)
            }
        }
    }
}