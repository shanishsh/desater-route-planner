package com.touristsafety.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.model.AlertSeverity

/**
 * Standardized severity chip for hazards, alerts, and safety status.
 * Uses strict semantic coloring.
 */
@Composable
fun AppSeverityChip(
    severity: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (severity.uppercase()) {
        "CRITICAL", "EXTREME", "HIGH" -> Triple(EmergencyRedContainer, EmergencyRed, severity.uppercase())
        "WARNING", "MEDIUM", "CAUTION" -> Triple(WarningAmberContainer, WarningAmber, severity.uppercase())
        "SAFE", "NORMAL", "LOW" -> Triple(SafeGreenContainer, SafeGreen, severity.uppercase())
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, severity.uppercase())
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AppSeverityChip(
    severity: AlertSeverity,
    modifier: Modifier = Modifier
) {
    AppSeverityChip(severity = severity.name, modifier = modifier)
}

/**
 * Status badge for system conditions (Offline, Live GPS, Synced, Simulation).
 */
@Composable
fun AppStatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isWarning: Boolean = false,
    isSafe: Boolean = false
) {
    val bgColor = when {
        isWarning -> WarningAmberContainer
        isSafe -> SafeGreenContainer
        else -> SageContainer
    }
    val contentColor = when {
        isWarning -> WarningAmber
        isSafe -> SafeGreen
        else -> OnSageContainer
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

