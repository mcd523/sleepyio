package com.sleepyio.sleepyio.intel.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepyio.sleepyio.SleeperSpacing
import com.sleepyio.sleepyio.SleeperType
import com.sleepyio.sleepyio.client.model.league.SleeperLeague
import com.sleepyio.sleepyio.intel.LeaguemateRepository
import com.sleepyio.sleepyio.intel.OpponentRepository
import com.sleepyio.sleepyio.intel.SurveillanceInsightModule
import com.sleepyio.sleepyio.intel.model.DataTier
import com.sleepyio.sleepyio.intel.model.IntelCategory
import kotlin.math.roundToInt

data class FAABIntelResult(
    val totalWaiverClaims: Int,
    val totalFreeAgentAdds: Int,
    val waiverRatio: Float,
    val spendingProfile: String
)

object FAABIntelModule : SurveillanceInsightModule<FAABIntelResult> {
    override val id = "faab_intel"
    override val displayName = "FAAB Intelligence"
    override val category = IntelCategory.TACTICAL
    override val requiredTier = DataTier.METADATA

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): FAABIntelResult? {
        val opponentRepo = repository as? OpponentRepository ?: return null

        val opponentLeagues = opponentRepo.getOpponentLeagues(targetUserId) ?: return null
        if (opponentLeagues.isEmpty()) return null

        var totalWaiverClaims = 0
        var totalFreeAgentAdds = 0

        for (opponentLeague in opponentLeagues) {
            val leagueId = opponentLeague.league.leagueId
            val metadata = opponentRepo.fetchShadowMetadata(leagueId) ?: continue
            val opponentRoster = metadata.rosters.find { it.ownerId == targetUserId } ?: continue
            val rosterIds = setOf(opponentRoster.rosterId)

            val transactions = opponentRepo.getAllTransactions(leagueId)
            val opponentTx = transactions.filter { tx ->
                tx.rosterIds.any { it in rosterIds }
            }

            for (tx in opponentTx) {
                when (tx.type) {
                    "waiver" -> totalWaiverClaims++
                    "free_agent" -> totalFreeAgentAdds++
                }
            }
        }

        val totalMoves = totalWaiverClaims + totalFreeAgentAdds
        if (totalMoves == 0) return null

        val waiverRatio = totalWaiverClaims.toFloat() / totalMoves

        val spendingProfile = when {
            waiverRatio > 0.6f -> "aggressive"
            waiverRatio > 0.3f -> "balanced"
            else -> "patient"
        }

        return FAABIntelResult(
            totalWaiverClaims = totalWaiverClaims,
            totalFreeAgentAdds = totalFreeAgentAdds,
            waiverRatio = waiverRatio,
            spendingProfile = spendingProfile
        )
    }

    @Composable
    override fun Render(data: FAABIntelResult, modifier: Modifier) {
        val profileColor = when (data.spendingProfile) {
            "aggressive" -> MaterialTheme.colorScheme.error
            "balanced" -> Color(0xFFFF9800)
            else -> Color(0xFF4CAF50)
        }

        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(SleeperSpacing.md)) {
                Text(
                    text = displayName,
                    fontSize = SleeperType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                ) {
                    StatColumn(
                        label = "Waiver Claims",
                        value = data.totalWaiverClaims.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatColumn(
                        label = "Free Agent Adds",
                        value = data.totalFreeAgentAdds.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                // Spending profile label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SleeperSpacing.sm)
                ) {
                    Text(
                        text = "Profile:",
                        fontSize = SleeperType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = data.spendingProfile.replaceFirstChar { it.uppercase() },
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(profileColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = SleeperSpacing.xs, vertical = SleeperSpacing.xxs)
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                // Ratio bar
                val ratioPct = (data.waiverRatio * 100).roundToInt()
                Text(
                    text = "Waiver usage: $ratioPct%",
                    fontSize = SleeperType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(SleeperSpacing.xxs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            RoundedCornerShape(3.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(data.waiverRatio)
                            .height(6.dp)
                            .background(profileColor, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = SleeperType.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = SleeperType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
