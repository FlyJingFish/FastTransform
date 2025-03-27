package io.github.flyjingfish.fast_transform

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import io.github.flyjingfish.fast_transform.tasks.DefaultTransformTask
import org.gradle.api.tasks.TaskProvider
import org.gradle.configurationcache.extensions.capitalized

/**
 * @param taskProvider 注册的任务的 TaskProvider
 * @param fastDex 是否是加速打包模式
 */
fun Variant.toTransformAll(taskProvider: TaskProvider<out DefaultTransformTask>,fastDex:Boolean = true){
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
                if (!outDir.get().asFile.exists()){
                    outDir.get().asFile.mkdirs()
                }
            }
            it.doLast { _->
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
        it.isFastDex = fastDex
        it.hideOutputFile.set(outFile)
        it.hideOutputDir.set(outDir)
    }
}
