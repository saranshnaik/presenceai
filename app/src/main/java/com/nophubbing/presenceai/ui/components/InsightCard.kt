package com.nophubbing.presenceai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InsightCard() {

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text("Daily Insight")

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Your presence score peaks between 10am–12pm. Consider scheduling important conversations during this window."
            )
        }
    }
}