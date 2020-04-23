package edu.temple.businfo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, SeptaInterface.SeptaResponseListener {

    private final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private GoogleMap map;
    private SupportMapFragment mapFragment;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Marker userMarker;
    private Map<Integer,BusStop> busStops = new HashMap<>();
    private Map<Integer,Bus> buses = new HashMap<>();
    private Map<Integer,BusStopTime> busStopTimes = new HashMap<>();
    private SeptaInterface septaApi;
    private BusStop currentBusStop;
    private BitmapDescriptor busIcon;
    private BitmapDescriptor busStopIcon;
    private TextView busStopTextView;

    private Timer busTimer;
    private BusLocationTask busLocationTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        busIcon = BitmapDescriptorFactory.fromResource(R.drawable.ic_bus);
        busStopIcon = BitmapDescriptorFactory.fromResource(R.drawable.ic_bus_stop);
        busStopTextView = findViewById(R.id.busStopTextView);
        septaApi = new SeptaInterface(this);

        // Check to see if permission is granted
        //request permission if not granted
        if (checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Permission already granted
            // initialize activity
            intializeLocationServices();

            //request location updates
            updateLocation();
        }
    }

    private void intializeLocationServices(){
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {

                //stop requesting updates when GPS location received
                //get bus stops in range on new location
                if( location.getProvider().equals( LocationManager.GPS_PROVIDER ) ){
                    locationManager.removeUpdates(locationListener);
                    Log.d("SEPTA","calling get bus stops.");
                    try {
                        septaApi.getBusStops(location.getLatitude(), location.getLongitude());
                    }catch (UnsupportedEncodingException e){
                        e.printStackTrace();
                    }
                }
                Toast.makeText(MainActivity.this, location.getProvider(), Toast.LENGTH_SHORT).show();

                //update|add user marker and move camera
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 15);
                if (map != null) {
                    map.animateCamera(cameraUpdate);
                    if (userMarker == null) {
                        userMarker = map.addMarker(new MarkerOptions().position(latLng)
                                .title("My Current Location"));
                        userMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
                    } else {
                        userMarker.setPosition(latLng);
                    }
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        if( busTimer == null ) {
            busTimer = new Timer();
            busLocationTask = new BusLocationTask();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        locationManager.removeUpdates(locationListener);
    }

    /**
     * request location updates
     * initialize location / map services if necessary
     *
     */
    @SuppressLint("MissingPermission")
    private void updateLocation(){
        //get updates every 100ms
        if( locationManager == null || locationListener == null ){
            intializeLocationServices();
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, locationListener);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setZoomControlsEnabled(true);

        map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {

                try {
                    BusStop stop = ((BusStop) marker.getTag());
                    if (stop != null) {
                        busStopTextView.setText(stop.getName());
                        Toast.makeText(MainActivity.this, stop.getName(), Toast.LENGTH_SHORT).show();
                        try {
                            //get bus stop schedule if the route data has not been set
                            //otherwise get bus locations for stop
                            currentBusStop = stop;
                            if (currentBusStop.getRoutes() == null) {
                                septaApi.getBusSchedule(stop.getId());
                            }else{
                                septaApi.getBusLocations( currentBusStop.getRoutes().get(0) );
                            }
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                }catch(ClassCastException e){
                    e.printStackTrace();
                }

                return false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //get user location
                //getLastKnownLocation();
                updateLocation();
            }else {
                Log.d("Permissions Result", "Permission denied");
                Toast.makeText(this, "This app requires access to location.", Toast.LENGTH_LONG).show();
            }
        }
    }

    //Helper to get bus locations on timer
    public class BusLocationTask extends TimerTask
    {
        private Boolean started = false;

        @Override
        public void run() {
            started = true;
            septaApi.getBusLocations(currentBusStop.getRoutes().get(0));
        }

        public boolean isRunning(){ return started; }
    }

    /**
     * handle success response for bus stop locations close to user
     *
     * @param data
     */
    private void handleBusStopLocationsResponse( JSONArray data ){
        for(BusStop stop : busStops.values() ){
            stop.getMapMarker().remove();
        }
        busStops.clear();

        Log.d("septa", "data len: " + data.length());

        for(int i = 0; i < data.length(); i++ ){
            try {
                BusStop busStop = new BusStop(data.getJSONObject(i));
                LatLng location = new LatLng( busStop.getLat(), busStop.getLon() );
                Marker marker = map.addMarker(new MarkerOptions().position(location)
                        .title(busStop.getName())
                        .snippet("id: " + busStop.getId())
                        .icon(busStopIcon));
                marker.setTag(busStop);
                busStop.setMapMarker(marker);
                busStops.put(busStop.getId(), busStop);
            }catch(JSONException e){
                e.printStackTrace();
            }
        }

        Log.d("septa","bus stop locations complete.");
    }

    /**
     * handle success response for bus stop schedule response
     * mainly used to get routes serviced by bus stop
     *
     * sets BusLocationsTask on Timer schedule
     *
     * @param data
     */
    private void handleBusStopScheduleResponse(JSONArray data){
        try {
            JSONObject schedule = data.getJSONObject(1);
            JSONObject schedule_item;
            int stop_id = data.getInt(0);
            Iterator<String> keys = schedule.keys();
            ArrayList<String> routes = new ArrayList<>();
            String key;
            while( keys.hasNext() ){
                key = keys.next();
                routes.add( key );
                schedule_item = schedule.getJSONArray( key ).getJSONObject(0);
                Objects.requireNonNull(busStops.get(stop_id))
                        .setDirection( schedule_item.getString("DirectionDesc") );
            }
            Objects.requireNonNull(busStops.get(stop_id)).setRoutes( routes );

            //schedule bus locations to update every 10 seconds
            if( ! busLocationTask.isRunning() ){
                busTimer.scheduleAtFixedRate(busLocationTask,0,10000);
            }
            //septaApi.getBusLocations( routes.get(0) );

        }catch (JSONException e){
            e.printStackTrace();
        }
    }

    /**
     * handle success response for bus locations response
     *
     * @param data
     */
    private void handleBusLocationsResponse( JSONArray data ){

        //remove previous bus markers
        for(Bus bus : buses.values() ){
            bus.getMapMarker().remove();
        }
        buses.clear();

        //update buses, set new bus markers
        List<String> trip_ids = new ArrayList<String>();
        if( currentBusStop != null ){
            for(int i = 0; i < data.length(); i++ ){
                try {
                    Bus bus = new Bus(data.getJSONObject(i));
                    if( currentBusStop.isForDestination( bus.getDestination() ) ){
                        LatLng location = new LatLng( bus.getLat(), bus.getLon() );
                        Marker marker = map.addMarker(new MarkerOptions().position(location)
                                .title("route:" + currentBusStop.getRoutes().get(0))
                                .snippet("ETA: ")
                                .icon(busIcon));
                        marker.setTag(bus);
                        bus.setMapMarker(marker);
                        buses.put(bus.getId(), bus);
                        trip_ids.add( String.valueOf(bus.getTrip()) );
                    }
                }catch(JSONException e){
                    e.printStackTrace();
                }
            }

            //get bus arrival times
            try {
                septaApi.getBusStopTimes(currentBusStop.getId(), TextUtils.join(",", trip_ids));
            }catch (UnsupportedEncodingException e){
                e.printStackTrace();
            }
            zoomToFitMarkers();
            Toast.makeText(this,"Bus locations updated", Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(this,"Bus Stop not selected", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * handle success response for bus stop times response
     * used to get scheduled arrival times and set ETA
     *
     * @param data
     */
    private void handleBusStopTimesResponse( JSONArray data ){
        //set busStopTimes
        busStopTimes.clear();
        for(int i = 0; i < data.length(); i++ ){
            try {
                BusStopTime busStopTime = new BusStopTime(data.getJSONObject(i));
                busStopTimes.put( busStopTime.getTripId(), busStopTime );
            }catch(JSONException e){
                e.printStackTrace();
            }
        }

        //update bus arrival times
        for(Bus bus : buses.values() ){
            BusStopTime busStopTime = busStopTimes.get(bus.getTrip());
            if( busStopTime != null ){
                if( currentBusStop.getId() == busStopTime.getStopId() ){
                    bus.setArrivalTime( busStopTime.getArrivalTime() );
                    if( bus.isPastStop() ){
                        bus.getMapMarker().remove();
                    }
                }
            }
        }

        zoomToFitMarkers();
    }

    /**
     * update map zoom to show all markers
     */
    private void zoomToFitMarkers(){
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for(Bus bus : buses.values()){
            builder.include(bus.getMapMarker().getPosition());
        }
        for(BusStop stop : busStops.values()){
            builder.include(stop.getMapMarker().getPosition());
        }
        LatLngBounds bound = builder.build();
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bound, 15);
        map.animateCamera(cameraUpdate);
    }

    /**
     * callback for success response for SeptaInterface
     *
     * @param data
     * @param response_for
     */
    @Override
    public void response(JSONArray data, String response_for ) {
        switch (response_for) {
            case SeptaInterface.BUS_STOP_LOCATIONS_RESPONSE:
                handleBusStopLocationsResponse(data);
                break;
            case SeptaInterface.BUS_STOP_SCHEDULE_RESPONSE:
                handleBusStopScheduleResponse(data);
                break;
            case SeptaInterface.BUS_LOCATIONS_RESPONSE:
                handleBusLocationsResponse(data);
                break;
            case SeptaInterface.BUS_STOP_TIMES_RESPONSE:
                handleBusStopTimesResponse(data);
                break;
        }
    }

    /**
     * callback for error response for SeptaInterface
     *
     * @param error
     * @param response_for
     */
    @Override
    public void errorResponse(String error, String response_for) {
        if( error != null ) {
            Log.d("SEPTA", error);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }else{
            Toast.makeText(this, "SEPTA api error", Toast.LENGTH_LONG).show();
        }
    }
}
