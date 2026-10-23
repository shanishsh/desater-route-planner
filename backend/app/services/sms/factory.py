"""SMS Provider Factory."""
from app.config import settings
from app.services.sms.base import SMSProvider
from app.services.sms.mock_provider import mock_sms_provider
from app.services.sms.twilio_provider import TwilioSMSProvider


def get_sms_provider() -> SMSProvider:
    if settings.SMS_PROVIDER.lower() == "twilio":
        twilio = TwilioSMSProvider()
        if twilio.is_configured:
            return twilio
    return mock_sms_provider

