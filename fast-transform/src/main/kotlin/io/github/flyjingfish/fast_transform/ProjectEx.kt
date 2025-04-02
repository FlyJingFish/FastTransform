package io.github.flyjingfish.fast_transform

import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import io.github.flyjingfish.fast_transform.tasks.FastDexTask
import org.gradle.api.Project
import org.gradle.api.Task

fun Project.fastDex(){
    rootProject.gradle.taskGraph.addTaskExecutionGraphListener {
        var lastCanModifyTask : Task? =null
        var dexTask : DexArchiveBuilderTask? =null
        for (task in it.allTasks) {
            if (task is DexArchiveBuilderTask){
                dexTask = task
                break
            }
            lastCanModifyTask = task
        }
        lastCanModifyTask?.doLast { _->
            dexTask?.let { doTask ->
                val fastDexTask = FastDexTask(doTask)
                fastDexTask.taskAction()
            }
        }
//        dexTask?.doFirst {
//            for (projectClass in dexTask.projectClasses) {
//                println("projectClass=${projectClass.absolutePath}")
//            }
//            for (projectClass in dexTask.subProjectClasses) {
//                println("subProjectClasses=${projectClass.absolutePath}")
//            }
//            for (projectClass in dexTask.externalLibClasses) {
//                println("externalLibClasses=${projectClass.absolutePath}")
//            }
//            for (projectClass in dexTask.mixedScopeClasses) {
//                println("mixedScopeClasses=${projectClass.absolutePath}")
//            }
//            for (projectClass in dexTask.externalLibDexFiles) {
//                println("externalLibDexFiles=${projectClass.absolutePath}")
//            }
//        }
    }
}