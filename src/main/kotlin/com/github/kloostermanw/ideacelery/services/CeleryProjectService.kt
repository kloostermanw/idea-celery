package com.github.kloostermanw.ideacelery.services

import com.github.kloostermanw.ideacelery.CeleryBundle
import com.intellij.openapi.project.Project

class CeleryProjectService(project: Project) {
    init {
        println(CeleryBundle.message("projectService", project.name))
    }
}
