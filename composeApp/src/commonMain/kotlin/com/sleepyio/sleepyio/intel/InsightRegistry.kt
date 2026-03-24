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
import com.sleepyio.sleepyio.intel.modules.TradeNetworkModule
import com.sleepyio.sleepyio.intel.modules.AnomalyDetectorModule
import com.sleepyio.sleepyio.intel.modules.ThreatLevelModule
import com.sleepyio.sleepyio.intel.modules.CounterIntelModule

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

    /**
     * Returns surveillance modules in two-pass execution order:
     * Pass 1: all modules except ThreatLevel (independent analyses)
     * Pass 2: ThreatLevel last (composite scoring from raw data)
     */
    fun getTwoPassModules(): List<SurveillanceInsightModule<*>> {
        val all = getSurveillanceModules()
        val (threatModules, otherModules) = all.partition { it.id == ThreatLevelModule.id }
        return otherModules + threatModules
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

        // Phase 4: Advanced modules (Tier 3 / Deep)
        register(TradeNetworkModule)
        register(AnomalyDetectorModule)
        register(CounterIntelModule)
        register(ThreatLevelModule)
    }
}
