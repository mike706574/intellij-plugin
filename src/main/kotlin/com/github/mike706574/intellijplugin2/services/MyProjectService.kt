package com.github.mike706574.intellijplugin2.services

import com.github.mike706574.intellijplugin2.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
