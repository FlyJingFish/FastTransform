package io.github.flyjingfish.fast_transform

import org.gradle.api.Plugin
import org.gradle.api.Project

class FastDexPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.fastDex()
    }

}