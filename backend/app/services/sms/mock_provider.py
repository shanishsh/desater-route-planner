"""Mock SMS Provider for local development, simulation, and automated testing."""
import uuid
import logging
from typing import Dict, List, Any
from app.services.sms.base import SMSProvider, SMSResult

logger = logging.getLogger(__name__)


class MockSMSProvider(SMSProvider):
    def __init__(self):
        # Store dispatched mock messages for inspection and testing
        self.dispatched_messages: List[Dict[str, Any]] = []

    async def send_sms(
        self,
        to_phone: str,
        message: str,
        alert_id: str,
        idempotency_key: str,
    ) -> SMSResult:
        # Check idempotency in mock history
        for existing in self.dispatched_messages:
            if existing["idempotency_key"] == idempotency_key:
                logger.info("MockSMSProvider: Idempotent replay for key %s", idempotency_key)
                return SMSResult(
                    success=True,
                    provider_message_id=existing["provider_message_id"],
                    status="ACCEPTED",
                    is_simulated=True,
                )

        provider_msg_id = f"MOCK_SMS_{uuid.uuid4().hex[:12].upper()}"
        
        # Mask phone for privacy in logs
        masked_phone = to_phone[:3] + "****" + to_phone[-4:] if len(to_phone) > 7 else "***"
        
        record = {
            "provider_message_id": provider_msg_id,
            "to_phone": to_phone,
            "masked_phone": masked_phone,
            "message": message,
            "alert_id": alert_id,
            "idempotency_key": idempotency_key,
            "status": "ACCEPTED",
            "is_simulated": True,
        }
        self.dispatched_messages.append(record)
        
        logger.info(
            "[SIMULATED SMS] Provider ID: %s | Recipient: %s | Alert ID: %s | Body: %s",
            provider_msg_id,
            masked_phone,
            alert_id,
            message[:60] + "..." if len(message) > 60 else message,
        )
        
        return SMSResult(
            success=True,
            provider_message_id=provider_msg_id,
            status="ACCEPTED",
            is_simulated=True,
        )

    async def get_delivery_status(self, provider_message_id: str) -> str:
        for msg in self.dispatched_messages:
            if msg["provider_message_id"] == provider_message_id:
                # In simulation mode, mock providers move from ACCEPTED to DELIVERED
                return "DELIVERED"
        return "UNKNOWN"

    def clear(self):
        self.dispatched_messages.clear()


# Global singleton instance for app and tests
mock_sms_provider = MockSMSProvider()

