"""
Authoritative Uttarakhand Settlements, Trailheads, and Districts Registry.
Provides verified coordinates, alternate spellings, and fuzzy matching for Uttarakhand locations.
"""

from typing import List, Dict, Any, Optional
import math


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (
        math.sin(dlat / 2.0) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(dlon / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r * c


UTTARAKHAND_SETTLEMENTS: List[Dict[str, Any]] = [
    # Kumaon Region
    {
        "name": "Almora",
        "latitude": 29.5971,
        "longitude": 79.6591,
        "district": "Almora",
        "category": "DISTRICT_HQ",
        "aliases": ["almore", "almora city", "almora bazaar", "almora town"],
    },
    {
        "name": "Ranikhet",
        "latitude": 29.6434,
        "longitude": 79.4322,
        "district": "Almora",
        "category": "HILL_STATION",
        "aliases": ["ranikhet cantt", "chaubatia"],
    },
    {
        "name": "Kausani",
        "latitude": 29.8542,
        "longitude": 79.5967,
        "district": "Bageshwar",
        "category": "HILL_STATION",
        "aliases": ["kausani tea estate"],
    },
    {
        "name": "Bageshwar",
        "latitude": 29.8394,
        "longitude": 79.7712,
        "district": "Bageshwar",
        "category": "DISTRICT_HQ",
        "aliases": ["bageshwar dham", "bagnath temple"],
    },
    {
        "name": "Jageshwar",
        "latitude": 29.6397,
        "longitude": 79.8536,
        "district": "Almora",
        "category": "PILGRIMAGE",
        "aliases": ["jageshwar dham", "jageshwar temple"],
    },
    {
        "name": "Dwarahat",
        "latitude": 29.7833,
        "longitude": 79.4333,
        "district": "Almora",
        "category": "TOWN",
        "aliases": ["dwarahat temple"],
    },
    {
        "name": "Someshwar",
        "latitude": 29.7800,
        "longitude": 79.6000,
        "district": "Almora",
        "category": "TOWN",
        "aliases": ["someshwar valley"],
    },
    {
        "name": "Chaukhutia",
        "latitude": 29.8833,
        "longitude": 79.3500,
        "district": "Almora",
        "category": "TOWN",
        "aliases": [],
    },
    {
        "name": "Nainital",
        "latitude": 29.3919,
        "longitude": 79.4542,
        "district": "Nainital",
        "category": "DISTRICT_HQ",
        "aliases": ["naini tal", "mall road nainital", "naini lake"],
    },
    {
        "name": "Haldwani",
        "latitude": 29.2183,
        "longitude": 79.5130,
        "district": "Nainital",
        "category": "CITY",
        "aliases": ["haldwani kathgodam"],
    },
    {
        "name": "Kathgodam",
        "latitude": 29.2711,
        "longitude": 79.5414,
        "district": "Nainital",
        "category": "RAIL_HEAD",
        "aliases": ["kathgodam railway station"],
    },
    {
        "name": "Bhimtal",
        "latitude": 29.3497,
        "longitude": 79.5539,
        "district": "Nainital",
        "category": "TOWN",
        "aliases": ["bhimtal lake"],
    },
    {
        "name": "Bhowali",
        "latitude": 29.3800,
        "longitude": 79.5200,
        "district": "Nainital",
        "category": "TOWN",
        "aliases": ["kainchi dham", "neeb karori baba"],
    },
    {
        "name": "Mukteshwar",
        "latitude": 29.4722,
        "longitude": 79.6550,
        "district": "Nainital",
        "category": "HILL_STATION",
        "aliases": ["mukteshwar dham"],
    },
    {
        "name": "Ramnagar",
        "latitude": 29.3949,
        "longitude": 79.1265,
        "district": "Nainital",
        "category": "TOWN",
        "aliases": ["jim corbett entry", "corbett gate"],
    },
    {
        "name": "Jim Corbett National Park",
        "latitude": 29.5300,
        "longitude": 78.7747,
        "district": "Nainital",
        "category": "NATIONAL_PARK",
        "aliases": ["corbett", "dhikala", "bijrani"],
    },
    {
        "name": "Pithoragarh",
        "latitude": 29.5829,
        "longitude": 80.2182,
        "district": "Pithoragarh",
        "category": "DISTRICT_HQ",
        "aliases": ["pithoragarh valley"],
    },
    {
        "name": "Dharchula",
        "latitude": 29.8500,
        "longitude": 80.5333,
        "district": "Pithoragarh",
        "category": "TREK_HUB",
        "aliases": ["om parvat route", "adi kailash route"],
    },
    {
        "name": "Munsyari",
        "latitude": 30.0667,
        "longitude": 80.2333,
        "district": "Pithoragarh",
        "category": "TREK_HUB",
        "aliases": ["munsiyari", "panchachuli base", "milam trek"],
    },
    {
        "name": "Champawat",
        "latitude": 29.3333,
        "longitude": 80.1000,
        "district": "Champawat",
        "category": "DISTRICT_HQ",
        "aliases": ["banasur ka kila"],
    },
    {
        "name": "Tanakpur",
        "latitude": 29.0717,
        "longitude": 80.1097,
        "district": "Champawat",
        "category": "RAIL_HEAD",
        "aliases": ["purnagiri entry"],
    },
    {
        "name": "Rudrapur",
        "latitude": 28.9800,
        "longitude": 79.4000,
        "district": "Udham Singh Nagar",
        "category": "DISTRICT_HQ",
        "aliases": ["us nagar"],
    },
    {
        "name": "Kashipur",
        "latitude": 29.2105,
        "longitude": 78.9619,
        "district": "Udham Singh Nagar",
        "category": "CITY",
        "aliases": ["drona sagar"],
    },
    {
        "name": "Pantnagar",
        "latitude": 29.0274,
        "longitude": 79.4930,
        "district": "Udham Singh Nagar",
        "category": "AIRPORT",
        "aliases": ["pantnagar airport"],
    },
    # Garhwal Region
    {
        "name": "Dehradun",
        "latitude": 30.3165,
        "longitude": 78.0322,
        "district": "Dehradun",
        "category": "STATE_CAPITAL",
        "aliases": ["dehra dun", "dehradoon", "clock tower dehradun", "isbt dehradun"],
    },
    {
        "name": "Mussoorie",
        "latitude": 30.4598,
        "longitude": 78.0700,
        "district": "Dehradun",
        "category": "HILL_STATION",
        "aliases": ["mussoorie mall road", "kempty falls", "queen of hills"],
    },
    {
        "name": "Rishikesh",
        "latitude": 30.0869,
        "longitude": 78.2676,
        "district": "Dehradun",
        "category": "YOGA_CAPITAL",
        "aliases": ["ram jhula", "laxman jhula", "triveni ghat", "tapovan"],
    },
    {
        "name": "Haridwar",
        "latitude": 29.9457,
        "longitude": 78.1642,
        "district": "Haridwar",
        "category": "DISTRICT_HQ",
        "aliases": ["har ki pauri", "hardwar"],
    },
    {
        "name": "Kedarnath",
        "latitude": 30.7346,
        "longitude": 79.0669,
        "district": "Rudraprayag",
        "category": "PILGRIMAGE_DHAM",
        "aliases": ["kedar", "kedarnath temple", "shri kedarnath", "kedar valley"],
    },
    {
        "name": "Gaurikund",
        "latitude": 30.6534,
        "longitude": 79.0268,
        "district": "Rudraprayag",
        "category": "TRAILHEAD",
        "aliases": ["gauri kund", "kedarnath base", "nh 107 terminus"],
    },
    {
        "name": "Sonprayag",
        "latitude": 30.6300,
        "longitude": 79.0100,
        "district": "Rudraprayag",
        "category": "TRAILHEAD",
        "aliases": ["son prayag", "shuttle point kedarnath"],
    },
    {
        "name": "Guptkashi",
        "latitude": 30.5228,
        "longitude": 79.0772,
        "district": "Rudraprayag",
        "category": "TOWN",
        "aliases": ["gupt kashi", "vishwanath temple guptkashi"],
    },
    {
        "name": "Rudraprayag",
        "latitude": 30.2844,
        "longitude": 78.9806,
        "district": "Rudraprayag",
        "category": "DISTRICT_HQ",
        "aliases": ["rudra prayag confluence"],
    },
    {
        "name": "Ukhimath",
        "latitude": 30.5167,
        "longitude": 79.0983,
        "district": "Rudraprayag",
        "category": "TOWN",
        "aliases": ["winter seat kedarnath", "omkareshwar temple"],
    },
    {
        "name": "Chopta",
        "latitude": 30.4878,
        "longitude": 79.2189,
        "district": "Rudraprayag",
        "category": "TREK_HUB",
        "aliases": ["tungnath base", "chandrashila trek", "mini switzerland"],
    },
    {
        "name": "Tungnath",
        "latitude": 30.4883,
        "longitude": 79.2167,
        "district": "Rudraprayag",
        "category": "PILGRIMAGE",
        "aliases": ["tungnath temple", "chandrashila summit"],
    },
    {
        "name": "Badrinath",
        "latitude": 30.7433,
        "longitude": 79.4938,
        "district": "Chamoli",
        "category": "PILGRIMAGE_DHAM",
        "aliases": ["badri", "badrinath temple", "shri badrinath dham", "mana village"],
    },
    {
        "name": "Joshimath",
        "latitude": 30.5564,
        "longitude": 79.5662,
        "district": "Chamoli",
        "category": "DISTRICT_HQ",
        "aliases": ["jyotirmath", "joshimath cantt"],
    },
    {
        "name": "Auli",
        "latitude": 30.5310,
        "longitude": 79.5700,
        "district": "Chamoli",
        "category": "SKI_RESORT",
        "aliases": ["auli ski resort", "auli ropeway"],
    },
    {
        "name": "Govindghat",
        "latitude": 30.6250,
        "longitude": 79.5583,
        "district": "Chamoli",
        "category": "TRAILHEAD",
        "aliases": ["valley of flowers base", "hemkund base"],
    },
    {
        "name": "Valley of Flowers",
        "latitude": 30.7280,
        "longitude": 79.5960,
        "district": "Chamoli",
        "category": "NATIONAL_PARK",
        "aliases": ["phoolon ki ghati", "valley of flowers trek"],
    },
    {
        "name": "Hemkund Sahib",
        "latitude": 30.7000,
        "longitude": 79.5800,
        "district": "Chamoli",
        "category": "PILGRIMAGE",
        "aliases": ["shri hemkund sahib gurudwara"],
    },
    {
        "name": "Roopkund",
        "latitude": 30.2644,
        "longitude": 79.7314,
        "district": "Chamoli",
        "category": "ALPINE_TREK",
        "aliases": ["mystery lake", "skeleton lake", "bedni bugyal route"],
    },
    {
        "name": "Gopeshwar",
        "latitude": 30.4000,
        "longitude": 79.3500,
        "district": "Chamoli",
        "category": "DISTRICT_HQ",
        "aliases": ["chamoli gopeshwar", "gopinath temple"],
    },
    {
        "name": "Karnaprayag",
        "latitude": 30.2583,
        "longitude": 79.2167,
        "district": "Chamoli",
        "category": "CONFLUENCE",
        "aliases": ["karna prayag"],
    },
    {
        "name": "Nandaprayag",
        "latitude": 30.3300,
        "longitude": 79.3200,
        "district": "Chamoli",
        "category": "CONFLUENCE",
        "aliases": ["nanda prayag"],
    },
    {
        "name": "Vishnuprayag",
        "latitude": 30.5600,
        "longitude": 79.5800,
        "district": "Chamoli",
        "category": "CONFLUENCE",
        "aliases": ["vishnu prayag"],
    },
    {
        "name": "Gangotri",
        "latitude": 30.9947,
        "longitude": 78.9398,
        "district": "Uttarkashi",
        "category": "PILGRIMAGE_DHAM",
        "aliases": ["gangotri temple", "gaumukh base", "bhagirathi origin"],
    },
    {
        "name": "Gaumukh",
        "latitude": 30.9260,
        "longitude": 79.0800,
        "district": "Uttarkashi",
        "category": "GLACIER",
        "aliases": ["gomukh", "tapovan trek"],
    },
    {
        "name": "Yamunotri",
        "latitude": 31.0140,
        "longitude": 78.4600,
        "district": "Uttarkashi",
        "category": "PILGRIMAGE_DHAM",
        "aliases": ["yamunotri temple", "janki chatti route"],
    },
    {
        "name": "Janki Chatti",
        "latitude": 30.9800,
        "longitude": 78.4400,
        "district": "Uttarkashi",
        "category": "TRAILHEAD",
        "aliases": ["jankichatti", "yamunotri base"],
    },
    {
        "name": "Uttarkashi",
        "latitude": 30.7268,
        "longitude": 78.4354,
        "district": "Uttarkashi",
        "category": "DISTRICT_HQ",
        "aliases": ["kashi of north", "vishwanath temple uttarkashi"],
    },
    {
        "name": "Har Ki Dun",
        "latitude": 31.1490,
        "longitude": 78.3610,
        "district": "Uttarkashi",
        "category": "ALPINE_TREK",
        "aliases": ["har ki doon", "sankri trek", "valley of gods"],
    },
    {
        "name": "Sankri",
        "latitude": 31.0772,
        "longitude": 78.1800,
        "district": "Uttarkashi",
        "category": "TRAILHEAD",
        "aliases": ["kedarkantha base", "har ki dun base"],
    },
    {
        "name": "Kedarkantha",
        "latitude": 31.0210,
        "longitude": 78.1720,
        "district": "Uttarkashi",
        "category": "SUMMIT_TREK",
        "aliases": ["kedar kantha", "juda ka talab"],
    },
    {
        "name": "New Tehri",
        "latitude": 30.3800,
        "longitude": 78.4800,
        "district": "Tehri Garhwal",
        "category": "DISTRICT_HQ",
        "aliases": ["tehri dam", "tehri lake"],
    },
    {
        "name": "Dhanaulti",
        "latitude": 30.4542,
        "longitude": 78.2392,
        "district": "Tehri Garhwal",
        "category": "HILL_STATION",
        "aliases": ["eco park dhanaulti"],
    },
    {
        "name": "Pauri",
        "latitude": 30.1500,
        "longitude": 78.7800,
        "district": "Pauri Garhwal",
        "category": "DISTRICT_HQ",
        "aliases": ["pauri garhwal"],
    },
    {
        "name": "Kotdwar",
        "latitude": 29.7500,
        "longitude": 78.5300,
        "district": "Pauri Garhwal",
        "category": "RAIL_HEAD",
        "aliases": ["kotdwara", "gateway to garhwal"],
    },
    {
        "name": "Lansdowne",
        "latitude": 29.8378,
        "longitude": 78.6833,
        "district": "Pauri Garhwal",
        "category": "HILL_STATION",
        "aliases": ["garhwali mess", "tip in top"],
    },
    {
        "name": "Devprayag",
        "latitude": 30.1460,
        "longitude": 78.5990,
        "district": "Tehri Garhwal",
        "category": "CONFLUENCE",
        "aliases": ["ganga confluence", "dev prayag"],
    },
    {
        "name": "Srinagar Garhwal",
        "latitude": 30.2200,
        "longitude": 78.7800,
        "district": "Pauri Garhwal",
        "category": "EDUCATION_HUB",
        "aliases": ["srinagar uk", "hnbgu"],
    },
    {
        "name": "Chakrata",
        "latitude": 30.7016,
        "longitude": 77.8697,
        "district": "Dehradun",
        "category": "HILL_STATION",
        "aliases": ["tiger falls", "deoban"],
    },
]


def search_uttarakhand_settlements(query: str, limit: int = 8) -> List[Dict[str, Any]]:
    """
    Finds settlements matching query with typo and substring tolerance.
    Handles common phonetics like 'almore' -> 'Almora', 'kedar' -> 'Kedarnath'.
    """
    clean = query.strip().lower()
    if not clean or len(clean) < 2:
        return []

    scored_matches = []

    for item in UTTARAKHAND_SETTLEMENTS:
        name_lower = item["name"].lower()
        score = 0

        # Exact match
        if clean == name_lower:
            score = 100
        # Prefix match
        elif name_lower.startswith(clean):
            score = 80
        # Substring in name
        elif clean in name_lower:
            score = 60
        # Substring in aliases or district
        elif any(clean in a.lower() for a in item["aliases"]):
            score = 70
        # Distance-based / typo match (e.g. 'almore' vs 'almora')
        elif len(clean) >= 4 and len(name_lower) >= 4:
            # Check for 1 character difference (Levenshtein-like fast check)
            if clean[:4] == name_lower[:4]:
                score = 65

        if score > 0:
            scored_matches.append((score, item))

    # Sort descending by match score
    scored_matches.sort(key=lambda x: x[0], reverse=True)

    results = []
    for score, item in scored_matches[:limit]:
        results.append({
            "name": item["name"],
            "latitude": item["latitude"],
            "longitude": item["longitude"],
            "country": "India",
            "admin1": "Uttarakhand",
            "display_name": f"{item['name']}, {item['district']}, Uttarakhand",
        })

    return results


def find_nearest_settlement(lat: float, lon: float, max_dist_km: float = 25.0) -> Optional[Dict[str, Any]]:
    """Finds nearest known Uttarakhand settlement to label a map coordinate."""
    best = None
    best_dist = float("inf")

    for item in UTTARAKHAND_SETTLEMENTS:
        dist = haversine_km(lat, lon, item["latitude"], item["longitude"])
        if dist < best_dist and dist <= max_dist_km:
            best_dist = dist
            best = item

    if best:
        return {
            "name": best["name"],
            "district": best["district"],
            "distance_km": round(best_dist, 1),
            "display_name": f"{best['name']} Area ({best['district']}), Uttarakhand",
        }
    return None
