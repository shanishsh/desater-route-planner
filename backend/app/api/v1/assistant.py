"""Live Trek Safety Assistant API endpoints."""
import logging
from fastapi import APIRouter, HTTPException, status
from app.schemas.assistant import (
    LiveSafetyAssistantRequest,
    AssistantInteractiveQueryRequest,
    LiveSafetyAssistantResponse,
)
from app.services.ai.live_safety_assistant import live_safety_assistant_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/assistant", tags=["assistant"])


@router.post(
    "/safety-guidance",
    response_model=LiveSafetyAssistantResponse,
    summary="Generate grounded safety guidance for active trek context or safety event",
)
async def get_safety_guidance(
    request: LiveSafetyAssistantRequest,
) -> LiveSafetyAssistantResponse:
    """
    Evaluates a live trek safety event or current progress state.
    Returns rich, grounded safety guidance citing exact hazard IDs, recommended actions,
    and concise voice-ready spoken alerts.
    """
    try:
        return await live_safety_assistant_service.get_safety_guidance_async(request)
    except Exception as e:
        logger.error("Error generating live safety guidance: %s", e, exc_info=True)
        # Guarantees robust fallback instead of 500 error to mobile client
        return live_safety_assistant_service.generate_deterministic_guidance(request)


@router.post(
    "/ask",
    response_model=LiveSafetyAssistantResponse,
    summary="Answer contextual trekker queries strictly grounded in live conditions",
)
async def ask_assistant(
    request: AssistantInteractiveQueryRequest,
) -> LiveSafetyAssistantResponse:
    """
    Answers questions from the trekker (e.g. 'What is the weather ahead?', 'Nearest clinic?',
    'Is there an alternative trail?').
    Responses are strictly grounded in supplied route context without hallucinations.
    """
    try:
        return await live_safety_assistant_service.answer_user_query_async(request)
    except Exception as e:
        logger.error("Error answering interactive query: %s", e, exc_info=True)
        return live_safety_assistant_service._generate_query_fallback(request)

