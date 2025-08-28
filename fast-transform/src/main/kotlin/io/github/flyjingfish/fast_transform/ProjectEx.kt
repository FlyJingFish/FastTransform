package io.github.flyjingfish.fast_transform

import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import io.github.flyjingfish.fast_transform.tasks.FastDexTask
import io.github.flyjingfish.fast_transform.utils.RuntimeProject
import org.gradle.api.Project
import org.gradle.api.Task

fun Project.fastDex(){
    val curProject = this
    var lastCanModifyTask : Task? =null
    var dexTask : DexArchiveBuilderTask? =null
    val runtimeProject = RuntimeProject.get(this)
    val isSet = try {
        project.rootProject.gradle.taskGraph.afterTask {
            if (it == lastCanModifyTask){
                dexTask?.let { doTask ->
                    val fastDexTask = FastDexTask(doTask, runtimeProject)
                    fastDexTask.taskAction()
                }
            }
        }
        true
    } catch (_: Throwable) {
        false
    }
    project.rootProject.gradle.taskGraph.addTaskExecutionGraphListener {
        for (task in it.allTasks) {
            if (task is DexArchiveBuilderTask && task.project == curProject){
                dexTask = task
                break
            }
            lastCanModifyTask = task
        }
        if (!isSet){
            lastCanModifyTask?.doLast { _->
                dexTask?.let { doTask ->
                    val fastDexTask = FastDexTask(doTask, runtimeProject)
                    fastDexTask.taskAction()
                }
            }
        }

//            dexTask?.doFirst {
//                for (projectClass in dexTask.projectClasses) {
//                    println("projectClass=${projectClass.absolutePath}")
//                }
//                for (projectClass in dexTask.subProjectClasses) {
//                    println("subProjectClasses=${projectClass.absolutePath}")
//                }
//                for (projectClass in dexTask.externalLibClasses) {
//                    println("externalLibClasses=${projectClass.absolutePath}")
//                }
//                for (projectClass in dexTask.mixedScopeClasses) {
//                    println("mixedScopeClasses=${projectClass.absolutePath}")
//                }
//                for (projectClass in dexTask.externalLibDexFiles) {
//                    println("externalLibDexFiles=${projectClass.absolutePath}")
//                }
//            }
    }
}