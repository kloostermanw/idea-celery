package com.github.kloostermanw.ideacelery.listeners

import com.github.kloostermanw.ideacelery.services.CeleryProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

internal class CeleryProjectManagerListener : ProjectManagerListener {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun projectOpened(project: Project) {
        project.service<CeleryProjectService>()
    }
}
