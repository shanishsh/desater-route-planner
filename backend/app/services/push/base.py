"""Push Provider interface."""
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional, Dict, Any


@dataclass
class PushResult:
    success: bool
    provider_message_id: Optional[str]
    status: str  # SENT, FAILED
    is_simulated: bool
    error: Optional[str] = None


class PushProvider(ABC):
    @abstractmethod
    async def send_push(
        self,
        fcm_token: str,
        title: str,
        body: str,
        data: Dict[str, Any],
    ) -> PushResult:
        """Send a push notification to an FCM device token."""
        pass

