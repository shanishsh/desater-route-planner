"""SMS Provider interface and domain models."""
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional


@dataclass
class SMSResult:
    success: bool
    provider_message_id: Optional[str]
    status: str  # QUEUED, ACCEPTED, SENT, DELIVERED, FAILED
    is_simulated: bool
    error: Optional[str] = None


class SMSProvider(ABC):
    @abstractmethod
    async def send_sms(
        self,
        to_phone: str,
        message: str,
        alert_id: str,
        idempotency_key: str,
    ) -> SMSResult:
        """Send an SMS message to an E.164 phone number."""
        pass

    @abstractmethod
    async def get_delivery_status(self, provider_message_id: str) -> str:
        """Query delivery status from the SMS provider."""
        pass

