"""Mock FCM Push Provider for testing and local development without cloud dependencies."""
import uuid
import logging
from typing import Dict, List, Any
from app.services.push.base import PushProvider, PushResult

logger = logging.getLogger(__name__)


class MockFCMProvider(PushProvider):
    def __init__(self):
        self.dispatched_pushes: List[Dict[str, Any]] = []

    async def send_push(
        self,
        fcm_token: str,
        title: str,
        body: str,
        data: Dict[str, Any],
    ) -> PushResult:
        msg_id = f"projects/tourist-safety/messages/mock_{uuid.uuid4().hex[:12]}"
        masked_token = fcm_token[:8] + "..." + fcm_token[-6:] if len(fcm_token) > 14 else "***"
        
        record = {
            "message_id": msg_id,
            "fcm_token": fcm_token,
            "masked_token": masked_token,
            "title": title,
            "body": body,
            "data": data,
            "is_simulated": True,
        }
        self.dispatched_pushes.append(record)
        logger.info(
            "[SIMULATED PUSH] Message ID: %s | Target: %s | Title: %s",
            msg_id,
            masked_token,
            title,
        )
        return PushResult(
            success=True,
            provider_message_id=msg_id,
            status="SENT",
            is_simulated=True,
        )

    def clear(self):
        self.dispatched_pushes.clear()


mock_fcm_provider = MockFCMProvider()

