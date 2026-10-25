# Crash and Android Runtime Reliability Audit

**Repository Commit**: `8403e98`  
**Active Branch**: `main`  
**Audit Priority**: High (Application Stability & Crash Prevention)

---

## 1. Investigation of Historical Startup Crash

### Stack Trace Analysis
```
java.lang.SecurityException: getDataNetworkTypeForSubscriber
    at android.os.Parcel.createExceptionOrNull(Parcel.java:3011)
    at android.os.Parcel.createException(Parcel.java:2995)
    at android.os.Parcel.readException(Parcel.java:2978)
    at android.os.Parcel.readException(Parcel.java:2920)
    at com.android.internal.telephony.ITelephony$Stub$Proxy.getDataNetworkTypeForSubscriber(ITelephony.java:9842)
    at android.telephony.TelephonyManager.getDataNetworkType(TelephonyManager.java:3421)
    at com.touristsafety.app.core.network.TelephonyContextManager.getLocalCoverageState(TelephonyContextManager.kt:65)
    at com.touristsafety.app.feature.providers.ProviderStatusViewModel.<init>(ProviderStatusViewModel.kt:46)
    at com.touristsafety.app.navigation.NavGraphKt$AppNavGraph$1$10.invoke(NavGraph.kt:225)
```

### Root Cause
1. **Privileged API Usage**: On Android 7.0 (API 24) through Android 14 (API 34), invoking `TelephonyManager.getDataNetworkType()` or `TelephonyManager.getNetworkType()` requires the dangerous runtime permission `android.Manifest.permission.READ_PHONE_STATE`.
2. **Eager ViewModel Initialization**: In `ProviderStatusViewModel.kt`, line 46:
   ```kotlin
   private val _uiState = MutableStateFlow(
       ProviderStatusUiState(
           currentBaseUrl = serverConfigManager.getCurrentUrl(),
           telephony = telephonyManager.getLocalCoverageState() // CRITICAL FAILURE POINT
       )
   )
   ```
   Because `getLocalCoverageState()` was called during instance construction when Jetpack Navigation inflated the destination, the app crashed immediately upon opening the Provider Status screen or entering navigation on any phone without `READ_PHONE_STATE` granted.

### Audit of Current Mitigation in `TelephonyContextManager.kt`
Lines 42-66 of `TelephonyContextManager.kt`:
```kotlin
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
```
- **Verification**: `ProviderStatusViewModelTest.kt` passes with 100% success.
- **Architectural Recommendation**: The ViewModel should never invoke synchronous hardware queries during property construction. Telephony state should be observed asynchronously via `viewModelScope.launch(Dispatchers.IO)` and updated into `_uiState` as a flow or coroutine response.

---

## 2. Android Permissions and Telephony Audit

| Permission | Declared in Manifest | Protection Level | Runtime Check Implemented | Fallback Behavior when Denied |
| :--- | :--- | :--- | :--- | :--- |
| `ACCESS_FINE_LOCATION` | Yes | Dangerous | Yes (`LocationHelper.kt`, `DeviceLocationRepository.kt`) | Falls back to `ACCESS_COARSE_LOCATION` or manual map search. |
| `ACCESS_COARSE_LOCATION` | Yes | Dangerous | Yes | Allows city/settlement level planning without exact trail GPS. |
| `READ_PHONE_STATE` | No | Dangerous | Yes (`TelephonyContextManager.kt`) | Not declared in manifest to preserve user privacy; returns `"Cellular (Permission Restricted)"`. |
| `POST_NOTIFICATIONS` | Yes (API 33+) | Dangerous | Yes (`PermissionOnboardingScreen.kt`) | Warnings are rendered inside app UI and saved to local Room alert table. |
| `CALL_PHONE` | No | Dangerous | N/A | Application does NOT request `CALL_PHONE`. Instead, `EmergencyScreen.kt` fires `Intent.ACTION_DIAL` with `tel:112`, which delegates to the native dialer without requiring phone call permissions. |
| `INTERNET` | Yes | Normal | Granted at install | Offline mode activates if network is unavailable. |
| `ACCESS_NETWORK_STATE` | Yes | Normal | Granted at install | Monitored by `NetworkConnectivityMonitor` via `ConnectivityManager.NetworkCallback`. |

---

## 3. Lifecycle, Threading, and Coroutine Safety

### A. Main-Thread I/O Audit
- **Room Database Access**: All Room DAOs (`PlannedRouteDao`, `MapPointDao`, `AlertDao`) are marked with `suspend` or return `kotlinx.coroutines.flow.Flow`. Room automatically executes queries on an internal background dispatcher, preventing `IllegalStateException: Cannot access database on the main thread`.
- **Network Calls**: Retrofit `ApiService` interface methods are all `suspend fun`, dispatched via OkHttp background thread pools.
- **Location Fixes**: Handled with `CancellationTokenSource` and Google Play Services Location SDK `getCurrentLocation(PRIORITY_HIGH_ACCURACY, token)`. Handled asynchronously in `DeviceLocationRepository.kt`.

### B. Coroutine Scope Cancellation & Leak Audit
- ViewModels consistently use `viewModelScope`, ensuring that network and database operations are automatically cancelled when the user navigates away from a screen.
- Long-running tracking jobs in `ActiveTripViewModel` are scoped to the trip lifecycle and tied to the active foreground service.
- **WorkManager**: `TouristSafetyApp.kt` disables the default automatic WorkManager initialization (`tools:node="remove"` in `AndroidManifest.xml`) and implements `Configuration.Provider` with Dagger-Hilt `HiltWorkerFactory` to prevent initialization races.

### C. State Restoration & Process Death
- ViewModels utilize `StateFlow` and Room database reactivity.
- When Android kills the application process in the background under memory pressure, `ActiveTripDao` retains the active trip entity. When the app relaunches, `ActiveTripViewModel` checks for uncompleted trips in Room and restores monitoring state without requiring manual re-entry.
- *Gap Identified*: ViewModels do not yet save transient text input into `SavedStateHandle`. If process death occurs while typing in `PlanTripScreen`, unsubmitted text fields are cleared.

