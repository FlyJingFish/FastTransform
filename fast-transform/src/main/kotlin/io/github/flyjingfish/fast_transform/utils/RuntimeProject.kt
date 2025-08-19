package io.github.flyjingfish.fast_transform.utils

import org.gradle.api.Project
import java.io.File
import java.io.Serializable

data class RuntimeProject(
    val buildDir: File,
    val rootProjectBuildDir: File,
    val layoutBuildDirectory: File,
    val name: String,
): Serializable {
    companion object {
        fun get(project: Project): RuntimeProject {
            val runtimeProject = RuntimeProject(
                buildDir = project.getBuildDirectory(),
                rootProjectBuildDir = project.rootProject.getBuildDirectory(),
                layoutBuildDirectory = project.layout.buildDirectory.asFile.get(),
                name = project.name
            )
            return runtimeProject
        }

    }

    fun getJarCache():File{
        val file = File(buildDir.absolutePath,"/intermediates/fast_transform/last_cache.json")
        if (!file.parentFile.exists()){
            file.parentFile.mkdirs()
        }
        return file
    }
}