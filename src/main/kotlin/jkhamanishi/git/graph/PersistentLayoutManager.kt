@file:Suppress("UnstableApiUsage")

package jkhamanishi.git.graph

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import javax.swing.JTable

object PersistentLayoutManager {

    private val logger = ConsoleLogger("PersistentLayoutManager")
    private val laneCache = HashMap<Int, Int>()

    fun clear() {
        laneCache.clear()
    }

    fun assignLane(nodeIndex: Int, lane: Int) {
        laneCache[nodeIndex] = lane
    }

    fun getLaneForNode(nodeIndex: Int, defaultLane: Int): Int {
        return laneCache[nodeIndex] ?: defaultLane
    }

    fun applyLayout(swingTable: JTable) {
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
            clear()
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
                val nodesCount = resolveNodesCount(visibleGraph, visiblePack)
                if (nodesCount <= 0) {
                    logger.warn("All node count strategies returned 0 or invalid count ($nodesCount). Skipping lane layout calculation.")
                    return
                }
                logger.info("VisibleGraph node count successfully resolved: $nodesCount nodes found.")

                val getNodeMethod = resolveNodeGetterMethod(visibleGraph) ?: return

                val nodeToLane = CommitLaneCalculator.computeNodeLanes(visibleGraph, nodesCount, getNodeMethod)

                for ((nodeIndex, lane) in nodeToLane) {
                    assignLane(nodeIndex, lane)
                }

                logger.info("Successfully computed fixed node-index rules across $nodesCount nodes.")
            } catch (e: Exception) {
                logger.warn("Could not compute fixed commit lane logic: ${e.message}", e)
            }

            val proxyGetter = createProxyGetter(originalGetter)
            getterField.set(comparator, proxyGetter)
            logger.info("Successfully injected Persistent Layout Proxy with explicit rule comments!")

            clearGeneratorCache(printElementGenerator)

        } catch (e: Exception) {
            logger.warn("Failed to inject persistent layout getter: ${e.message}", e)
        }
    }

    private fun resolveNodesCount(visibleGraph: Any, visiblePack: Any): Int {
        val countMethod = visibleGraph.javaClass.methods.firstOrNull {
            it.parameterCount == 0 && (it.name == "getVisibleNodesCount" || it.name == "getNodesCount" || it.name == "nodesCount")
        }
        if (countMethod != null) {
            val count = countMethod.invoke(visibleGraph) as? Int ?: 0
            if (count > 0) return count
        }

        val genericCountMethod = visibleGraph.javaClass.methods.firstOrNull {
            it.parameterCount == 0 && (it.returnType == Int::class.java || it.returnType == Int::class.javaPrimitiveType) &&
                    (it.name.contains("count", ignoreCase = true) || it.name.contains("size", ignoreCase = true))
        }
        if (genericCountMethod != null) {
            val count = genericCountMethod.invoke(visibleGraph) as? Int ?: 0
            if (count > 0) return count
        }

        val packSizeMethod = visiblePack.javaClass.methods.firstOrNull {
            it.parameterCount == 0 && (it.returnType == Int::class.java || it.returnType == Int::class.javaPrimitiveType) &&
                    it.name.contains("size", ignoreCase = true)
        }
        return packSizeMethod?.invoke(visiblePack) as? Int ?: 0
    }

    private fun resolveNodeGetterMethod(visibleGraph: Any): Method? {
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
            return null
        }

        getNodeMethod.isAccessible = true
        logger.info("Successfully resolved node getter method: ${getNodeMethod.name} returning ${getNodeMethod.returnType.name}")
        return getNodeMethod
    }

    private fun createProxyGetter(originalGetter: Any): Any {
        return Proxy.newProxyInstance(
            originalGetter.javaClass.classLoader,
            originalGetter.javaClass.interfaces
        ) { _, method, args ->
            if (method.name == "apply" || method.name == "applyAsInt" || method.name == "invoke") {
                val nodeIndex = if (args != null && args.isNotEmpty()) args[0] as Int else 0

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

                return@newProxyInstance getLaneForNode(nodeIndex, rawLayoutIndex)
            }

            method.isAccessible = true
            method.invoke(originalGetter, *(args ?: emptyArray()))
        }
    }

    private fun clearGeneratorCache(printElementGenerator: Any) {
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
    }
}