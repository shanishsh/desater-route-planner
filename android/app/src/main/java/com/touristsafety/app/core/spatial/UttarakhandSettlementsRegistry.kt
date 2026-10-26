package com.touristsafety.app.core.spatial

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class UttarakhandSettlement(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val district: String,
    val category: String,
    val aliases: List<String> = emptyList()
) {
    val displayName: String get() = "$name, $district, Uttarakhand"
}

object UttarakhandSettlementsRegistry {

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }

    val SETTLEMENTS: List<UttarakhandSettlement> = listOf(
        // Kumaon
        UttarakhandSettlement("Almora", 29.5971, 79.6591, "Almora", "DISTRICT_HQ", listOf("almore", "almora city", "almora bazaar", "almora town", "almodha")),
        UttarakhandSettlement("Ranikhet", 29.6434, 79.4322, "Almora", "HILL_STATION", listOf("ranikhet cantt", "chaubatia")),
        UttarakhandSettlement("Kausani", 29.8542, 79.5967, "Bageshwar", "HILL_STATION", listOf("kausani tea estate")),
        UttarakhandSettlement("Bageshwar", 29.8394, 79.7712, "Bageshwar", "DISTRICT_HQ", listOf("bageshwar dham", "bagnath temple")),
        UttarakhandSettlement("Jageshwar", 29.6397, 79.8536, "Almora", "PILGRIMAGE", listOf("jageshwar dham", "jageshwar temple")),
        UttarakhandSettlement("Dwarahat", 29.7833, 79.4333, "Almora", "TOWN", listOf("dwarahat temple")),
        UttarakhandSettlement("Someshwar", 29.7800, 79.6000, "Almora", "TOWN", listOf("someshwar valley")),
        UttarakhandSettlement("Chaukhutia", 29.8833, 79.3500, "Almora", "TOWN"),
        UttarakhandSettlement("Nainital", 29.3919, 79.4542, "Nainital", "DISTRICT_HQ", listOf("naini tal", "mall road nainital", "naini lake")),
        UttarakhandSettlement("Haldwani", 29.2183, 79.5130, "Nainital", "COMMERCIAL_HUB", listOf("kathgodam", "haldwani junction")),
        UttarakhandSettlement("Bhimtal", 29.3500, 79.5667, "Nainital", "LAKE_TOWN"),
        UttarakhandSettlement("Bhowali", 29.3833, 79.5167, "Nainital", "TOWN"),
        UttarakhandSettlement("Mukteshwar", 29.4722, 79.6486, "Nainital", "HILL_STATION"),
        UttarakhandSettlement("Ramnagar", 29.3900, 79.1200, "Nainital", "GATEWAY", listOf("corbett gateway", "jim corbett")),
        UttarakhandSettlement("Rudrapur", 28.9800, 79.4000, "Udham Singh Nagar", "DISTRICT_HQ"),
        UttarakhandSettlement("Kashipur", 29.2100, 78.9600, "Udham Singh Nagar", "CITY"),
        UttarakhandSettlement("Khatima", 28.9200, 79.9700, "Udham Singh Nagar", "BORDER_TOWN"),
        UttarakhandSettlement("Pantnagar", 29.0200, 79.4900, "Udham Singh Nagar", "AIRPORT_TOWN"),
        UttarakhandSettlement("Pithoragarh", 29.5829, 80.2182, "Pithoragarh", "DISTRICT_HQ"),
        UttarakhandSettlement("Munsyari", 30.0673, 80.2372, "Pithoragarh", "TREK_BASE", listOf("munsiari", "panchachuli base")),
        UttarakhandSettlement("Dharchula", 29.8458, 80.5367, "Pithoragarh", "BORDER_TOWN"),
        UttarakhandSettlement("Didihat", 29.8000, 80.2500, "Pithoragarh", "TOWN"),
        UttarakhandSettlement("Berinag", 29.7700, 80.0500, "Pithoragarh", "TOWN"),
        UttarakhandSettlement("Champawat", 29.3356, 80.0911, "Champawat", "DISTRICT_HQ"),
        UttarakhandSettlement("Lohaghat", 29.4000, 80.0900, "Champawat", "TOWN"),
        UttarakhandSettlement("Tanakpur", 29.0722, 80.1111, "Champawat", "RAIL_HEAD"),

        // Garhwal
        UttarakhandSettlement("Dehradun", 30.3165, 78.0322, "Dehradun", "STATE_CAPITAL", listOf("dehradoon", "doon valley", "clock tower dehradun")),
        UttarakhandSettlement("Mussoorie", 30.4598, 78.0700, "Dehradun", "HILL_STATION", listOf("mussoorie mall road", "kempty falls")),
        UttarakhandSettlement("Rishikesh", 30.0869, 78.2676, "Dehradun", "PILGRIMAGE_ADVENTURE", listOf("laxman jhula", "ram jhula", "tapovan")),
        UttarakhandSettlement("Haridwar", 29.9457, 78.1642, "Haridwar", "DISTRICT_HQ", listOf("har ki pauri", "hardwar")),
        UttarakhandSettlement("Roorkee", 29.8543, 77.8880, "Haridwar", "CITY", listOf("iit roorkee")),
        UttarakhandSettlement("Kedarnath", 30.7346, 79.0669, "Rudraprayag", "CHAR_DHAM", listOf("kedarnath temple", "kedar", "kedarnath dham")),
        UttarakhandSettlement("Gaurikund", 30.6534, 79.0268, "Rudraprayag", "TRAILHEAD", listOf("gaurikund base", "kedarnath trailhead")),
        UttarakhandSettlement("Sonprayag", 30.6300, 78.9900, "Rudraprayag", "HUB"),
        UttarakhandSettlement("Guptkashi", 30.5228, 79.0769, "Rudraprayag", "TOWN"),
        UttarakhandSettlement("Ukhimath", 30.5147, 79.0983, "Rudraprayag", "TOWN", listOf("omkareshwar temple ukhimath")),
        UttarakhandSettlement("Chopta", 30.4853, 79.1764, "Rudraprayag", "TREK_BASE", listOf("mini switzerland of india")),
        UttarakhandSettlement("Tungnath", 30.4886, 79.2172, "Rudraprayag", "PILGRIMAGE_PEAK"),
        UttarakhandSettlement("Rudraprayag", 30.2850, 78.9800, "Rudraprayag", "DISTRICT_HQ"),
        UttarakhandSettlement("Badrinath", 30.7433, 79.4938, "Chamoli", "CHAR_DHAM", listOf("badrinath temple", "badri dham", "mana village")),
        UttarakhandSettlement("Joshimath", 30.5564, 79.5662, "Chamoli", "HUB", listOf("jyotirmath")),
        UttarakhandSettlement("Auli", 30.5300, 79.5700, "Chamoli", "SKI_RESORT"),
        UttarakhandSettlement("Govindghat", 30.6200, 79.5900, "Chamoli", "TRAILHEAD", listOf("valley of flowers base", "hemkund base")),
        UttarakhandSettlement("Gopeshwar", 30.4136, 79.3248, "Chamoli", "DISTRICT_HQ"),
        UttarakhandSettlement("Karnaprayag", 30.2600, 79.2200, "Chamoli", "SANGAM"),
        UttarakhandSettlement("New Tehri", 30.3800, 78.4800, "Tehri Garhwal", "DISTRICT_HQ", listOf("tehri lake", "tehri dam")),
        UttarakhandSettlement("Dhanaulti", 30.4500, 78.2300, "Tehri Garhwal", "HILL_STATION"),
        UttarakhandSettlement("Devprayag", 30.1500, 78.6000, "Tehri Garhwal", "HOLY_CONFLUENCE", listOf("bhagirathi alaknanda sangam")),
        UttarakhandSettlement("Pauri", 30.1500, 78.7800, "Pauri Garhwal", "DISTRICT_HQ"),
        UttarakhandSettlement("Srinagar Garhwal", 30.2200, 78.7800, "Pauri Garhwal", "EDUCATION_HUB"),
        UttarakhandSettlement("Lansdowne", 29.8400, 78.6800, "Pauri Garhwal", "CANTT"),
        UttarakhandSettlement("Kotdwar", 29.7500, 78.5300, "Pauri Garhwal", "GATEWAY"),
        UttarakhandSettlement("Uttarkashi", 30.7268, 78.4354, "Uttarkashi", "DISTRICT_HQ"),
        UttarakhandSettlement("Gangotri", 30.9947, 78.9398, "Uttarkashi", "CHAR_DHAM", listOf("gangotri temple", "bhagirathi")),
        UttarakhandSettlement("Yamunotri", 31.0140, 78.4600, "Uttarkashi", "CHAR_DHAM", listOf("yamunotri temple", "janki chatti")),
        UttarakhandSettlement("Harsil", 31.0360, 78.7370, "Uttarkashi", "VALLEY"),
        UttarakhandSettlement("Sankri", 31.0800, 78.1800, "Uttarkashi", "TREK_BASE", listOf("har ki dun base", "kedarkantha base"))
    )

    fun searchSettlements(query: String, limit: Int = 10): List<UttarakhandSettlement> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return SETTLEMENTS.take(limit)

        return SETTLEMENTS
            .filter { s ->
                s.name.lowercase().contains(q) ||
                s.district.lowercase().contains(q) ||
                s.aliases.any { it.contains(q) || q.contains(it) }
            }
            .take(limit)
    }

    fun findSettlement(query: String): UttarakhandSettlement? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null

        return SETTLEMENTS.firstOrNull { s ->
            s.name.lowercase() == q ||
            s.aliases.any { it == q }
        } ?: SETTLEMENTS.firstOrNull { s ->
            s.name.lowercase().contains(q) ||
            s.aliases.any { it.contains(q) || q.contains(it) }
        }
    }

    fun findNearestSettlement(lat: Double, lon: Double, maxDistKm: Double = 25.0): UttarakhandSettlement? {
        var closest: UttarakhandSettlement? = null
        var minDist = Double.MAX_VALUE

        for (s in SETTLEMENTS) {
            val d = haversineKm(lat, lon, s.latitude, s.longitude)
            if (d < minDist && d <= maxDistKm) {
                minDist = d
                closest = s
            }
        }
        return closest
    }
}

