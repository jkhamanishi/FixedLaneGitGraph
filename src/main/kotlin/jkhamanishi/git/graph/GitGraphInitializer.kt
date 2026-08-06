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
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import javax.swing.JTable

class GitGraphInitializer : ProjectActivity {

    private val logger = ConsoleLogger("GitGraphInitializer")

    @Volatile
    private var isIntercepted = false

    override suspend fun execute(project: Project) {
        VcsNotifier.getInstance(project).notifySuccess(
            "fixed.lane.git.graph.notification",
            "FixedLaneGitGraph2",
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

        VcsNotifier.getInstance(project).notifySuccess(
            "fixed.lane.git.graph.table",
            "Git log intercepted",
            "Successfully accessed Git Log table with $rowCount rows and $columnCount columns."
        )
        LongEdgesEnforcer().enableLongEdgesViaProperty(swingTable)
        inspectGraphModel(swingTable)
    }

    private fun inspectGraphModel(swingTable: JTable) {
        try {
            val dataModel = swingTable.model
            val getVisiblePackMethod = dataModel.javaClass.methods.firstOrNull { it.name.startsWith("getVisiblePack") }

            if (getVisiblePackMethod != null) {
                getVisiblePackMethod.isAccessible = true
                val visiblePack = getVisiblePackMethod.invoke(dataModel)
                logger.info("DEBUG: Retained VisiblePack instance: $visiblePack")
                if (visiblePack != null) {
                    inspectVisiblePack(visiblePack)
                }
            } else {
                logger.warn("DEBUG: No getVisiblePack method on dataModel: ${dataModel.javaClass.name}")
            }
        } catch (e: Exception) {
            logger.warn("Error retrieving VisiblePack: ${e.message}")
        }
    }

    private fun inspectVisiblePack(visiblePack: Any) {
        try {
            val refsMethod = visiblePack.javaClass.methods.firstOrNull { it.name.contains("getRefs", ignoreCase = true) }
            val refs = refsMethod?.invoke(visiblePack)
            if (refs != null) {
                logger.info("Successfully extracted references container for branch column alignment.")
            }
        } catch (e: Exception) {
            logger.warn("Could not extract references: ${e.message}")
        }

        val getVisibleGraphMethod = visiblePack.javaClass.methods.firstOrNull {
            it.name == "getVisibleGraph" || it.name.contains("VisibleGraph", ignoreCase = true)
        }

        if (getVisibleGraphMethod != null) {
            val visibleGraph = getVisibleGraphMethod.invoke(visiblePack)
            logger.info("DEBUG: Retained VisibleGraph instance: $visibleGraph")
            if (visibleGraph != null) {
                inspectVisibleGraph(visibleGraph, visiblePack)
            }
        } else {
            logger.warn("DEBUG: No getVisibleGraph method on VisiblePack: ${visiblePack.javaClass.name}")
        }
    }

    private fun inspectVisibleGraph(visibleGraph: Any, visiblePack: Any) {
        var clazz: Class<*>? = visibleGraph.javaClass
        var foundGenerator = false
        while (clazz != null && clazz != Any::class.java) {
            for (field in clazz.declaredFields) {
                try {
                    field.isAccessible = true
                    val value = field.get(visibleGraph) ?: continue

                    if (field.name == "printElementGenerator") {
                        foundGenerator = true
                        logger.info("DEBUG: Found printElementGenerator field on $clazz")
                        injectPersistentLayoutGetter(value, visibleGraph, visiblePack)
                    }
                } catch (_: Exception) {
                    // Ignore inaccessible fields
                }
            }
            clazz = clazz.superclass
        }
        if (!foundGenerator) {
            logger.warn("DEBUG: Could not locate printElementGenerator field on VisibleGraph hierarchy.")
        }
    }

    private fun injectPersistentLayoutGetter(printElementGenerator: Any, visibleGraph: Any, visiblePack: Any) {
        try {
            PersistentLayoutManager.clear()
            logger.info("DEBUG: Cleared PersistentLayoutManager cache.")

            val comparatorField = printElementGenerator.javaClass.getDeclaredField("elementComparator").apply { isAccessible = true }
            val comparator = comparatorField.get(printElementGenerator) ?: run {
                logger.warn("DEBUG: elementComparator field was null on printElementGenerator.")
                return
            }

            val getterField = comparator.javaClass.declaredFields.firstOrNull {
                it.name == "myLayoutIndexGetter" || it.type.name.contains("Function")
            }?.apply { isAccessible = true } ?: run {
                logger.warn("DEBUG: Could not locate myLayoutIndexGetter on comparator.")
                return
            }

            val originalGetter = getterField.get(comparator) ?: run {
                logger.warn("DEBUG: originalGetter was null.")
                return
            }

            logger.info("DEBUG: originalGetter type: ${originalGetter.javaClass.name}")

            try {
                var nodesCount = 0

                val countMethod = visibleGraph.javaClass.methods.firstOrNull {
                    it.parameterCount == 0 && (it.name == "getVisibleNodesCount" || it.name == "getNodesCount" || it.name == "nodesCount")
                }
                if (countMethod != null) {
                    nodesCount = countMethod.invoke(visibleGraph) as? Int ?: 0
                }

                if (nodesCount <= 0) {
                    val genericCountMethod = visibleGraph.javaClass.methods.firstOrNull {
                        it.parameterCount == 0 && (it.returnType == Int::class.java || it.returnType == Int::class.javaPrimitiveType) &&
                                (it.name.contains("count", ignoreCase = true) || it.name.contains("size", ignoreCase = true))
                    }
                    if (genericCountMethod != null) {
                        nodesCount = genericCountMethod.invoke(visibleGraph) as? Int ?: 0
                    }
                }

                if (nodesCount <= 0) {
                    val packSizeMethod = visiblePack.javaClass.methods.firstOrNull {
                        it.parameterCount == 0 && (it.returnType == Int::class.java || it.returnType == Int::class.javaPrimitiveType) &&
                                it.name.contains("size", ignoreCase = true)
                    }
                    if (packSizeMethod != null) {
                        nodesCount = packSizeMethod.invoke(visiblePack) as? Int ?: 0
                    }
                }

                if (nodesCount <= 0) {
                    logger.warn("All node count strategies returned 0 or invalid count ($nodesCount). Skipping lane layout calculation.")
                    return
                }
                logger.info("VisibleGraph node count successfully resolved: $nodesCount nodes found.")

                var getNodeMethod = visibleGraph.javaClass.methods.firstOrNull {
                    it.parameterCount == 1 &&
                            (it.parameterTypes[0] == Int::class.java || it.parameterTypes[0] == Int::class.javaPrimitiveType) &&
                            !it.returnType.isPrimitive &&
                            it.name.contains("Node", ignoreCase = true)
                }

                if (getNodeMethod == null) {
                    getNodeMethod = visibleGraph.javaClass.methods.firstOrNull {
                        it.parameterCount == 1 &&
                                (it.parameterTypes[0] == Int::class.java || it.parameterTypes[0] == Int::class.javaPrimitiveType) &&
                                !it.returnType.isPrimitive &&
                                it.returnType != String::class.java
                    }
                }

                if (getNodeMethod == null) {
                    logger.warn("Could not find any method to retrieve node at index on VisibleGraph.")
                    return
                }

                getNodeMethod.isAccessible = true
                logger.info("Successfully resolved node getter method: ${getNodeMethod.name} returning ${getNodeMethod.returnType.name}")

                val nodeParents = HashMap<Int, List<Int>>()

                for (i in 0 until nodesCount) {
                    val node = getNodeMethod.invoke(visibleGraph, i) ?: continue

                    val getAdjacentMethod = node.javaClass.methods.firstOrNull { it.name == "getAdjacentRows" }?.apply { isAccessible = true }

                    val rawAdjacent = if (getAdjacentMethod != null) {
                        try {
                            val paramTypes = getAdjacentMethod.parameterTypes
                            val result = when {
                                paramTypes.isEmpty() -> getAdjacentMethod.invoke(node)
                                paramTypes.size == 1 && (paramTypes[0] == Boolean::class.java || paramTypes[0] == Boolean::class.javaPrimitiveType) -> {
                                    // Pass true to get upper/parent adjacent rows
                                    getAdjacentMethod.invoke(node, true)
                                }
                                paramTypes.size == 1 -> {
                                    getAdjacentMethod.invoke(node, i)
                                }
                                else -> null
                            }
                            result as? List<*> ?: emptyList<Any>()
                        } catch (e: Exception) {
                            logger.warn("Failed to invoke getAdjacentRows for index $i: ${e.javaClass.simpleName} - ${e.message}")
                            emptyList<Any>()
                        }
                    } else {
                        emptyList<Any>()
                    }

                    val parentIndices = rawAdjacent.mapNotNull { p ->
                        when (p) {
                            is Int -> p
                            else -> {
                                val m = p?.javaClass?.methods?.firstOrNull {
                                    it.name == "getNodeIndex" || it.name == "getIndex" || it.name == "getId" || it.name == "toInt"
                                }?.apply { isAccessible = true }
                                m?.invoke(p) as? Int
                            }
                        }
                    }
                    nodeParents[i] = parentIndices
                }

                data class ChildRelation(val childIndex: Int, val parentPosition: Int)
                val parentToChildren = HashMap<Int, MutableList<ChildRelation>>()

                for (c in 0 until nodesCount) {
                    val parents = nodeParents[c] ?: emptyList()
                    parents.forEachIndexed { pos, p ->
                        parentToChildren.getOrPut(p) { mutableListOf() }
                            .add(ChildRelation(childIndex = c, parentPosition = pos))
                    }
                }

                val nodeToLane = HashMap<Int, Int>()
                var laneCounter = 0

                fun acquireLane(): Int {
                    val current = laneCounter
                    if (laneCounter < 2) {
                        laneCounter++
                    }
                    return current.coerceAtMost(2)
                }

                for (i in 0 until nodesCount) {
                    val children = parentToChildren[i] ?: emptyList()

                    if (children.isEmpty()) {
                        // Rule 1: If the current commit has no child commit, assign a new lane
                        // (if a lane is already used, laneCounter increments to lane two / next lane).
                        nodeToLane[i] = acquireLane()
                    } else {
                        val primaryChild = children[0]
                        val childIdx = primaryChild.childIndex
                        val parentPos = primaryChild.parentPosition
                        val childParents = nodeParents[childIdx] ?: emptyList()
                        val isChildMerge = childParents.size > 1
                        val childLane = nodeToLane[childIdx] ?: acquireLane()

                        if (isChildMerge) {
                            if (parentPos == 0) {
                                // Rule 2: If the child commit is a merge commit and has the current commit as the first parent, put it in the same lane.
                                nodeToLane[i] = childLane
                            } else {
                                // Rule 3: If the child commit is a merge commit and has the current commit as the second parent, assign a new lane.
                                nodeToLane[i] = acquireLane()
                            }
                        } else {
                            // Rule 4: If the child commit is not a merge commit, make the current commit the same lane as the child commit.
                            nodeToLane[i] = childLane
                        }
                    }
                }

                for ((nodeIndex, lane) in nodeToLane) {
                    PersistentLayoutManager.assignLane(nodeIndex, lane)
                }

                logger.info("Successfully computed fixed node-index rules across $nodesCount nodes.")
            } catch (e: Exception) {
                logger.warn("Could not compute fixed commit lane logic: ${e.message}", e)
            }

            val proxyGetter = Proxy.newProxyInstance(
                originalGetter.javaClass.classLoader,
                originalGetter.javaClass.interfaces
            ) { _, method, args ->
                if (method.name == "apply" || method.name == "applyAsInt" || method.name == "invoke") {
                    val nodeIndex = if (args != null && args.isNotEmpty()) args[0] as Int else 0

                    // Find the actual method on the lambda/getter class that accepts an integer
                    val targetMethod = originalGetter.javaClass.methods.firstOrNull {
                        it.parameterCount == 1 && (it.parameterTypes[0] == Int::class.java || it.parameterTypes[0] == Int::class.javaPrimitiveType)
                    } ?: method

                    targetMethod.isAccessible = true
                    val rawLayoutIndex = try {
                        targetMethod.invoke(originalGetter, nodeIndex) as? Int ?: 0
                    } catch (e: Exception) {
                        logger.warn("Failed to invoke original getter method: ${e.message}")
                        0
                    }

                    return@newProxyInstance PersistentLayoutManager.getLaneForNode(nodeIndex, rawLayoutIndex)
                }

                method.isAccessible = true
                method.invoke(originalGetter, *(args ?: emptyArray()))
            }

            getterField.set(comparator, proxyGetter)
            logger.info("Successfully injected Persistent Layout Proxy with explicit rule comments!")

            try {
                val cacheField = printElementGenerator.javaClass.getDeclaredField("cache").apply { isAccessible = true }
                val cache = cacheField.get(printElementGenerator)
                if (cache != null) {
                    cache.javaClass.methods.firstOrNull { it.name == "clear" }?.invoke(cache)
                    logger.info("Cleared PrintElementGeneratorImpl cache.")
                }
            } catch (e: Exception) {
                logger.warn("DEBUG: Failed to clear PrintElementGeneratorImpl cache: ${e.message}")
            }

        } catch (e: Exception) {
            logger.warn("Failed to inject persistent layout getter: ${e.message}", e)
        }
    }
}