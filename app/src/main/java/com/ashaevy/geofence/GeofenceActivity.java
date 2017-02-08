package com.ashaevy.geofence;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.location.Geofence;
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

import java.util.Date;
import java.util.Map;

public class GeofenceActivity extends FragmentActivity implements
        OnMapReadyCallback, ControlsFragment.OnFragmentInteractionListener,
        GoogleMap.OnMarkerDragListener, GoogleMap.OnMapLongClickListener {

    public static final LatLng KIEV = new LatLng(50.4501, 30.5234);
    public static final float DEFAULT_RADIUS = 100;
    public static final double RADIUS_OF_EARTH_METERS = 6371009;

    public static final float DEFAULT_STROKE_WIDTH = 2;
    public static final int DEFAULT_FILL_COLOR = Color.parseColor("#4de95367");
    public static final int DEFAULT_STROKE_COLOR = Color.BLACK;

    private GoogleMap mMap;
    private DraggableCircle mGeofenceCircle;
    private GeofenceHelper mGeofenceHelper;

    private BroadcastReceiver geofenceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int transitionType = intent.getIntExtra(GeofenceTransitionsIntentService.KEY_GEOFENCE_UPDATE_TYPE, -1);
            if (transitionType != -1) {
                switch (transitionType) {
                    case Geofence.GEOFENCE_TRANSITION_ENTER:
                        ((TextView) findViewById(R.id.geofence_state)).setText(R.string.geofence_state_inside);
                        return;
                    case Geofence.GEOFENCE_TRANSITION_EXIT:
                        ((TextView) findViewById(R.id.geofence_state)).setText(R.string.geofence_state_outsize);
                        return ;
                    default:
                        ((TextView) findViewById(R.id.geofence_state)).setText(R.string.geofence_state_unknown);
                        return ;
                }
            }
        }
    };

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
    };

    private MapLocationSource mLocationSource = new MapLocationSource();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_geofence);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        findViewById(R.id.button_set_current_wifi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String currentSsid = getCurrentSsid(GeofenceActivity.this);
                if (currentSsid != null) {
                    ((TextInputEditText) findViewById(R.id.input_wifi_name)).
                            setText(currentSsid);
                }
            }
        });

        findViewById(R.id.start_geofencing).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mGeofenceHelper.setGeofence("GEOFENCE_CIRCLE",
                        mGeofenceCircle.centerMarker.getPosition(), ((float) mGeofenceCircle.radius));
            }
        });

        findViewById(R.id.stop_geofencing).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO check client
                mGeofenceHelper.disableMockLocation();
                mGeofenceHelper.removeGeofence();
                ((TextView) findViewById(R.id.geofence_state)).setText(R.string.geofence_state_unknown);
            }
        });

        findViewById(R.id.button_random_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Location mockLocation = generateRandomTestLocation();
                mGeofenceHelper.setMockLocation(mockLocation);
                mLocationSource.setLocation(mockLocation);
            }
        });

        mGeofenceHelper = new GeofenceHelper(this);
        mGeofenceHelper.create();
    }

    public Location generateRandomTestLocation() {
        Location location = new Location(LocationManager.NETWORK_PROVIDER);
        location.setLatitude(KIEV.latitude + Math.random() * 0.1);
        location.setLongitude(KIEV.longitude + Math.random() * 0.1);
        location.setTime(new Date().getTime());
        location.setAccuracy(3.0f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            location.setElapsedRealtimeNanos(System.nanoTime());
        }

        return location;
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GeofenceTransitionsIntentService.GEOFENCE_UPDATED);
        LocalBroadcastManager.getInstance(this).registerReceiver(geofenceUpdateReceiver, filter);
        mGeofenceHelper.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGeofenceHelper.stop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(geofenceUpdateReceiver);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setOnMapLongClickListener(this);
        mMap.setOnMarkerDragListener(this);

        mGeofenceCircle = new DraggableCircle(KIEV, DEFAULT_RADIUS, true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(KIEV, getZoomLevel(mGeofenceCircle.circle)));

        // FIXME add premission check

        mMap.setLocationSource(mLocationSource);
        mMap.setMyLocationEnabled(true);
    }

    public int getZoomLevel(Circle circle) {
        int zoomLevel = 11;
        if (circle != null) {
            double radius = circle.getRadius() + circle.getRadius() / 2;
            double scale = radius / 500;
            zoomLevel = (int) (16 - Math.log(scale) / Math.log(2));
        }
        return zoomLevel;
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

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
        ((TextInputEditText) findViewById(R.id.input_point_x)).setText(String.valueOf(position.latitude));
        ((TextInputEditText) findViewById(R.id.input_point_y)).setText(String.valueOf(position.longitude));
        ((TextInputEditText) findViewById(R.id.input_radius)).setText(String.valueOf(mGeofenceCircle.radius));
    }

    @Override
    public void onMapLongClick(LatLng latLng) {

    }

    private class DraggableCircle {
        private final Marker centerMarker;
        private final Marker radiusMarker;
        private final Circle circle;
        private double radius;

        public DraggableCircle(LatLng center, double radius, boolean clickable) {
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

        public boolean onMarkerMoved(Marker marker) {
            if (marker.equals(centerMarker)) {
                circle.setCenter(marker.getPosition());
                radiusMarker.setPosition(toRadiusLatLng(marker.getPosition(), radius));
                return true;
            }
            if (marker.equals(radiusMarker)) {
                radius = toRadiusMeters(centerMarker.getPosition(), radiusMarker.getPosition());
                circle.setRadius(radius);
                return true;
            }
            return false;
        }

        public void setClickable(boolean clickable) {
            circle.setClickable(clickable);
        }
    }

    public static String getCurrentSsid(Context context) {
        String ssid = null;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo == null) {
            return null;
        }

        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {
                ssid = connectionInfo.getSSID();
            }
        }

        return ssid;
    }

    /** Generate LatLng of radius marker */
    private static LatLng toRadiusLatLng(LatLng center, double radius) {
        double radiusAngle = Math.toDegrees(radius / RADIUS_OF_EARTH_METERS) /
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
