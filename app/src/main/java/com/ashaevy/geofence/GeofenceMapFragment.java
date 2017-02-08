package com.ashaevy.geofence;

import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;

import com.ashaevy.geofence.data.GeofenceData;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * Created by ashaevy on 08.02.17.
 */

public class GeofenceMapFragment extends SupportMapFragment implements GeofenceContract.MapView,
        OnMapReadyCallback, GoogleMap.OnMarkerDragListener {

    private static final float DEFAULT_STROKE_WIDTH = 2;
    private static final int DEFAULT_FILL_COLOR = Color.parseColor("#4de95367");
    private static final int DEFAULT_STROKE_COLOR = Color.BLACK;

    private GoogleMap mMap;
    private DraggableCircle mGeofenceCircle;

    private GeofenceContract.Presenter mPresenter;

    @Override
    public void updateGeofence(GeofenceData geofenceData) {
        LatLng position = new LatLng(geofenceData.getLatitude(), geofenceData.getLongitude());
        mGeofenceCircle.updateCircleParams(position, geofenceData.getRadius());
    }

    @Override
    public void setLocation(Location location) {
        mLocationSource.setLocation(location);
    }

    private static class MapLocationSource implements LocationSource {
        private OnLocationChangedListener mOnLocationChangedListener;

        @Override
        public void activate(OnLocationChangedListener onLocationChangedListener) {
            mOnLocationChangedListener = onLocationChangedListener;
        }

        @Override
        public void deactivate() {
            mOnLocationChangedListener = null;
        }

        public boolean setLocation(Location location) {
            if (mOnLocationChangedListener != null) {
                mOnLocationChangedListener.onLocationChanged(location);
                return true;
            }
            return false;
        }
    }

    private MapLocationSource mLocationSource = new MapLocationSource();

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setOnMarkerDragListener(this);

        mGeofenceCircle = new DraggableCircle(Constants.KIEV, Constants.DEFAULT_RADIUS, true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(Constants.KIEV, getZoomLevel(mGeofenceCircle.circle)));

        // FIXME add premission check

        mMap.setLocationSource(mLocationSource);
        mMap.setMyLocationEnabled(true);
    }

    @Override
    public void onMarkerDrag(Marker marker) {
        onMarkerMoved(marker);
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
        onMarkerMoved(marker);
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        onMarkerMoved(marker);
    }

    private void onMarkerMoved(Marker marker) {
        mGeofenceCircle.onMarkerMoved(marker);
        updateEdits();
    }

    private void updateEdits() {
        LatLng position = mGeofenceCircle.centerMarker.getPosition();
        GeofenceData geofenceData = new GeofenceData();
        geofenceData.setLatitude(position.latitude);
        geofenceData.setLongitude(position.longitude);
        geofenceData.setRadius(mGeofenceCircle.radius);
        mPresenter.updateGeofenceFromMap(geofenceData);
    }

    private int getZoomLevel(Circle circle) {
        int zoomLevel = 11;
        if (circle != null) {
            double radius = circle.getRadius() + circle.getRadius() / 2;
            double scale = radius / 500;
            zoomLevel = (int) (16 - Math.log(scale) / Math.log(2));
        }
        return zoomLevel;
    }

    @Override
    public void setPresenter(GeofenceContract.Presenter presenter) {
        mPresenter = presenter;
    }

    private class DraggableCircle {
        private final Marker centerMarker;
        private final Marker radiusMarker;
        private final Circle circle;
        private double radius;

        DraggableCircle(LatLng center, double radius, boolean clickable) {
            this.radius = radius;
            centerMarker = mMap.addMarker(new MarkerOptions()
                    .position(center)
                    .draggable(true));
            radiusMarker = mMap.addMarker(new MarkerOptions()
                    .position(toRadiusLatLng(center, radius))
                    .draggable(true)
                    .icon(BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_AZURE)));
            circle = mMap.addCircle(new CircleOptions()
                    .center(center)
                    .radius(radius)
                    .strokeWidth(DEFAULT_STROKE_WIDTH)
                    .strokeColor(DEFAULT_STROKE_COLOR)
                    .fillColor(DEFAULT_FILL_COLOR)
                    .clickable(clickable));
        }

        boolean onMarkerMoved(Marker marker) {
            if (marker.equals(centerMarker)) {
                moveCircle(marker.getPosition());
                return true;
            }
            if (marker.equals(radiusMarker)) {
                radius = toRadiusMeters(centerMarker.getPosition(), radiusMarker.getPosition());
                circle.setRadius(radius);
                return true;
            }
            return false;
        }

        public void moveCircle(LatLng position) {
            circle.setCenter(position);
            radiusMarker.setPosition(toRadiusLatLng(position, radius));
        }

        public void setRadius(double radius) {
            this.radius = radius;
            radiusMarker.setPosition(toRadiusLatLng(centerMarker.getPosition(), radius));
        }

        public void updateCircleParams(LatLng position, double radius) {
            circle.setCenter(position);
            this.radius = radius;
            radiusMarker.setPosition(toRadiusLatLng(centerMarker.getPosition(), radius));
        }

        public void setClickable(boolean clickable) {
            circle.setClickable(clickable);
        }
    }

    /** Generate LatLng of radius marker */
    private static LatLng toRadiusLatLng(LatLng center, double radius) {
        double radiusAngle = Math.toDegrees(radius / Constants.RADIUS_OF_EARTH_METERS) /
                Math.cos(Math.toRadians(center.latitude));
        return new LatLng(center.latitude, center.longitude + radiusAngle);
    }

    private static double toRadiusMeters(LatLng center, LatLng radius) {
        float[] result = new float[1];
        Location.distanceBetween(center.latitude, center.longitude,
                radius.latitude, radius.longitude, result);
        return result[0];
    }
}