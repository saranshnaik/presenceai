package com.nophubbing.presenceai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nophubbing.presenceai.ai.GeminiService
import com.nophubbing.presenceai.ai.PresenceHistoryManager
import com.nophubbing.presenceai.analytics.InsightsRepository
import com.nophubbing.presenceai.ui.components.*
import com.nophubbing.presenceai.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun InsightsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("Tap the button to generate personalized AI insights based on your usage data.") }
    var comparisonText by remember { mutableStateOf("") }
    val history = remember { PresenceHistoryManager.getHistoryList(context) }

    LaunchedEffect(Unit) {
        comparisonText = InsightsRepository.getWeeklyComparison(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            "AI BEHAVIORAL INSIGHTS",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(16.dp))

        // Comparative Summary Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(BgCard, Color(0xFF0D0A28))))
                .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Text("Trend Summary", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(comparisonText, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        // AI Insight Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF060412))
                .border(1.dp, PresencePurple.copy(0.3f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, "AI", tint = PresencePurple, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Gemini Pro Analysis", color = PresencePurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(Modifier.height(16.dp))

                if (isLoading) {
                    CircularProgressIndicator(color = PresencePurple, modifier = Modifier.size(32.dp))
                } else {
                    Text(
                        resultText,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            val summary = InsightsRepository.getWeeklyComparison(context)
                            resultText = GeminiService.generateInsights(context, summary)
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = PresencePurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Regenerate Insights")
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))

        // ── Historical Visualization ──────────────────────────────────
        Text(
            "HISTORICAL TRENDS",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BgCard)
                .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                PresenceLineChart(history = history, modifier = Modifier.fillMaxWidth())
                
                Spacer(Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    ScoreBarChart(history = history, modifier = Modifier.weight(1.2f))
                    Spacer(Modifier.width(16.dp))
                    NudgeAcceptancePieChart(
                        acceptanceRate = history.lastOrNull()?.acceptanceRate ?: 0,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
