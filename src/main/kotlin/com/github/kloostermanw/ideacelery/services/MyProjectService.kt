package com.github.kloostermanw.ideacelery.services

import com.github.kloostermanw.ideacelery.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {
    init {
        println(MyBundle.message("projectService", project.name))
    }
}
