package com.sleepyio.sleepyio.intel

import com.sleepyio.sleepyio.intel.modules.DraftTendencyModule
import com.sleepyio.sleepyio.intel.modules.HeadToHeadModule
import com.sleepyio.sleepyio.intel.modules.RosterCompositionModule
import com.sleepyio.sleepyio.intel.modules.ScoringTrendModule
import com.sleepyio.sleepyio.intel.modules.TransactionActivityModule
import com.sleepyio.sleepyio.intel.modules.VulnerabilityModule

object InsightRegistry {
    private val modules = mutableListOf<InsightModule<*>>()

    fun register(module: InsightModule<*>) {
        modules.add(module)
    }

    fun getAll(): List<InsightModule<*>> = modules.toList()

    fun getById(id: String): InsightModule<*>? = modules.find { it.id == id }

    init {
        register(HeadToHeadModule)
        register(VulnerabilityModule)
        register(TransactionActivityModule)
        register(DraftTendencyModule)
        register(ScoringTrendModule)
        register(RosterCompositionModule)
    }
}
