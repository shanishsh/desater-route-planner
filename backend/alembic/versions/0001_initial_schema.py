"""Initial database schema.

Revision ID: 0001_initial_schema
Revises: 
Create Date: 2026-10-23 12:00:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa

revision: str = '0001_initial_schema'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'users',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('email', sa.String(length=255), nullable=False),
        sa.Column('hashed_password', sa.String(length=255), nullable=False),
        sa.Column('role', sa.String(length=50), nullable=False),
        sa.Column('is_active', sa.Boolean(), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index('ix_users_email', 'users', ['email'], unique=True)

    op.create_table(
        'itineraries',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('title', sa.String(length=200), nullable=False),
        sa.Column('description', sa.Text(), nullable=False),
        sa.Column('region', sa.String(length=100), nullable=False),
        sa.Column('difficulty', sa.String(length=50), nullable=False),
        sa.Column('distance_km', sa.Float(), nullable=False),
        sa.Column('estimated_duration_hours', sa.Float(), nullable=False),
        sa.Column('start_latitude', sa.Float(), nullable=False),
        sa.Column('start_longitude', sa.Float(), nullable=False),
        sa.Column('end_latitude', sa.Float(), nullable=False),
        sa.Column('end_longitude', sa.Float(), nullable=False),
        sa.Column('route_geojson', sa.JSON(), nullable=False),
        sa.Column('is_active', sa.Boolean(), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index('ix_itineraries_region', 'itineraries', ['region'])

    op.create_table(
        'route_segments',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('itinerary_id', sa.String(length=36), sa.ForeignKey('itineraries.id', ondelete='CASCADE'), nullable=False),
        sa.Column('sequence_order', sa.Integer(), nullable=False),
        sa.Column('name', sa.String(length=150), nullable=False),
        sa.Column('elevation_gain_m', sa.Float(), nullable=False),
        sa.Column('hazard_level', sa.String(length=50), nullable=False),
        sa.Column('segment_geojson', sa.JSON(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index('ix_route_segments_itinerary_id', 'route_segments', ['itinerary_id'])

    op.create_table(
        'map_points',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('name', sa.String(length=200), nullable=False),
        sa.Column('category', sa.String(length=50), nullable=False),
        sa.Column('latitude', sa.Float(), nullable=False),
        sa.Column('longitude', sa.Float(), nullable=False),
        sa.Column('address', sa.String(length=255), nullable=True),
        sa.Column('phone', sa.String(length=50), nullable=True),
        sa.Column('description', sa.Text(), nullable=True),
        sa.Column('verification_status', sa.String(length=50), nullable=False),
        sa.Column('verification_timestamp', sa.DateTime(timezone=True), nullable=False),
        sa.Column('provenance', sa.String(length=255), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index('ix_map_points_category', 'map_points', ['category'])
    op.create_index('ix_map_points_lat_lon', 'map_points', ['latitude', 'longitude'])

    op.create_table(
        'alerts',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('title', sa.String(length=200), nullable=False),
        sa.Column('description', sa.Text(), nullable=False),
        sa.Column('severity', sa.String(length=50), nullable=False),
        sa.Column('category', sa.String(length=50), nullable=False),
        sa.Column('affected_itinerary_id', sa.String(length=36), sa.ForeignKey('itineraries.id', ondelete='SET NULL'), nullable=True),
        sa.Column('affected_segment_id', sa.String(length=36), sa.ForeignKey('route_segments.id', ondelete='SET NULL'), nullable=True),
        sa.Column('source_name', sa.String(length=100), nullable=False),
        sa.Column('source_url', sa.String(length=255), nullable=True),
        sa.Column('source_published_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('retrieved_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('freshness_threshold_seconds', sa.Integer(), nullable=False),
        sa.Column('is_simulation', sa.Boolean(), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index('ix_alerts_severity_category', 'alerts', ['severity', 'category'])
    op.create_index('ix_alerts_affected_itinerary_id', 'alerts', ['affected_itinerary_id'])

    op.create_table(
        'weather_snapshots',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('latitude', sa.Float(), nullable=False),
        sa.Column('longitude', sa.Float(), nullable=False),
        sa.Column('temperature_c', sa.Float(), nullable=False),
        sa.Column('relative_humidity_pct', sa.Float(), nullable=False),
        sa.Column('wind_speed_kmh', sa.Float(), nullable=False),
        sa.Column('precipitation_mm', sa.Float(), nullable=False),
        sa.Column('precipitation_probability_pct', sa.Float(), nullable=False),
        sa.Column('weather_code', sa.Integer(), nullable=False),
        sa.Column('weather_description', sa.String(length=100), nullable=False),
        sa.Column('forecast_valid_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('retrieved_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('expires_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index('ix_weather_lat_lon', 'weather_snapshots', ['latitude', 'longitude'])

    op.create_table(
        'crowd_snapshots',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('itinerary_id', sa.String(length=36), sa.ForeignKey('itineraries.id', ondelete='CASCADE'), nullable=False),
        sa.Column('location_name', sa.String(length=150), nullable=False),
        sa.Column('crowd_level', sa.String(length=50), nullable=False),
        sa.Column('density_description', sa.String(length=200), nullable=False),
        sa.Column('is_simulation', sa.Boolean(), nullable=False),
        sa.Column('observed_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index('ix_crowd_itinerary_observed', 'crowd_snapshots', ['itinerary_id', 'observed_at'])

    op.create_table(
        'audit_events',
        sa.Column('id', sa.String(length=36), primary_key=True),
        sa.Column('user_id', sa.String(length=36), nullable=True),
        sa.Column('action', sa.String(length=100), nullable=False),
        sa.Column('resource_type', sa.String(length=100), nullable=False),
        sa.Column('resource_id', sa.String(length=100), nullable=True),
        sa.Column('details', sa.JSON(), nullable=True),
        sa.Column('ip_address', sa.String(length=45), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
    )


def downgrade() -> None:
    op.drop_table('audit_events')
    op.drop_table('crowd_snapshots')
    op.drop_table('weather_snapshots')
    op.drop_table('alerts')
    op.drop_table('map_points')
    op.drop_table('route_segments')
    op.drop_table('itineraries')
    op.drop_table('users')

