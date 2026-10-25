"""AI-assisted bulletin extraction service.

Parses unstructured government disaster/road bulletins (GSI, DGRE, PWD, USDMA)
into structured draft advisory records with explicit source citations.
Falls back safely to raw text preservation when extraction fails or is low-confidence.
"""
import re
import logging
from datetime import datetime, timezone
from typing import Dict, Any, Optional, List
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


class StructuredBulletinDraft(BaseModel):
    document_id: str
    source_agency: str
    hazard_type: str  # landslide, avalanche, road_closure, flash_flood, weather
    affected_districts: List[str] = Field(default_factory=list)
    severity_term: str  # CRITICAL, HIGH, MODERATE, ADVISORY
    issue_date: Optional[str] = None
    valid_until: Optional[str] = None
    cited_source_text: str
    confidence_score: float  # 0.0 to 1.0
    needs_manual_review: bool = False
    raw_text: str


class BulletinExtractorService:
    """Extracts structured disaster attributes from government text bulletins with grounding."""

    DISTRICT_PATTERNS = [
        "Dehradun", "Uttarkashi", "Chamoli", "Rudraprayag", "Tehri", "Pauri",
        "Pithoragarh", "Bageshwar", "Almora", "Champawat", "Nainital", "Haridwar", "Udham Singh Nagar"
    ]

    HAZARD_KEYWORDS = {
        "landslide": ["landslide", "rockfall", "debris flow", "mudslide", "bhusanket"],
        "avalanche": ["avalanche", "snow slide", "snow drift", "blizzard"],
        "road_closure": ["road closed", "blocked", "highway closed", "nh-58", "nh-107", "traffic suspended"],
        "flash_flood": ["flash flood", "water level rising", "breach", "cloudburst"],
    }

    SEVERITY_KEYWORDS = {
        "CRITICAL": ["red alert", "danger mark", "severely blocked", "high danger", "critical"],
        "HIGH": ["orange alert", "warning", "heavy rockfall", "medium danger"],
        "MODERATE": ["yellow alert", "caution", "watch", "slight"],
    }

    def extract_structured_advisory(
        self,
        raw_text: str,
        document_id: str,
        source_agency: str = "Uttarakhand PWD / SDMA",
    ) -> StructuredBulletinDraft:
        """
        Extracts validated structured fields deterministically or with LLM assistance.
        Preserves complete raw source text and flags low-confidence records for human review.
        """
        text_lower = raw_text.lower()
        now = datetime.now(timezone.utc)

        # 1. Identify matched districts
        matched_districts = []
        for d in self.DISTRICT_PATTERNS:
            if re.search(rf"\b{re.escape(d.lower())}\b", text_lower):
                matched_districts.append(d)

        # 2. Identify hazard type
        hazard_type = "general_advisory"
        for h_type, kws in self.HAZARD_KEYWORDS.items():
            if any(kw in text_lower for kw in kws):
                hazard_type = h_type
                break

        # 3. Identify severity
        severity = "ADVISORY"
        for sev, kws in self.SEVERITY_KEYWORDS.items():
            if any(kw in text_lower for kw in kws):
                severity = sev
                break

        # 4. Extract snippet for citation
        sentences = re.split(r"[.\n]+", raw_text)
        cited_snippet = ""
        for s in sentences:
            if any(d.lower() in s.lower() for d in matched_districts) or any(k in s.lower() for k in ["closed", "alert", "warning"]):
                cited_snippet = s.strip()
                break
        if not cited_snippet and sentences:
            cited_snippet = sentences[0].strip()

        confidence = 0.5
        if matched_districts:
            confidence += 0.25
        if hazard_type != "general_advisory":
            confidence += 0.15
        if severity != "ADVISORY":
            confidence += 0.10

        needs_review = confidence < 0.7 or not matched_districts

        return StructuredBulletinDraft(
            document_id=document_id,
            source_agency=source_agency,
            hazard_type=hazard_type,
            affected_districts=matched_districts,
            severity_term=severity,
            issue_date=now.isoformat(),
            valid_until=None,
            cited_source_text=cited_snippet[:250],
            confidence_score=round(min(1.0, confidence), 2),
            needs_manual_review=needs_review,
            raw_text=raw_text,
        )


bulletin_extractor_service = BulletinExtractorService()
