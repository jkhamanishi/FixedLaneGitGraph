@file:Suppress("UnstableApiUsage")

package jkhamanishi.git.graph

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.vcs.log.ui.table.VcsLogCommitList
import java.awt.Component
import java.awt.Container
import java.util.concurrent.TimeUnit
import javax.swing.JTable

class GitGraphInitializer : ProjectActivity {

    private val logger = ConsoleLogger("GitGraphInitializer")

    @Volatile
    private var isIntercepted = false

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
                    if (!isIntercepted) {
                        logger.debug("Git Tool Window opened. Scheduling UI crawl...")
                        scheduleComponentSearch(gitWindow.component, project, retriesLeft = 10)
                    } else {
                        logger.debug("Git Tool Window opened, but isIntercepted is already true.")
                    }
                } else {
                    if (isIntercepted) {
                        logger.info("Git Tool Window closed or hidden. Resetting isIntercepted flag.")
                        isIntercepted = false
                    }
                }
            }
        })
    }

    private fun scheduleComponentSearch(parentComponent: Component, project: Project, retriesLeft: Int) {
        if (isIntercepted || retriesLeft <= 0) return

        ApplicationManager.getApplication().invokeLater {
            if (isIntercepted) return@invokeLater

            val foundTable = findGitLogTable(parentComponent)

            if (foundTable != null) {
                isIntercepted = true
                logger.info("Found target table! Class: ${foundTable.javaClass.name}. Waiting 200ms before intercepting...")

                AppExecutorUtil.getAppScheduledExecutorService().schedule({
                    ApplicationManager.getApplication().invokeLater {
                        interceptTable(project, foundTable)
                    }
                }, 50, TimeUnit.MILLISECONDS)

                return@invokeLater
            }

            AppExecutorUtil.getAppScheduledExecutorService().schedule({
                scheduleComponentSearch(parentComponent, project, retriesLeft - 1)
            }, 250, TimeUnit.MILLISECONDS)
        }
    }

    private fun findGitLogTable(component: Component): JTable? {
        if (component is VcsLogCommitList) {
            return component as? JTable
        }

        if (component is JTable && component.javaClass.name.contains("VcsLog", ignoreCase = true)) {
            return component
        }

        if (component is Container) {
            for (child in component.components) {
                val result = findGitLogTable(child)
                if (result != null) return result
            }
        }

        return null
    }

    private fun interceptTable(project: Project, swingTable: JTable) {
        val rowCount = swingTable.rowCount
        val columnCount = swingTable.columnCount

        logger.info("Intercepting Git Log table ($rowCount rows, $columnCount columns). Table model: ${swingTable.model.javaClass.name}")

        LongEdgesEnforcer.enableLongEdgesViaProperty(swingTable)

        // Delegate all graph inspection and layout injection to PersistentLayoutManager
        PersistentLayoutManager.applyLayout(swingTable)
    }
}