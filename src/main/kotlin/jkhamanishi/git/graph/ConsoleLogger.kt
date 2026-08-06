package jkhamanishi.git.graph

import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ConsoleLogger(private val tag: String, private val enabled: Boolean = true) {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun debug(message: String, throwable: Throwable? = null)  = log("DEBUG", message, throwable)
    fun info(message: String, throwable: Throwable? = null)   = log("INFO", message, throwable)
    fun warn(message: String, throwable: Throwable? = null)   = log("WARN", message, throwable)
    fun error(message: String, throwable: Throwable? = null)  = log("ERROR", message, throwable)

    private fun log(level: String, message: String, throwable: Throwable?) {
        if (!enabled) return

        val time = LocalTime.now().format(timeFormatter)

        val fullMessage = if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "$message\n$sw"
        } else {
            message
        }

        // Suppress inspection locally on the internal printer method
        @Suppress("NO_SYSTEM_FROM_LOGGING", "UseOfSystemOutOrSystemErr")
        println("[$time] [$level] [$tag] $fullMessage")
    }
}