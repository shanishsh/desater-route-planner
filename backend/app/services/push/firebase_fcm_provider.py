"""Production Firebase Cloud Messaging (FCM) Push Provider."""
import os
import json
import logging
from typing import Dict, Any, Optional
from app.config import settings
from app.services.push.base import PushProvider, PushResult

logger = logging.getLogger(__name__)


class FirebaseFCMProvider(PushProvider):
    """Sends real push notifications to Android devices via Firebase Cloud Messaging."""

    def __init__(self):
        self._initialized = False
        self._app = None

    def _ensure_initialized(self) -> bool:
        if self._initialized:
            return True

        try:
            import firebase_admin
            from firebase_admin import credentials, messaging

            # Check if default app is already initialized
            try:
                self._app = firebase_admin.get_app()
                self._initialized = True
                return True
            except ValueError:
                pass

            cred = None
            # 1. Try credentials JSON string if configured (common in cloud environments like Render)
            cred_json_str = getattr(settings, "FIREBASE_CREDENTIALS_JSON", None)
            if cred_json_str and cred_json_str.strip():
                try:
                    cred_dict = json.loads(cred_json_str)
                    cred = credentials.Certificate(cred_dict)
                except Exception as ex:
                    logger.warning("Failed to parse FIREBASE_CREDENTIALS_JSON: %s", ex)

            # 2. Try file path
            if cred is None and settings.FIREBASE_CREDENTIALS_PATH:
                path = settings.FIREBASE_CREDENTIALS_PATH.strip()
                candidate_paths = [
                    path,
                    os.path.join("backend", path),
                    os.path.join(os.path.dirname(__file__), "..", "..", "..", path),
                ]
                resolved_path = None
                for cp in candidate_paths:
                    if os.path.exists(cp):
                        resolved_path = cp
                        break

                if resolved_path:
                    cred = credentials.Certificate(resolved_path)
                else:
                    logger.warning("FIREBASE_CREDENTIALS_PATH does not exist: %s", path)

            if cred:
                self._app = firebase_admin.initialize_app(cred)
                self._initialized = True
                logger.info("Firebase Admin SDK initialized successfully for FCM.")
                return True
            else:
                logger.warning("No valid Firebase credentials found for FirebaseFCMProvider.")
                return False

        except ImportError:
            logger.warning("firebase_admin package is not installed. Run 'pip install firebase-admin' to enable live FCM.")
            return False
        except Exception as e:
            logger.error("Failed to initialize Firebase Admin SDK: %s", e)
            return False

    async def send_push(
        self,
        fcm_token: str,
        title: str,
        body: str,
        data: Dict[str, Any],
    ) -> PushResult:
        if not self._ensure_initialized():
            return PushResult(
                success=False,
                provider_message_id=None,
                status="FAILED",
                is_simulated=False,
                error="Firebase Admin SDK is unconfigured or failed to initialize.",
            )

        try:
            from firebase_admin import messaging

            string_data = {k: str(v) for k, v in data.items()} if data else {}

            message = messaging.Message(
                notification=messaging.Notification(
                    title=title,
                    body=body,
                ),
                data=string_data,
                token=fcm_token,
                android=messaging.AndroidConfig(
                    priority="high",
                    notification=messaging.AndroidNotification(
                        channel_id="disaster_alerts",
                        priority="max",
                        sound="default",
                    ),
                ),
            )

            response = messaging.send(message)
            logger.info("FCM push successfully sent. Message ID: %s", response)
            return PushResult(
                success=True,
                provider_message_id=response,
                status="SENT",
                is_simulated=False,
            )

        except Exception as exc:
            logger.warning("FCM push delivery failed for token %s: %s", fcm_token[:8] + "...", exc)
            return PushResult(
                success=False,
                provider_message_id=None,
                status="FAILED",
                is_simulated=False,
                error=str(exc),
            )

