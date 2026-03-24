package com.sleepyio.sleepyio.intel.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.min
import kotlin.math.sqrt

data class ThreatLevelResult(
    val overallScore: Int,
    val components: Map<String, Int>,
    val label: String
)

object ThreatLevelModule : SurveillanceInsightModule<ThreatLevelResult> {
    override val id = "threat_level"
    override val displayName = "Threat Assessment"
    override val category = IntelCategory.TACTICAL
    override val requiredTier = DataTier.DEEP

    override suspend fun analyze(
        targetUserId: String,
        myUserId: String,
        leagueHistory: Map<Long, List<SleeperLeague>>,
        repository: LeaguemateRepository
    ): ThreatLevelResult? {
        val opponentRepo = repository as? OpponentRepository ?: return null

        val opponentLeagues = opponentRepo.getOpponentLeagues(targetUserId) ?: return null
        if (opponentLeagues.isEmpty()) return null

        // Component 1: Roster strength (count of starters across leagues, scale 0-25)
        var totalStarters = 0
        for (opponentLeague in opponentLeagues) {
            val leagueId = opponentLeague.league.leagueId
            val rosters = try { opponentRepo.getRosters(leagueId) } catch (_: Exception) { continue }
            val opponentRoster = rosters.find { it.ownerId == targetUserId } ?: continue
            totalStarters += opponentRoster.starters.count { it != "0" }
        }
        // Normalize: assume ~9 starters per league, 3 leagues = 27 is a strong signal
        val rosterStrength = min(25, (totalStarters.toFloat() / (opponentLeagues.size * 9) * 25).toInt())

        // Component 2: Activity level (transaction count across leagues, scale 0-25)
        var totalTransactions = 0
        for (opponentLeague in opponentLeagues) {
            val leagueId = opponentLeague.league.leagueId
            val transactions = try {
                opponentRepo.getLeaguemateTransactions(leagueId, targetUserId)
            } catch (_: Exception) { continue }
            totalTransactions += transactions.size
        }
        // Normalize: 20+ transactions across all leagues is very active
        val activityLevel = min(25, (totalTransactions.toFloat() / 20 * 25).toInt())

        // Component 3: H2H dominance (win rate against you in shared leagues, scale 0-25)
        var h2hWins = 0
        var h2hTotal = 0
        val sharedLeagues = opponentLeagues.filter { it.isShared }
        for (sharedLeague in sharedLeagues) {
            val leagueId = sharedLeague.league.leagueId
            val h2h = try {
                opponentRepo.getHeadToHeadHistoryAllTime(leagueId, targetUserId)
            } catch (_: Exception) { continue }
            val allResults = h2h.values.flatten()
            // H2H is from my perspective, so "won = true" means I won
            h2hWins += allResults.count { !it.won } // opponent wins
            h2hTotal += allResults.size
        }
        val h2hDominance = if (h2hTotal > 0) {
            min(25, (h2hWins.toFloat() / h2hTotal * 25).toInt())
        } else 12 // neutral when no data

        // Component 4: Scoring consistency (lower CV = more threatening, scale 0-25)
        val allWeeklyScores = mutableListOf<Float>()
        val nflState = try { opponentRepo.getNflState() } catch (_: Exception) { null }
        val currentWeek = nflState?.week?.toInt() ?: 1
        for (opponentLeague in opponentLeagues) {
            val leagueId = opponentLeague.league.leagueId
            val rosters = try { opponentRepo.getRosters(leagueId) } catch (_: Exception) { continue }
            val rosterIds = rosters.filter { it.ownerId == targetUserId }.map { it.rosterId.toLong() }.toSet()
            if (rosterIds.isEmpty()) continue

            for (week in 1 until currentWeek) {
                val matchups = try { opponentRepo.getMatchups(leagueId, week) } catch (_: Exception) { continue }
                matchups.find { it.rosterId in rosterIds }?.let { allWeeklyScores.add(it.points) }
            }
        }

        val scoringConsistency = if (allWeeklyScores.size >= 3) {
            val mean = allWeeklyScores.average()
            if (mean > 0) {
                val variance = allWeeklyScores.map { (it - mean) * (it - mean) }.average()
                val cv = sqrt(variance) / mean // coefficient of variation
                // Lower CV = more consistent = more threatening
                // CV of 0.05 = very consistent (25), CV of 0.3+ = volatile (0)
                min(25, ((1.0 - (cv / 0.3).coerceAtMost(1.0)) * 25).toInt())
            } else 0
        } else 12 // neutral when insufficient data

        val overallScore = rosterStrength + activityLevel + h2hDominance + scoringConsistency
        val label = when {
            overallScore > 80 -> "Elite"
            overallScore > 60 -> "Dangerous"
            overallScore > 40 -> "Moderate"
            else -> "Manageable"
        }

        return ThreatLevelResult(
            overallScore = overallScore,
            components = mapOf(
                "Roster Strength" to rosterStrength,
                "Activity Level" to activityLevel,
                "H2H Dominance" to h2hDominance,
                "Scoring Consistency" to scoringConsistency
            ),
            label = label
        )
    }

    @Composable
    override fun Render(data: ThreatLevelResult, modifier: Modifier) {
        val scoreColor = when {
            data.overallScore > 80 -> MaterialTheme.colorScheme.error
            data.overallScore > 60 -> Color(0xFFFF9800)
            data.overallScore > 40 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.tertiary
        }

        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(SleeperSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        fontSize = SleeperType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = data.label,
                        fontSize = SleeperType.caption,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(scoreColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = SleeperSpacing.sm, vertical = SleeperSpacing.xxs)
                    )
                }

                Spacer(modifier = Modifier.height(SleeperSpacing.sm))

                // Large overall score
                Text(
                    text = "${data.overallScore}",
                    fontSize = SleeperType.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
                Text(
                    text = "/ 100",
                    fontSize = SleeperType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(SleeperSpacing.md))

                // Component progress bars
                data.components.forEach { (name, value) ->
                    Column(modifier = Modifier.padding(vertical = SleeperSpacing.xxs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = name,
                                fontSize = SleeperType.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$value/25",
                                fontSize = SleeperType.caption,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { value / 25f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = scoreColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
