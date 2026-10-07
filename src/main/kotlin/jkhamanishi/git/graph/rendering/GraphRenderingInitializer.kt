@file:Suppress("UnstableApiUsage")

package jkhamanishi.git.graph.rendering

import com.intellij.openapi.project.Project
import com.intellij.vcs.log.ui.table.VcsLogCommitList
import jkhamanishi.git.graph.ConsoleLogger
import java.awt.Component
import java.awt.Container
import javax.swing.JTable

/**
 * Rendering initialization utilities.
 *
 * Handles:
 * - Finding the git log table in the IDE UI hierarchy
 * - Initializing rendering customizations (long edges, persistent layout)
 * - Injecting proxies to intercept and customize lane assignments
 */
object GraphRenderingInitializer {

    private val logger = ConsoleLogger("GraphRenderingInitializer")

    fun findGitLogTable(component: Component): JTable? {
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

    @Suppress("UNUSED_PARAMETER")
    fun initializeRendering(project: Project, swingTable: JTable) {
        val rowCount = swingTable.rowCount
        val columnCount = swingTable.columnCount

        logger.info("Initializing rendering for Git Log table ($rowCount rows, $columnCount columns). Table model: ${swingTable.model.javaClass.name}")

        LongEdgesEnforcer.enableLongEdgesViaProperty(swingTable)

        // Delegate all graph inspection and layout injection to PersistentLayoutManager
        PersistentLayoutManager.applyLayout(swingTable)
    }
}


