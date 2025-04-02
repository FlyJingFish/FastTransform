package io.github.flyjingfish.fast_transform

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import io.github.flyjingfish.fast_transform.tasks.DefaultTransformTask
import io.github.flyjingfish.fast_transform.tasks.FastDexTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.configurationcache.extensions.capitalized

/**
 * @param taskProvider 注册的任务的 TaskProvider
 * @param fastDex 是否是加速打包模式
 */
private val IsSetMap = mutableMapOf<Project,Boolean>()
fun Variant.toTransformAll(taskProvider: TaskProvider<out DefaultTransformTask>, fastDex:Boolean = true){
    val project = taskProvider.get().project
    if (fastDex && IsSetMap[project] != true){
        var lastCanModifyTask : Task? =null
        var dexTask : DexArchiveBuilderTask? =null
        var thisTask : DefaultTransformTask? =null
        val thisTaskClass = taskProvider.get().javaClass
        var isFastDex = false
        val isSet = try {
            project.rootProject.gradle.taskGraph.afterTask {
                if (it == lastCanModifyTask){
                    dexTask?.let { doTask ->
                        if (thisTask != null && !thisTask!!.isFastDex && isFastDex){
                            val fastDexTask = FastDexTask(doTask)
                            fastDexTask.taskAction()
                        }
                    }
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
        project.rootProject.gradle.taskGraph.addTaskExecutionGraphListener {
            for (task in it.allTasks) {
                if (task.javaClass == thisTaskClass){
                    thisTask = task as DefaultTransformTask
                }
                if (task is DexArchiveBuilderTask){
                    dexTask = task
                    break
                }
                lastCanModifyTask = task
            }
            if (lastCanModifyTask != null && dexTask != null && thisTask != null){
                if (thisTask!!.isFastDex && lastCanModifyTask!!.javaClass != thisTaskClass){
                    isFastDex = true
                    thisTask!!.isFastDex = false
                    if (!isSet){
                        lastCanModifyTask?.doLast { _->
                            dexTask?.let { doTask ->
                                val fastDexTask = FastDexTask(doTask)
                                fastDexTask.taskAction()
                            }
                        }
                    }
                }
            }
        }
        IsSetMap[project] = true
    }

    artifacts
        .forScope(ScopedArtifacts.Scope.ALL)
        .use(taskProvider)
        .toTransform(
            ScopedArtifact.CLASSES,
            DefaultTransformTask::hideAllJars,
            DefaultTransformTask::hideAllDirectories,
            if (fastDex) DefaultTransformTask::hideOutputDir else DefaultTransformTask::hideOutputFile
        )
    taskProvider.configure {
        val outDir = it.project.layout.buildDirectory.file("intermediates/classes/${taskProvider.name}/All/")
        val outFile = it.project.layout.buildDirectory.file("intermediates/classes/${taskProvider.name}/All/classes.jar")
        if (fastDex){
            it.doFirst{
                IsSetMap[project] = false
                if (!outDir.get().asFile.exists()){
                    outDir.get().asFile.mkdirs()
                }
            }
            it.doLast { aopTask ->
                if (aopTask is DefaultTransformTask && aopTask.isFastDex){
                    val dexTaskName = "dexBuilder${name.capitalized()}"
                    it.project.tasks.withType(DexArchiveBuilderTask::class.java).forEach { dexTask ->
                        if (dexTaskName == dexTask?.name){
                            outDir.get().asFile.listFiles()?.filter { file -> file.name != outFile.get().asFile.name}?.let { files ->
                                dexTask.projectClasses.setFrom(files)
                            }
                        }
                    }
                }
            }
        }
        it.isFastDex = fastDex
        it.hideOutputFile.set(outFile)
        it.hideOutputDir.set(outDir)
    }
}
