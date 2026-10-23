"""Notification Dispatcher implementing severity policies, deduplication, and multi-channel delivery."""
import json
import logging
from datetime import datetime, timezone
from typing import List, Optional
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.disaster_alert import DisasterAlert
from app.models.subscriber import Subscriber
from app.models.device_installation import DeviceInstallation
from app.models.notification_delivery import NotificationDelivery
from app.services.sms.factory import get_sms_provider
from app.services.push.factory import get_push_provider

logger = logging.getLogger(__name__)


class NotificationDispatcher:
    def __init__(self, session: AsyncSession):
        self.session = session
        self.sms_provider = get_sms_provider()
        self.push_provider = get_push_provider()

    async def dispatch_for_alert(self, alert_id: str) -> List[NotificationDelivery]:
        """Process notifications for an alert according to severity and delivery policy."""
        stmt = select(DisasterAlert).where(DisasterAlert.id == alert_id)
        result = await self.session.execute(stmt)
        alert = result.scalar_one_or_none()

        if not alert:
            logger.error("Alert not found for dispatch: %s", alert_id)
            return []

        # Do not dispatch new alerts if already cancelled or expired
        if alert.status in ("cancelled", "expired"):
            logger.info("Alert %s is %s; skipping notification dispatch.", alert_id, alert.status)
            return []

        deliveries: List[NotificationDelivery] = []

        # 1. FCM Push Delivery (Applicable for all severities: INFO, WARNING, SEVERE, CRITICAL)
        push_deliveries = await self._dispatch_push_notifications(alert)
        deliveries.extend(push_deliveries)

        # 2. SMS Delivery: Triggered for SEVERE or CRITICAL disaster alerts
        if alert.severity in ("SEVERE", "CRITICAL"):
            sms_deliveries = await self._dispatch_sms_notifications(alert)
            deliveries.extend(sms_deliveries)
        else:
            logger.info(
                "Alert %s severity is %s; SMS fallback omitted per policy (reserved for SEVERE/CRITICAL).",
                alert_id,
                alert.severity,
            )

        await self.session.commit()
        return deliveries

    async def _dispatch_sms_notifications(self, alert: DisasterAlert) -> List[NotificationDelivery]:
        deliveries: List[NotificationDelivery] = []

        # Query all verified, active subscribers with explicit consent
        stmt = select(Subscriber).where(
            Subscriber.status == "ACTIVE",
            Subscriber.consent_given == True,  # noqa: E712
        )
        result = await self.session.execute(stmt)
        subscribers = result.scalars().all()

        for sub in subscribers:
            # Check geographic targeting
            if not self._is_subscriber_targeted(sub, alert):
                continue

            # Deduplication: Check if already sent or queued for this alert
            dup_stmt = select(NotificationDelivery).where(
                NotificationDelivery.alert_id == alert.id,
                NotificationDelivery.subscriber_id == sub.id,
                NotificationDelivery.channel == "SMS",
            )
            dup_res = await self.session.execute(dup_stmt)
            if dup_res.scalar_one_or_none():
                logger.info("SMS deduplicated for subscriber %s and alert %s", sub.id, alert.id)
                continue

            # Check simulation safeguard: Never send real SMS for simulated alerts
            is_simulated = alert.is_simulated or (alert.source_verification_status == "SIMULATED")
            message_prefix = "[DISASTER ALERT]"
            if is_simulated:
                message_prefix = "[SIMULATION TEST - NO REAL DISASTER]"

            message_body = (
                f"{message_prefix} {alert.title.upper()} ({alert.severity}). "
                f"Instructions: {alert.required_protective_instructions}. "
                f"Source: {alert.issuing_authority}. Reply STOP to unsubscribe."
            )

            idempotency_key = f"sms_{alert.id}_{sub.id}_{alert.version}"
            
            # Dispatch through SMS provider
            sms_result = await self.sms_provider.send_sms(
                to_phone=sub.phone_e164,
                message=message_body,
                alert_id=alert.id,
                idempotency_key=idempotency_key,
            )

            masked_phone = sub.phone_e164[:3] + "****" + sub.phone_e164[-4:] if len(sub.phone_e164) > 7 else "***"

            delivery = NotificationDelivery(
                alert_id=alert.id,
                channel="SMS",
                subscriber_id=sub.id,
                recipient_address=masked_phone,
                provider_message_id=sms_result.provider_message_id,
                status=sms_result.status,
                is_simulated=is_simulated or sms_result.is_simulated,
                error_details=sms_result.error,
                delivered_at=datetime.now(timezone.utc) if sms_result.status == "DELIVERED" else None,
            )
            self.session.add(delivery)
            deliveries.append(delivery)

        return deliveries

    async def _dispatch_push_notifications(self, alert: DisasterAlert) -> List[NotificationDelivery]:
        deliveries: List[NotificationDelivery] = []

        stmt = select(DeviceInstallation).where(
            DeviceInstallation.is_active == True,  # noqa: E712
            DeviceInstallation.fcm_token.isnot(None),
        )
        result = await self.session.execute(stmt)
        devices = result.scalars().all()

        is_simulated = alert.is_simulated or (alert.source_verification_status == "SIMULATED")
        title_prefix = "⚠️ DISASTER WARNING: "
        if is_simulated:
            title_prefix = "🧪 [SIMULATED] DISASTER WARNING: "

        push_title = f"{title_prefix}{alert.title}"
        push_body = f"[{alert.severity}] {alert.required_protective_instructions}"

        push_data = {
            "alert_id": alert.id,
            "severity": alert.severity,
            "category": alert.hazard_category,
            "version": str(alert.version),
            "is_simulated": str(is_simulated).lower(),
            "center_lat": str(alert.center_latitude),
            "center_lng": str(alert.center_longitude),
            "radius_meters": str(alert.affected_radius_meters),
            "authority": alert.issuing_authority,
        }

        for dev in devices:
            if not dev.fcm_token:
                continue

            # Deduplication
            dup_stmt = select(NotificationDelivery).where(
                NotificationDelivery.alert_id == alert.id,
                NotificationDelivery.device_id == dev.id,
                NotificationDelivery.channel == "FCM_PUSH",
            )
            dup_res = await self.session.execute(dup_stmt)
            if dup_res.scalar_one_or_none():
                continue

            push_res = await self.push_provider.send_push(
                fcm_token=dev.fcm_token,
                title=push_title,
                body=push_body,
                data=push_data,
            )

            masked_token = dev.fcm_token[:8] + "..." + dev.fcm_token[-6:] if len(dev.fcm_token) > 14 else "***"

            delivery = NotificationDelivery(
                alert_id=alert.id,
                channel="FCM_PUSH",
                device_id=dev.id,
                recipient_address=masked_token,
                provider_message_id=push_res.provider_message_id,
                status=push_res.status,
                is_simulated=is_simulated or push_res.is_simulated,
                error_details=push_res.error,
                delivered_at=datetime.now(timezone.utc) if push_res.status == "SENT" else None,
            )
            self.session.add(delivery)
            deliveries.append(delivery)

        return deliveries

    def _is_subscriber_targeted(self, subscriber: Subscriber, alert: DisasterAlert) -> bool:
        """Evaluate if the subscriber is within the geographic scope of the alert."""
        # 1. If subscriber subscribed to specific route
        if subscriber.subscribed_itinerary_id and alert.affected_route_id:
            if subscriber.subscribed_itinerary_id == alert.affected_route_id:
                return True

        # 2. Check subscribed areas / districts
        if subscriber.subscribed_areas_json:
            try:
                areas = json.loads(subscriber.subscribed_areas_json)
                if not areas or "all" in [a.lower() for a in areas]:
                    return True
                # Match against alert authority, title, or description
                alert_text = f"{alert.title} {alert.description} {alert.issuing_authority}".lower()
                for area in areas:
                    if area.lower() in alert_text:
                        return True
            except Exception:
                pass
            return False

        # If subscriber has no area restrictions, default to targeted
        return True

