package com.nophubbing.presenceai.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PresenceCircle(score: Int) {

    Box(
        modifier = Modifier
            .size(200.dp)
            .border(6.dp, Color.LightGray, CircleShape),
        contentAlignment = Alignment.Center
    ) {

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text(
                text = score.toString(),
                fontSize = 40.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text("Presence Score", color = MaterialTheme.colorScheme.onBackground)
        }
    }
}