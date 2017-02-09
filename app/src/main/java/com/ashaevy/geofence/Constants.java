package com.ashaevy.geofence;

import com.google.android.gms.maps.model.LatLng;

/**
 * Constants used in Application.
 *
 */
public final class Constants {

    private Constants() {
    }

    public static final String PACKAGE_NAME = Constants.class.getPackage().getName();

    public static final String SHARED_PREFERENCES_NAME = PACKAGE_NAME + ".SHARED_PREFERENCES_NAME";

    public static final String GEOFENCES_ADDED_KEY = PACKAGE_NAME + ".GEOFENCES_ADDED_KEY";
    public static final String GEOFENCE_DATA_KEY = PACKAGE_NAME + ".GEOFENCE_DATA_KEY";

    public static final double RADIUS_OF_EARTH_METERS = 6371009;

    public static final LatLng KIEV = new LatLng(50.4501, 30.5234);
    public static final float DEFAULT_RADIUS = 100;


}
