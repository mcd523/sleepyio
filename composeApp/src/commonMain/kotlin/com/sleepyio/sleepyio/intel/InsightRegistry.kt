package com.sleepyio.sleepyio.intel

import com.sleepyio.sleepyio.intel.model.IntelCategory
import com.sleepyio.sleepyio.intel.modules.DraftTendencyModule
import com.sleepyio.sleepyio.intel.modules.HeadToHeadModule
import com.sleepyio.sleepyio.intel.modules.RosterCompositionModule
import com.sleepyio.sleepyio.intel.modules.ScoringTrendModule
import com.sleepyio.sleepyio.intel.modules.TransactionActivityModule
import com.sleepyio.sleepyio.intel.modules.VulnerabilityModule
import com.sleepyio.sleepyio.intel.modules.ConvergentInterestModule
import com.sleepyio.sleepyio.intel.modules.ShadowRosterModule
import com.sleepyio.sleepyio.intel.modules.FAABIntelModule
import com.sleepyio.sleepyio.intel.modules.WaiverPatternModule

object InsightRegistry {
    private val modules = mutableListOf<InsightModule<*>>()

    fun register(module: InsightModule<*>) {
        modules.add(module)
    }

    fun getAll(): List<InsightModule<*>> = modules.toList()

    fun getById(id: String): InsightModule<*>? = modules.find { it.id == id }

    fun getByCategory(category: IntelCategory): List<InsightModule<*>> {
        return modules.filter { module ->
            module is SurveillanceInsightModule<*> && module.category == category
        }
    }

    fun getSurveillanceModules(): List<SurveillanceInsightModule<*>> {
        return modules.filterIsInstance<SurveillanceInsightModule<*>>()
    }

    init {
        register(HeadToHeadModule)
        register(VulnerabilityModule)
        register(TransactionActivityModule)
        register(DraftTendencyModule)
        register(ScoringTrendModule)
        register(RosterCompositionModule)

        // Phase 2: Espionage modules
        register(ConvergentInterestModule)
        register(ShadowRosterModule)
        register(FAABIntelModule)
        register(WaiverPatternModule)
    }
}
