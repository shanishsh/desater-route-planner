"""Twilio SMS Provider for production delivery when credentials are configured."""
import logging
import httpx
from typing import Optional
from app.config import settings
from app.services.sms.base import SMSProvider, SMSResult

logger = logging.getLogger(__name__)


class TwilioSMSProvider(SMSProvider):
    def __init__(self):
        self.account_sid = settings.TWILIO_ACCOUNT_SID
        self.auth_token = settings.TWILIO_AUTH_TOKEN
        self.from_number = settings.TWILIO_FROM_NUMBER

    @property
    def is_configured(self) -> bool:
        return bool(self.account_sid and self.auth_token and self.from_number)

    async def send_sms(
        self,
        to_phone: str,
        message: str,
        alert_id: str,
        idempotency_key: str,
    ) -> SMSResult:
        if not self.is_configured:
            return SMSResult(
                success=False,
                provider_message_id=None,
                status="FAILED",
                is_simulated=False,
                error="Twilio credentials are not configured",
            )

        url = f"https://api.twilio.com/2010-04-01/Accounts/{self.account_sid}/Messages.json"
        data = {
            "To": to_phone,
            "From": self.from_number,
            "Body": message,
        }

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.post(
                    url,
                    data=data,
                    auth=(self.account_sid, self.auth_token),
                )
                if response.status_code in (200, 201):
                    res_json = response.json()
                    sid = res_json.get("sid")
                    status = res_json.get("status", "queued").upper()
                    return SMSResult(
                        success=True,
                        provider_message_id=sid,
                        status=status,
                        is_simulated=False,
                    )
                else:
                    logger.error("Twilio SMS failed with HTTP %d: %s", response.status_code, response.text)
                    return SMSResult(
                        success=False,
                        provider_message_id=None,
                        status="FAILED",
                        is_simulated=False,
                        error=f"Twilio error HTTP {response.status_code}",
                    )
        except Exception as ex:
            logger.exception("Exception during Twilio SMS send: %s", ex)
            return SMSResult(
                success=False,
                provider_message_id=None,
                status="FAILED",
                is_simulated=False,
                error=str(ex),
            )

    async def get_delivery_status(self, provider_message_id: str) -> str:
        if not self.is_configured or not provider_message_id:
            return "UNKNOWN"
        url = f"https://api.twilio.com/2010-04-01/Accounts/{self.account_sid}/Messages/{provider_message_id}.json"
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.get(url, auth=(self.account_sid, self.auth_token))
                if response.status_code == 200:
                    status = response.json().get("status", "unknown").upper()
                    return status
        except Exception as ex:
            logger.error("Error querying Twilio delivery status: %s", ex)
        return "UNKNOWN"

