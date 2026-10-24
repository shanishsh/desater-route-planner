"""
Verified Uttarakhand Corridors Registry.

Defines tested road approach corridors and alpine trekking segments within Uttarakhand,
enforcing travel-mode restrictions (e.g. prohibiting driving beyond roadheads).
"""

from typing import Any, Dict, List, Optional, Tuple


VERIFIED_UTTARAKHAND_CORRIDORS: List[Dict[str, Any]] = [
    {
        "id": "uk-corridor-rudraprayag-gaurikund",
        "title": "Gaurikund Road Approach (NH 107)",
        "subtitle": "Rudraprayag to Gaurikund Kedarnath Base",
        "corridor_type": "ROAD_APPROACH",
        "origin_name": "Rudraprayag, Uttarakhand",
        "origin_lat": 30.2844,
        "origin_lon": 78.9806,
        "destination_name": "Gaurikund Base, Uttarakhand",
        "destination_lat": 30.6534,
        "destination_lon": 79.0268,
        "allowed_modes": ["driving"],
        "default_mode": "driving",
        "distance_km": 74.8,
        "duration_minutes": 113.4,
        "description": (
            "NH 107 Char Dham road approach along the Mandakini river valley via Guptkashi and Sonprayag. "
            "Motor vehicle access terminates at Gaurikund."
        ),
        "verified_facilities": "Hospitals at Guptkashi/Ukhimath, police at Sonprayag/Gaurikund, fuel along NH 107.",
    },
    {
        "id": "uk-corridor-gaurikund-kedarnath-trek",
        "title": "Gaurikund to Kedarnath Alpine Trek",
        "subtitle": "High-altitude pilgrimage foot trek (3,584m)",
        "corridor_type": "ALPINE_TREK",
        "origin_name": "Gaurikund, Uttarakhand",
        "origin_lat": 30.6534,
        "origin_lon": 79.0268,
        "destination_name": "Kedarnath Temple, Uttarakhand",
        "destination_lat": 30.7339,
        "destination_lon": 79.0669,
        "allowed_modes": ["hiking", "walking"],
        "default_mode": "hiking",
        "distance_km": 16.5,
        "duration_minutes": 360.0,
        "description": (
            "Historic alpine pilgrimage trail along the Mandakini gorge via Jungle Chatti, Bheembali, and Lincholi. "
            "Motor vehicles are strictly prohibited. Foot, mule, and dandi travel only."
        ),
        "verified_facilities": "Medical relief posts at Bheembali/Lincholi, Kedarnath Police Outpost, GMVN shelters.",
    },
    {
        "id": "uk-corridor-rishikesh-devprayag",
        "title": "Rishikesh to Devprayag Valley Corridor",
        "subtitle": "NH 7 Char Dham Highway",
        "corridor_type": "HIGHWAY_CORRIDOR",
        "origin_name": "Rishikesh, Uttarakhand",
        "origin_lat": 30.0869,
        "origin_lon": 78.2676,
        "destination_name": "Devprayag, Uttarakhand",
        "destination_lat": 30.1459,
        "destination_lon": 78.5989,
        "allowed_modes": ["driving", "walking"],
        "default_mode": "driving",
        "distance_km": 73.1,
        "duration_minutes": 107.5,
        "description": "Scenic highway corridor along the holy confluence of Bhagirathi and Alaknanda rivers.",
        "verified_facilities": "AIIMS Rishikesh, Byasi police outpost, Devprayag Community Health Centre, fuel stations.",
    },
    {
        "id": "uk-corridor-dehradun-mussoorie",
        "title": "Dehradun to Mussoorie Hills Corridor",
        "subtitle": "Doon Valley to Queen of Hills",
        "corridor_type": "HILL_ROAD",
        "origin_name": "Dehradun Clock Tower, Uttarakhand",
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "destination_name": "Mussoorie Picture Palace, Uttarakhand",
        "destination_lat": 30.4598,
        "destination_lon": 78.0700,
        "allowed_modes": ["driving", "walking"],
        "default_mode": "driving",
        "distance_km": 31.2,
        "duration_minutes": 42.0,
        "description": "Mountain road ascending from Rajpur through Kuthalgate and Kolhukhet into Mussoorie.",
        "verified_facilities": "Max Super Speciality Hospital Dehradun, Mussoorie Police Station, Landour Community Hospital, fuel at Kuthalgate.",
    },
    {
        "id": "uk-corridor-haridwar-rishikesh",
        "title": "Haridwar to Rishikesh Gateway Corridor",
        "subtitle": "Gangetic foothills gateway",
        "corridor_type": "GATEWAY_CORRIDOR",
        "origin_name": "Haridwar Har Ki Pauri, Uttarakhand",
        "origin_lat": 29.9457,
        "origin_lon": 78.1642,
        "destination_name": "Rishikesh AIIMS, Uttarakhand",
        "destination_lat": 30.0869,
        "destination_lon": 78.2676,
        "allowed_modes": ["driving", "walking"],
        "default_mode": "driving",
        "distance_km": 24.4,
        "duration_minutes": 29.4,
        "description": "Gateway connection between Haridwar and Rishikesh along the Rajaji National Park border.",
        "verified_facilities": "Haridwar District Hospital, AIIMS Rishikesh, Raiwala Police Station, multiple fuel stations.",
    },
]


def get_verified_corridors() -> List[Dict[str, Any]]:
    """Returns the list of verified Uttarakhand corridors."""
    return VERIFIED_UTTARAKHAND_CORRIDORS


def find_verified_corridor(corridor_id: str) -> Optional[Dict[str, Any]]:
    """Looks up a corridor by unique identifier."""
    for c in VERIFIED_UTTARAKHAND_CORRIDORS:
        if c["id"] == corridor_id:
            return c
    return None


def is_pedestrian_only_corridor(
    origin_lat: float,
    origin_lon: float,
    dest_lat: float,
    dest_lon: float,
) -> Tuple[bool, Optional[str]]:
    """
    Checks if a requested route touches an alpine trekking corridor where
    motor vehicles are strictly prohibited (e.g. Gaurikund to Kedarnath).
    """
    # Gaurikund (30.6534, 79.0268) and Kedarnath (30.7339, 79.0669) bounding box
    if 30.64 <= origin_lat <= 30.75 and 79.01 <= origin_lon <= 79.08:
        if 30.64 <= dest_lat <= 30.75 and 79.01 <= dest_lon <= 79.08:
            # If moving towards Kedarnath (higher latitude) from Gaurikund
            if dest_lat > 30.67 or origin_lat > 30.67:
                return (
                    True,
                    "Motor road terminates at Gaurikund. Motor vehicles are strictly prohibited "
                    "on the Kedarnath alpine trail. Please select 'walking' or 'hiking' travel mode.",
                )

    return False, None

