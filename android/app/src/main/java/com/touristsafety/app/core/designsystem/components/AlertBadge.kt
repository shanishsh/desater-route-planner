package com.touristsafety.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.model.AlertSeverity

@Composable
fun AlertSeverityBadge(severity: AlertSeverity, modifier: Modifier = Modifier) {
    val (bgColor, label) = when (severity) {
        AlertSeverity.LOW -> AlertLowBlue to "LOW"
        AlertSeverity.MEDIUM -> AlertMediumAmber to "MEDIUM"
        AlertSeverity.HIGH -> AlertHighOrange to "HIGH"
        AlertSeverity.CRITICAL -> AlertCriticalRed to "CRITICAL"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun FreshnessPill(
    isStale: Boolean,
    ageMinutes: Long,
    modifier: Modifier = Modifier
) {
    val (bgColor, text) = if (isStale) {
        StaleGray to "STALE • ${ageMinutes}m ago"
    } else {
        FreshGreen to "FRESH • ${ageMinutes}m ago"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = bgColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun SimulationBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SimulationPurple.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "SIMULATED",
            color = SimulationPurple,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

