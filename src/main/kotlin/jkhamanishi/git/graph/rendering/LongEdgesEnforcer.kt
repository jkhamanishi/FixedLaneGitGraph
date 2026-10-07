@file:Suppress("UnstableApiUsage")

package jkhamanishi.git.graph.rendering

import com.intellij.ide.DataManager
import jkhamanishi.git.graph.ConsoleLogger
import java.awt.Container
import javax.swing.JTable

object LongEdgesEnforcer {

    private val logger = ConsoleLogger("LongEdgesEnforcer", false)

    fun enableLongEdgesViaProperty(swingTable: JTable) {
        try {
            logger.info("Entering enableLongEdgesViaProperty...")
            var properties: Any? = null
            var vcsLogUi: Any? = null

            // 1. Attempt to find VcsLogUi / VcsLogUiProperties via parent Component hierarchy
            var parent: Container? = swingTable.parent
            while (parent != null) {
                val members = mutableListOf<Any>()
                members.addAll(parent.javaClass.methods)

                for (field in parent.javaClass.declaredFields) {
                    try {
                        field.isAccessible = true
                        members.add(field)
                    } catch (_: Exception) {}
                }

                for (member in members) {
                    try {
                        val value = when (member) {
                            is java.lang.reflect.Method -> if (member.parameterCount == 0 && member.name.contains("Log", ignoreCase = true)) member.invoke(parent) else null
                            is java.lang.reflect.Field -> member.get(parent)
                            else -> null
                        }

                        if (value != null) {
                            if (value.javaClass.name.contains("VcsLogUiProperties", ignoreCase = true)) {
                                properties = value
                            } else if (value.javaClass.name.contains("VcsLogUi", ignoreCase = true) && vcsLogUi == null) {
                                vcsLogUi = value
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (properties != null) break
                parent = parent.parent
            }

            // 2. Extract properties from vcsLogUi if found
            if (properties == null && vcsLogUi != null) {
                val getPropertiesMethod = vcsLogUi.javaClass.methods.firstOrNull {
                    it.name.contains("Properties", ignoreCase = true)
                }
                if (getPropertiesMethod != null) {
                    getPropertiesMethod.isAccessible = true
                    properties = getPropertiesMethod.invoke(vcsLogUi)
                }
            }

            // 3. Fallback: DataKeys
            if (properties == null) {
                val dataContext = DataManager.getInstance().getDataContext(swingTable)
                val keyClasses = listOf(
                    "com.intellij.vcs.log.ui.VcsLogInternalDataKeys",
                    "com.intellij.vcs.log.VcsLogDataKeys"
                )

                for (className in keyClasses) {
                    try {
                        val keyClass = Class.forName(className)
                        for (field in keyClass.declaredFields) {
                            field.isAccessible = true
                            val dataKey = field.get(null) ?: continue

                            val getDataMethod = dataContext.javaClass.methods.firstOrNull {
                                it.name == "getData" && it.parameterTypes.firstOrNull()?.name?.contains("DataKey") == true
                            }

                            val result = getDataMethod?.invoke(dataContext, dataKey)
                            if (result != null) {
                                if (result.javaClass.name.contains("Properties")) {
                                    properties = result
                                    break
                                } else if (result.javaClass.name.contains("Ui")) {
                                    vcsLogUi = result
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    if (properties != null) break
                }
            }

            if (properties == null) {
                logger.warn("Unable to resolve VcsLogUiProperties via Component tree or DataKeys.")
                return
            }

            logger.info("Successfully resolved VcsLogUiProperties instance: ${properties.javaClass.name}")

            // 4. Locate the exact SHOW_LONG_EDGES property field
            val fields = properties.javaClass.fields + properties.javaClass.declaredFields
            val layoutPropField = fields.firstOrNull {
                it.name == "SHOW_LONG_EDGES"
            }?.apply { isAccessible = true }

            if (layoutPropField == null) {
                logger.warn("Could not locate SHOW_LONG_EDGES property field.")
                return
            }

            val propertyKey = layoutPropField.get(properties) ?: layoutPropField.get(null) ?: return
            logger.info("Successfully identified property key -> $propertyKey")

            val getMethod = properties.javaClass.methods.firstOrNull { it.name == "get" && it.parameterCount == 1 }
            val setMethod = properties.javaClass.methods.firstOrNull { it.name == "set" && it.parameterCount == 2 }

            if (getMethod != null) {
                getMethod.isAccessible = true // <-- Fix: explicitly permit access
                val initialVal = getMethod.invoke(properties, propertyKey)
                logger.info("Current SHOW_LONG_EDGES property BEFORE update: $initialVal")
            }

            if (setMethod != null) {
                setMethod.isAccessible = true // <-- Fix: explicitly permit access
                // Toggle off then on to force internal UI event dispatching
                setMethod.invoke(properties, propertyKey, false)
                setMethod.invoke(properties, propertyKey, true)
                logger.info("Enforced SHOW_LONG_EDGES = true transition on VcsLogUiProperties.")
            }

            if (vcsLogUi != null) {
                val candidateMethods = listOf("requestOptionUpdate", "refilter", "refresh", "jumpToRow")
                for (name in candidateMethods) {
                    val method = vcsLogUi.javaClass.methods.firstOrNull { it.name == name }
                    if (method != null && method.parameterCount == 0) {
                        method.isAccessible = true
                        method.invoke(vcsLogUi)
                        logger.info("Invoked $name() on VcsLogUi.")
                    }
                }
            }

            swingTable.revalidate()
            swingTable.repaint()

        } catch (e: Exception) {
            logger.warn("Exception in enableLongEdgesViaProperty: ${e.javaClass.name}: ${e.message}", e)
        }
    }
}

