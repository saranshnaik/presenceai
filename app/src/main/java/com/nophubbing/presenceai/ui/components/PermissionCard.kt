package com.nophubbing.presenceai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PermissionCard(
    title: String,
    description: String,
    required: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column(modifier = Modifier.weight(1f)) {

                Row {

                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold
                    )

                    if (required) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Required")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(description)
            }

            Switch(
                checked = enabled,
                onCheckedChange = {
                    onToggle()
                }
            )
        }
    }
}