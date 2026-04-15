package com.danidipp.sneakymisc.metaoverlayhelper

import com.danidipp.sneakymisc.SneakyMiscCommand
import com.danidipp.sneakymisc.SneakyModule
import com.danidipp.sneakymisc.metaoverlayhelper.commands.MetaOverlayHelperCommand
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.filter.AbstractFilter
import org.apache.logging.log4j.message.Message

class MetaOverlayHelper(logger: java.util.logging.Logger): SneakyModule() {
    companion object { val deps = listOf<String>("SneakyCharacterManager") }
    init {
        try {
            val log4j = LogManager.getRootLogger() as Logger
            log4j.addFilter(object: AbstractFilter() {
                fun validateMessage(message: String?): Filter.Result {
                    if (message == null) return Filter.Result.NEUTRAL
                    if (message.contains("/metaoverlayhelper")) return Filter.Result.DENY
                    return Filter.Result.NEUTRAL
                }
                override fun filter(event: LogEvent?): Filter.Result {
                    return validateMessage(event?.message?.formattedMessage)
                }
                override fun filter(logger: Logger?, level: Level?, marker: Marker?, msg: Message?, t: Throwable?): Filter.Result {
                    return validateMessage(msg?.formattedMessage)
                }
                override fun filter(logger: Logger?, level: Level?, marker: Marker?, msg: String?, vararg params: Any? ): Filter.Result {
                    return validateMessage(msg)
                }
                override fun filter(logger: Logger?, level: Level?, marker: Marker?, msg: Any?, t: Throwable?): Filter.Result {
                    return validateMessage(msg?.toString())
                }
            })
        } catch (e: Exception) {
            logger.warning("Failed to get log4j logger")
        }
    }
    override val commands: List<SneakyMiscCommand> = listOf(
        SneakyMiscCommand(MetaOverlayHelperCommand().build(), "Meta Overlay Helper command")
    )
}