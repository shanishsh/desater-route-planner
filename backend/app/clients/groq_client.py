"""Groq Cloud AI client for fast LLM inference with deterministic fallback."""
import logging
from typing import Optional, Dict, Any, List
import httpx
from app.config import settings
from app.clients.circuit_breaker import CircuitBreaker

logger = logging.getLogger(__name__)


class GroqClient:
    """Client for Groq Cloud API compatible with OpenAI completions."""

    def __init__(self):
        self.circuit_breaker = CircuitBreaker("GroqAI", failure_threshold=3, recovery_time_seconds=60.0)
        self.client = httpx.AsyncClient(
            timeout=httpx.Timeout(connect=5.0, read=25.0, write=5.0, pool=5.0),
            headers={"User-Agent": "TouristSafetyPlatform/1.0 (Safety-Assistant)"},
        )

    def is_configured(self) -> bool:
        """Returns True if GROQ_API_KEY is configured in settings."""
        key = settings.GROQ_API_KEY
        return bool(key and key.strip() and not key.startswith("YOUR_"))

    async def chat_completion(
        self,
        messages: List[Dict[str, str]],
        temperature: float = 0.2,
        max_tokens: int = 500,
        model: Optional[str] = None,
        response_format: Optional[Dict[str, Any]] = None,
    ) -> Optional[str]:
        """Calls Groq chat completions API with circuit breaker protection."""
        if not self.is_configured():
            logger.debug("Groq API key is unconfigured.")
            return None

        if not self.circuit_breaker.can_execute():
            logger.warning("Groq AI circuit breaker is currently OPEN.")
            return None

        key = settings.GROQ_API_KEY.strip()
        url = f"{settings.GROQ_BASE_URL.rstrip('/')}/chat/completions"
        headers = {
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        }
        payload: Dict[str, Any] = {
            "model": model or settings.GROQ_MODEL,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        if response_format:
            payload["response_format"] = response_format

        try:
            response = await self.client.post(url, headers=headers, json=payload)
            if response.status_code == 200:
                data = response.json()
                choices = data.get("choices", [])
                if choices:
                    msg = choices[0].get("message", {})
                    content = msg.get("content") or ""
                    # Handle reasoning models where output might be in reasoning field
                    if not content.strip() and msg.get("reasoning"):
                        content = msg.get("reasoning")
                    self.circuit_breaker.record_success()
                    return content.strip()
            else:
                logger.warning("Groq API returned HTTP %s: %s", response.status_code, response.text)
                self.circuit_breaker.record_failure()
                return None
        except Exception as exc:
            logger.warning("Groq API request error: %s", exc)
            self.circuit_breaker.record_failure()
            return None

    async def close(self):
        await self.client.aclose()


groq_client = GroqClient()
