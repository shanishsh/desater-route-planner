"""Push Provider Factory."""
from app.config import settings
from app.services.push.base import PushProvider
from app.services.push.mock_fcm_provider import mock_fcm_provider


def get_push_provider() -> PushProvider:
    if settings.FCM_PROVIDER == "firebase":
        from app.services.push.firebase_fcm_provider import FirebaseFCMProvider
        return FirebaseFCMProvider()
    return mock_fcm_provider
