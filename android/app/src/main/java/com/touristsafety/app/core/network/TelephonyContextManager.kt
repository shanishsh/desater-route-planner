package com.touristsafety.app.core.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class TelephonyCoverageState(
    val networkType: String,
    val carrierName: String,
    val signalLevel: Int, // 0 to 4 bars
    val isEmergencyOnly: Boolean,
    val disclaimer: String = "Local handset telemetry only. Base station functionality and emergency call routing during active disasters cannot be guaranteed."
)

@Singleton
class TelephonyContextManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun getLocalCoverageState(): TelephonyCoverageState {
        val tm = telephonyManager ?: return TelephonyCoverageState(
            networkType = "Unavailable",
            carrierName = "No Telephony Hardware",
            signalLevel = 0,
            isEmergencyOnly = true
        )

        val carrier = try {
            tm.networkOperatorName.ifBlank { "Unknown Carrier" }
        } catch (e: Exception) {
            "Unknown Carrier"
        }

        val hasPhonePermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val networkTypeStr = if (hasPhonePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                when (tm.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS/HSPA"
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_GPRS -> "2G GSM/EDGE"
                    else -> "Cellular (Standard)"
                }
            } catch (e: SecurityException) {
                "Cellular (Permission Restricted)"
            } catch (e: Exception) {
                "Cellular (Standard)"
            }
        } else {
            "Cellular (Permission Restricted)"
        }

        // On Android 9+, tm.signalStrength provides getLevel()
        val signalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                tm.signalStrength?.level ?: 2
            } catch (e: Exception) {
                2
            }
        } else {
            2
        }

        val isEmergency = try {
            if (hasPhonePermission) {
                @Suppress("DEPRECATION")
                tm.networkType == TelephonyManager.NETWORK_TYPE_UNKNOWN
            } else {
                false
            }
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }

        return TelephonyCoverageState(
            networkType = networkTypeStr,
            carrierName = carrier,
            signalLevel = signalLevel,
            isEmergencyOnly = isEmergency
        )
    }
}

