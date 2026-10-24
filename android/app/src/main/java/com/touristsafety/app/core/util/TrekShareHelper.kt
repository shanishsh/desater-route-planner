package com.touristsafety.app.core.util

import android.content.Context
import android.content.Intent
import com.touristsafety.app.core.database.entity.PlannedRouteEntity

object TrekShareHelper {

    fun generateShareableTrekSummary(route: PlannedRouteEntity): String {
        return buildString {
            appendLine("🏔️ TRAILGUARD UTTARAKHAND TREK PLAN")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Destination: ${route.destinationName}")
            appendLine("Departure Point: ${route.originName}")
            appendLine("Travel Mode: ${route.travelMode.replaceFirstChar { it.uppercase() }}")
            appendLine("Total Distance: ${route.distanceKm} km")
            appendLine("Estimated Travel Time: ~${route.durationMinutes.toInt()} minutes")
            appendLine("Safety Evaluation: ${route.overallSafetyStatus}")
            appendLine()
            appendLine("📡 Safety & Check-in Schedule:")
            appendLine("• Check-in confirmation scheduled every 3 hours.")
            appendLine("• Emergency escalation activates if check-in is overdue by > 60m.")
            appendLine("• Route ID: ${route.routeId.take(8)}")
            appendLine()
            appendLine("Shared via TrailGuard Tourist Safety Platform (Uttarakhand, India)")
        }
    }

    fun shareTrekPlan(context: Context, route: PlannedRouteEntity) {
        val summary = generateShareableTrekSummary(route)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Trek Safety Plan: ${route.destinationName}")
            putExtra(Intent.EXTRA_TEXT, summary)
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Trek Plan with Emergency Contacts")
        context.startActivity(shareIntent)
    }
}

