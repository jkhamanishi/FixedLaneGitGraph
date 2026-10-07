@file:Suppress("UnstableApiUsage")

package jkhamanishi.git.graph

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.concurrency.AppExecutorUtil
import jkhamanishi.git.graph.rendering.GraphRenderingInitializer
import java.util.concurrent.TimeUnit

/**
 * Plugin lifecycle initializer.
 *
 * Handles:
 * - Plugin startup notifications
 * - Attaching to git tool window when it becomes visible
 * - Delegating to rendering initialization once the git log table is found
 */
class PluginInitializer : ProjectActivity {

    private val logger = ConsoleLogger("PluginInitializer")

    @Volatile
    private var isInitialized = false

    override suspend fun execute(project: Project) {
        VcsNotifier.getInstance(project).notifySuccess(
            "fixed.lane.git.graph.notification",
            "FixedLaneGitGraph plugin is activated",
            "FixedLaneGitGraph active for project: ${project.name}"
        )

        ApplicationManager.getApplication().invokeLater {
            attachToGitLogTable(project)
        }
    }

    private fun attachToGitLogTable(project: Project) {
        val connection = project.messageBus.connect()

        connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged(toolWindowManager: ToolWindowManager) {
                val gitWindow = toolWindowManager.getToolWindow("Git")
                    ?: toolWindowManager.getToolWindow("Version Control")

                val isVisible = gitWindow?.isVisible == true

                if (isVisible) {
                    if (!isInitialized) {
                        logger.debug("Git Tool Window opened. Scheduling rendering initialization...")
                        scheduleRenderingInitialization(gitWindow.component, project, retriesLeft = 10)
                    } else {
                        logger.debug("Git Tool Window opened, but initialization already complete.")
                    }
                } else {
                    if (isInitialized) {
                        logger.info("Git Tool Window closed or hidden. Resetting initialization flag.")
                        isInitialized = false
                    }
                }
            }
        })
    }

    private fun scheduleRenderingInitialization(parentComponent: java.awt.Component, project: Project, retriesLeft: Int) {
        if (isInitialized || retriesLeft <= 0) return

        ApplicationManager.getApplication().invokeLater {
            if (isInitialized) return@invokeLater

            val foundTable = GraphRenderingInitializer.findGitLogTable(parentComponent)

            if (foundTable != null) {
                isInitialized = true
                logger.info("Found target table! Class: ${foundTable.javaClass.name}. Waiting 50ms before initializing rendering...")

                AppExecutorUtil.getAppScheduledExecutorService().schedule({
                    ApplicationManager.getApplication().invokeLater {
                        GraphRenderingInitializer.initializeRendering(project, foundTable)
                    }
                }, 50, TimeUnit.MILLISECONDS)

                return@invokeLater
            }

            AppExecutorUtil.getAppScheduledExecutorService().schedule({
                scheduleRenderingInitialization(parentComponent, project, retriesLeft - 1)
            }, 250, TimeUnit.MILLISECONDS)
        }
    }
}

