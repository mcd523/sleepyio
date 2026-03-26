package com.sleepyio.sleepyio.client.model.stats

import kotlinx.serialization.Serializable

@Serializable
data class PlayerStats(
    val pts_ppr: Double? = null,
    val pts_half_ppr: Double? = null,
    val pts_std: Double? = null,
    val pass_yd: Double? = null,
    val pass_td: Double? = null,
    val pass_int: Double? = null,
    val pass_att: Double? = null,
    val pass_cmp: Double? = null,
    val rush_yd: Double? = null,
    val rush_td: Double? = null,
    val rush_att: Double? = null,
    val rec: Double? = null,
    val rec_yd: Double? = null,
    val rec_td: Double? = null,
    val rec_tgt: Double? = null,
    val fum_lost: Double? = null,
    val fum: Double? = null,
    val snp: Double? = null,
    val tm_snp: Double? = null,
    // Kicker stats
    val fgm: Double? = null,
    val fga: Double? = null,
    val xpm: Double? = null,
    val xpa: Double? = null,
    // Defense stats
    val def_td: Double? = null,
    val sack: Double? = null,
    val int: Double? = null,
    val fum_rec: Double? = null,
    val pts_allow: Double? = null,
    val safe: Double? = null,
    val blk_kick: Double? = null
) {
    val fantasyPoints: Double
        get() = pts_ppr ?: pts_half_ppr ?: pts_std ?: 0.0

    val snapSharePct: Double?
        get() = if (snp != null && tm_snp != null && tm_snp > 0) (snp / tm_snp) * 100 else null

    val targetShare: Double?
        get() = null // Would need team-level target data to calculate
}
