package org.mozilla.mozstumbler.client.mapview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.SharedConstants;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.MainActivity;

import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.overlay.GpsLocationProvider;
import com.mapbox.mapboxsdk.overlay.TilesOverlay;
import com.mapbox.mapboxsdk.overlay.UserLocationOverlay;
import com.mapbox.mapboxsdk.tileprovider.MapTileLayerBasic;
import com.mapbox.mapboxsdk.tileprovider.tilesource.MapboxTileLayer;
import com.mapbox.mapboxsdk.tileprovider.tilesource.TileLayer;
import com.mapbox.mapboxsdk.tileprovider.tilesource.WebSourceTileLayer;
import com.mapbox.mapboxsdk.views.MapView;

public final class MapActivity extends Activity {
    private static final String LOGTAG = MapActivity.class.getName();

    private static final String COVERAGE_REDIRECT_URL = "https://location.services.mozilla.com/map.json";
    private static String sCoverageUrl = null;
    private static final int MENU_REFRESH           = 1;
    private static final String ZOOM_KEY = "zoom";
    private static final float DEFAULT_ZOOM = 13;
    private static final String LAT_KEY = "latitude";
    private static final String LON_KEY = "longitude";

    private MapView mMap;
    private UserLocationOverlay mUserLocationOverlay;
    Timer mGetUrl = new Timer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_map);

        mMap = (MapView) this.findViewById(R.id.map);
        mMap.setTileSource(getTileSource());
        mUserLocationOverlay = addLocationOverlay(this, mMap);

        float zoomLevel = DEFAULT_ZOOM;
        if (savedInstanceState != null) {
            zoomLevel = savedInstanceState.getFloat(ZOOM_KEY, DEFAULT_ZOOM);
            if (savedInstanceState.containsKey(LAT_KEY) && savedInstanceState.containsKey(LON_KEY)) {
                final double latitude = savedInstanceState.getDouble(LAT_KEY);
                final double longitude = savedInstanceState.getDouble(LON_KEY);
                final LatLng center = new LatLng(latitude, longitude);
                Log.d(LOGTAG, "Setting LatLon: " + center);
                mMap.setCenter(center);
            }
        }
        mMap.setZoom(zoomLevel);

        // @TODO: we do a similar "read from URL" in Updater, AbstractCommunicator, make one function for this
        if (sCoverageUrl == null) {
            mGetUrl.schedule(new TimerTask() {
                @Override
                public void run() {
                    java.util.Scanner scanner;
                    try {
                        scanner = new java.util.Scanner(new URL(COVERAGE_REDIRECT_URL).openStream(), "UTF-8");
                    } catch (Exception ex) {
                        Log.d(LOGTAG, ex.toString());
                        if (SharedConstants.guiLogMessageBuffer != null)
                            SharedConstants.guiLogMessageBuffer.add("Failed to get coverage url:" + ex.toString());
                        return;
                    }
                    scanner.useDelimiter("\\A");
                    String result = scanner.next();
                    try {
                        sCoverageUrl = new JSONObject(result).getString("tiles_url");
                    } catch (JSONException ex) {
                        if (SharedConstants.guiLogMessageBuffer != null)
                            SharedConstants.guiLogMessageBuffer.add("Failed to get coverage url:" + ex.toString());
                    }
                    scanner.close();
                }
            }, 0);
        }

        //addCoverageTiles(mMap);
        Log.d(LOGTAG, "onCreate");
    }

    private void addCoverageTiles(MapView mapView) {
        TilesOverlay coverageTilesOverlay = mlsCoverageTilesOverlay(this, mapView);
        mapView.getOverlays().add(coverageTilesOverlay);
    }

    private static UserLocationOverlay addLocationOverlay(Activity activity, MapView mapView) {
        UserLocationOverlay userLocationOverlay = new UserLocationOverlay(
                new GpsLocationProvider(activity), mapView);
        userLocationOverlay.enableMyLocation();
        userLocationOverlay.setDrawAccuracyEnabled(true);
        mapView.setCenter(userLocationOverlay.getMyLocation());
        mapView.getOverlays().add(userLocationOverlay);
        return userLocationOverlay;
    }

    @TargetApi(11)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item = menu.add(Menu.NONE,MENU_REFRESH,Menu.NONE,R.string.refresh_map)
                .setIcon(R.drawable.ic_action_refresh);
        if (Build.VERSION.SDK_INT >= 11) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_REFRESH:
                if (mUserLocationOverlay != null) {
                    mMap.setCenter(mUserLocationOverlay.getMyLocation());
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private static TileLayer getTileSource() {
        if (BuildConfig.TILE_SERVER_URL == null) {
            return openStreetMapTileLayer();
        }
        return new MapboxTileLayer(BuildConfig.TILE_SERVER_URL);
    }

    private static TilesOverlay mlsCoverageTilesOverlay(Context context, MapView mapView) {
        final MapTileLayerBasic coverageTileProvider = new MapTileLayerBasic(context, mlsCoverageTileLayer(), mapView);
        final TilesOverlay coverageTileOverlay = new TilesOverlay(coverageTileProvider);
        coverageTileOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        return coverageTileOverlay;
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent i = new Intent(MainActivity.ACTION_UNPAUSE_SCANNING);
        Log.d(LOGTAG, "onStart");
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putFloat(ZOOM_KEY, mMap.getZoomLevel());
        bundle.putDouble(LON_KEY, mMap.getCenter().getLongitude());
        bundle.putDouble(LAT_KEY, mMap.getCenter().getLatitude());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(LOGTAG, "onDestroy");
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(LOGTAG, "onStop");
    }

    private static TileLayer openStreetMapTileLayer() {
        return new WebSourceTileLayer("openstreetmap",
                "http://tile.openstreetmap.org/{z}/{x}/{y}.png").setName("OpenStreetMap")
                .setAttribution("© OpenStreetMap Contributors")
                .setMinimumZoomLevel(1)
                .setMaximumZoomLevel(18);
    }

    private static TileLayer mlsCoverageTileLayer() {
        if (sCoverageUrl != null) {
            return new WebSourceTileLayer("mozilla", sCoverageUrl + "{z}/{x}/{y}.png")
                    .setName("Mozilla Location Service Coverage Map")
                    .setAttribution("© Mozilla Location Services Contributors")
                    .setMinimumZoomLevel(1)
                    .setMaximumZoomLevel(13);
        } else {
            return null;
        }
    }
}
