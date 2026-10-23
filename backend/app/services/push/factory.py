"""Push Provider Factory."""
from app.services.push.base import PushProvider
from app.services.push.mock_fcm_provider import mock_fcm_provider


def get_push_provider() -> PushProvider:
    # In future, if firebase_admin is installed and configured, return FirebaseFCMProvider
    return mock_fcm_provider

