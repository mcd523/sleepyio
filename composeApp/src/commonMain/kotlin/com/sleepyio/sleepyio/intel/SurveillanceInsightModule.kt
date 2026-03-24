package com.sleepyio.sleepyio.intel

import com.sleepyio.sleepyio.intel.model.DataTier
import com.sleepyio.sleepyio.intel.model.IntelCategory

interface SurveillanceInsightModule<T> : InsightModule<T> {
    val category: IntelCategory
    val requiredTier: DataTier
}
