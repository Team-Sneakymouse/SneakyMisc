package com.danidipp.sneakymisc.dclock

import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.variables.variabletypes.GlobalVariable

interface ClockSource {
    fun currentValue(nowMillis: Long): ClockValue?
}

sealed interface ClockValue {
    data class TimeLeft(val secondsRemaining: Long) : ClockValue
    data class Literal(val value: Long) : ClockValue
}

class PocketBaseClockSource(
    val recordId: String,
    private val valueMode: SourceValueMode,
) : ClockSource {
    @Volatile
    var sourceValue: Long? = null

    override fun currentValue(nowMillis: Long): ClockValue? {
        val value = sourceValue ?: return null
        return when (valueMode) {
            SourceValueMode.UNIX_SECONDS -> ClockValue.TimeLeft((value * 1000L - nowMillis) / 1000L)
            SourceValueMode.LITERAL -> ClockValue.Literal(value)
        }
    }
}

class MagicSpellsClockSource(
    val variableName: String,
    private val valueMode: SourceValueMode,
) : ClockSource {
    override fun currentValue(nowMillis: Long): ClockValue? {
        val variable = MagicSpells.getVariableManager().getVariable(variableName) as? GlobalVariable ?: return null
        val value = variable.getValue("null").toString().toLongOrNull() ?: return null
        return when (valueMode) {
            SourceValueMode.UNIX_SECONDS -> ClockValue.TimeLeft((value * 1000L - nowMillis) / 1000L)
            SourceValueMode.LITERAL -> ClockValue.Literal(value)
        }
    }
}
