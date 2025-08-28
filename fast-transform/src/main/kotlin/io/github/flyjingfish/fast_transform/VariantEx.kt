package io.github.flyjingfish.fast_transform

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import io.github.flyjingfish.fast_transform.tasks.DefaultTransformTask
import io.github.flyjingfish.fast_transform.tasks.FastDexTask
import io.github.flyjingfish.fast_transform.utils.RecordFrom
import io.github.flyjingfish.fast_transform.utils.RuntimeProject
import io.github.flyjingfish.fast_transform.utils.printLog
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.TaskState
import org.gradle.configurationcache.extensions.capitalized
import java.io.File

private val IsSetMap = mutableMapOf<String, Boolean>()

/**
 * @param taskProvider 注册的任务的 TaskProvider
 * @param fastDex 是否是加速打包模式
 */
fun Variant.toTransformAll(
    taskProvider: TaskProvider<out DefaultTransformTask>,
    fastDex: Boolean = true
) {
    val thisTaskClass = taskProvider.get().javaClass
    val project = taskProvider.get().project
    val isSetKey = "${System.identityHashCode(project)}${thisTaskClass.name}${System.identityHashCode(taskProvider)}"
    printLog("=====>$isSetKey")
    var isNotSetFrom = false
    val runtimeProject = RuntimeProject.get(project)
    if (fastDex && IsSetMap[isSetKey] != true) {
        var lastTask: Task? = null
        var dexTask: DexArchiveBuilderTask? = null
        var thisTask: DefaultTransformTask? = null

        var isForceFastDex = false
        val isSet = try {
            project.rootProject.gradle.taskGraph.addTaskExecutionListener(object :
                TaskExecutionListener {
                override fun beforeExecute(p0: Task) {
                }

                override fun afterExecute(p0: Task, p1: TaskState) {
                    if (thisTask != taskProvider.get()){
                        return
                    }
                    if (p0 == lastTask){
                        dexTask?.let { doTask ->
                            if (isForceFastDex) {
                                val fastDexTask = FastDexTask(doTask, runtimeProject)
                                fastDexTask.taskAction()
                                printLog("$isSetKey ===> taskAction")
                                project.rootProject.gradle.taskGraph.removeTaskExecutionListener(this)
                            }else {
                                if (p1.upToDate){
                                    RecordFrom.setLastFrom(doTask,runtimeProject)
                                }
                            }
                        }
                    }
                }

            })
            true
        } catch (_: Throwable) {
            false
        }
        project.rootProject.gradle.taskGraph.addTaskExecutionGraphListener(object :TaskExecutionGraphListener{
            override fun graphPopulated(it: TaskExecutionGraph) {
                var nextTask: Task? = null
                for (task in it.allTasks) {
                    if (task.javaClass == thisTaskClass && task.project == project) {
                        thisTask = task as DefaultTransformTask
                        nextTask = it.allTasks[it.allTasks.indexOf(thisTask)+1]
                    }
                    if (task is DexArchiveBuilderTask && task.project == project) {
                        dexTask = task
                        break
                    }
                    lastTask = task
                }
                val doLastTask = lastTask
                val doDexTask = dexTask
                val doThisTask = thisTask
                if (doLastTask != null && doDexTask != null && doThisTask != null
                    && doThisTask.isFastDex && doLastTask.javaClass != thisTaskClass
                    && (doLastTask !is DefaultTransformTask || !doLastTask.isFastDex)
                ) {
                    isForceFastDex = true

                    if (!isSet) {
                        doLastTask.doLast { _ ->
                            if (thisTask == taskProvider.get()){
                                doDexTask.let { doTask ->
                                    val fastDexTask = FastDexTask(doTask, runtimeProject)
                                    fastDexTask.taskAction()
                                }
                            }
                        }
                    }
                }
                if (nextTask != null && nextTask != doDexTask) {
                    if (nextTask !is DefaultTransformTask) {
                        doThisTask?.isFastDex = false
                    }
                    isNotSetFrom = true
                }

                printLog("$isSetKey ===> $isForceFastDex")

                doDexTask?.doFirst {
                    if (thisTask != taskProvider.get()){
                        return@doFirst
                    }
                    val dexTask = it as DexArchiveBuilderTask
                    for (projectClass in dexTask.projectClasses) {
                        printLog("projectClass=${projectClass.absolutePath}")
                    }
                    for (projectClass in dexTask.subProjectClasses) {
                        printLog("subProjectClasses=${projectClass.absolutePath}")
                    }
                    for (projectClass in dexTask.externalLibClasses) {
                        printLog("externalLibClasses=${projectClass.absolutePath}")
                    }
                    for (projectClass in dexTask.mixedScopeClasses) {
                        printLog("mixedScopeClasses=${projectClass.absolutePath}")
                    }
                    for (projectClass in dexTask.externalLibDexFiles) {
                        printLog("externalLibDexFiles=${projectClass.absolutePath}")
                    }
                }
                project.rootProject.gradle.taskGraph.removeTaskExecutionGraphListener(this)
            }

        })
        IsSetMap[isSetKey] = true
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
        val outDir =
            it.project.layout.buildDirectory.file("intermediates/classes/${taskProvider.name}/All/")
        val outFile =
            it.project.layout.buildDirectory.file("intermediates/classes/${taskProvider.name}/All/classes.jar")
        if (fastDex) {
            it.doFirst {
                if (outFile.get().asFile.exists()) {
                    outFile.get().asFile.delete()
                }
                IsSetMap[isSetKey] = false
                if (!outDir.get().asFile.exists()) {
                    outDir.get().asFile.mkdirs()
                }
            }
            it.doLast { aopTask ->
                if (aopTask is DefaultTransformTask && aopTask.isFastDex && !isNotSetFrom) {
                    val dexTaskName = "dexBuilder${name.capitalized()}"
                    it.project.tasks.withType(DexArchiveBuilderTask::class.java)
                        .forEach { dexTask ->
                            if (dexTaskName == dexTask?.name && dexTask.project == project) {
                                outDir.get().asFile.listFiles()
                                    ?.filter { file -> file.name != outFile.get().asFile.name }
                                    ?.let { files ->
                                        RecordFrom.setFrom(dexTask, files,runtimeProject)
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
