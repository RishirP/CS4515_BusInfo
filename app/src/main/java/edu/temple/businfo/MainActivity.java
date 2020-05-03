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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.android.material.textfield.TextInputEditText;

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
    private final String BUS_STOP_ID_KEY = "bus_stop_id";

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
    private int savedBusStopId = 0;
    private BitmapDescriptor busIcon;
    private BitmapDescriptor busStopIcon;
    private BitmapDescriptor busStopSelectedIcon;
    private TextView busStopTextView;
    private TextInputEditText busEtaEditText;
    private Button setEtaButton;
    private Button gpsPermissionButton;

    private int etaThreshold = 20; //default threshold of 20 minutes

    private Timer busTimer;
    private BusLocationTask busLocationTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if( savedInstanceState != null ) {
            savedBusStopId = savedInstanceState.getInt(BUS_STOP_ID_KEY, 0);
        }

        busIcon = BitmapDescriptorFactory.fromResource(R.drawable.ic_bus);
        busStopIcon = BitmapDescriptorFactory.fromResource(R.drawable.ic_bus_stop);
        busStopSelectedIcon = BitmapDescriptorFactory.fromResource(R.drawable.ic_bus_stop_selected);
        busStopTextView = findViewById(R.id.busStopTextView);
        busEtaEditText = findViewById(R.id.busEtaTextInput);
        setEtaButton = findViewById(R.id.setEtaButton);
        busEtaEditText.setText( String.valueOf(etaThreshold) );
        gpsPermissionButton = findViewById(R.id.gpsPermissionButton);

        septaApi = new SeptaInterface(this);

        gpsPermissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            }
        });

        // Check to see if permission is granted
        //request permission if not granted
        if (checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            gpsPermissionButton.setVisibility(View.VISIBLE);
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

        setEtaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etaThreshold = Integer.parseInt( busEtaEditText.getText().toString() );
                Log.d("septa","eta threshold: " + etaThreshold);
                try {
                    if(currentBusStop != null) {
                        busTimer.cancel();
                        septaApi.getBusSchedule(currentBusStop.getId());
                    }else{
                        Toast.makeText(MainActivity.this,"Please select a bus stop",Toast.LENGTH_SHORT).show();
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if( locationManager != null ) {
            locationManager.removeUpdates(locationListener);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState){
        super.onSaveInstanceState(outState);
        if( currentBusStop != null ) {
            outState.putInt(BUS_STOP_ID_KEY, currentBusStop.getId());
        }
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
                        busStopMarkerClicked( stop );
                    }
                }catch(ClassCastException e){
                    e.printStackTrace();
                }

                return false;
            }
        });
    }

    /**
     * handle bus stop marker clicked event
     *
     * @param stop
     */
    private void busStopMarkerClicked(BusStop stop){
        busStopTextView.setText(stop.getName());
        Toast.makeText(MainActivity.this, stop.getName(), Toast.LENGTH_SHORT).show();
        try {
            //get bus stop schedule if the route data has not been set
            //otherwise get bus locations for stop
            if( currentBusStop != null ){
                currentBusStop.getMapMarker().setIcon(busStopIcon);
            }
            currentBusStop = stop;
            currentBusStop.getMapMarker().setIcon(busStopSelectedIcon);
            if (currentBusStop.getRoutes() == null) {
                septaApi.getBusSchedule(stop.getId());
            }else{
                septaApi.getBusLocations( currentBusStop.getRoutes().get(0) );
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //get user location
                //getLastKnownLocation();
                gpsPermissionButton.setVisibility(View.GONE);
                updateLocation();
            }else {
                gpsPermissionButton.setVisibility(View.VISIBLE);
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
            if( currentBusStop != null ) {
                ArrayList<String> routes = currentBusStop.getRoutes();
                if (routes != null) {
                    septaApi.getBusLocations(routes.get(0));
                }
            }
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

        if( savedBusStopId > 0 ){
            if( busStops.containsKey(savedBusStopId) ){
                BusStop stop = busStops.get(savedBusStopId);
                if( stop != null ) {
                    busStopMarkerClicked( stop );
                }
            }
        }

        Log.d("septa","bus stop locations complete.");
    }

    /**
     * handle success response for bus stop schedule response
     * mainly used to get routes serviced by bus stop
     *
     * sets BusLocationsTask on Timer schedule to run every 12 seconds
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

            busTimer.cancel();
            busTimer = new Timer();
            busLocationTask = new BusLocationTask();
            busTimer.scheduleAtFixedRate(busLocationTask,0,12000);

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

        List<String> trip_ids = new ArrayList<>();
        Map<Integer, Bus> newBuses = new HashMap<>();

        //add buses to new bus list
        if( currentBusStop != null ){
            for(int i = 0; i < data.length(); i++ ){
                try {
                    Bus newBus = new Bus(data.getJSONObject(i));
                    if( currentBusStop.isForDestination( newBus.getDestination() ) ){
                        newBuses.put(newBus.getId(), newBus);
                        trip_ids.add( String.valueOf(newBus.getTrip()) );

                        //update bus object or add new one
                        if( buses.containsKey(newBus.getId()) ){
                            Objects.requireNonNull(buses.get(newBus.getId())).update(data.getJSONObject(i));
                        }else{
                            buses.put( newBus.getId(), newBus );
                        }
                    }
                }catch(JSONException e){
                    e.printStackTrace();
                }
            }

            //remove buses that are not in new bus list
            Iterator<Integer> busIterator = buses.keySet().iterator();
            while( busIterator.hasNext() ){
                Integer key = busIterator.next();
                if( ! newBuses.containsKey(key) ){
                    Bus bus = buses.get(key);
                    if( bus != null ){
                        bus.removeMarker();
                    }
                    busIterator.remove();
                }
            }
            newBuses.clear();

            //get bus arrival times
            try {
                septaApi.getBusStopTimes(currentBusStop.getId(), TextUtils.join(",", trip_ids));
            }catch (UnsupportedEncodingException e){
                e.printStackTrace();
            }
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
        Iterator<Integer> busIterator = buses.keySet().iterator();
        while( busIterator.hasNext() ){
            Integer key = busIterator.next();
            Bus bus = buses.get(key);
            BusStopTime busStopTime = busStopTimes.get(bus.getTrip());
            if( busStopTime != null ){
                if( currentBusStop.getId() == busStopTime.getStopId() ){
                    bus.setArrivalTime( busStopTime.getArrivalTime() );
                    if( ! bus.isPastStop() && bus.getETA() <= etaThreshold ){
                        LatLng location = new LatLng( bus.getLat(), bus.getLon() );
                        Marker marker = bus.getMapMarker();

                        //add map marker or update existing one
                        if( marker != null ){
                            marker.setSnippet("ETA: " + bus.getETA() + "mins");
                            marker.setPosition(location);
                        }else{
                            marker = map.addMarker(new MarkerOptions().position(location)
                                    .title("route:" + currentBusStop.getRoutes().get(0))
                                    .snippet("ETA: " + bus.getETA() + "mins")
                                    .icon(busIcon));
                            marker.setTag(bus);
                            bus.setMapMarker(marker);
                        }
                        marker.showInfoWindow();
                    }else{
                        bus.removeMarker();
                        busIterator.remove();
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
            if( bus.hasMarker() ) {
                builder.include(bus.getMapMarker().getPosition());
            }
        }
        for(BusStop stop : busStops.values()){
            if( stop.hasMarker() ) {
                builder.include(stop.getMapMarker().getPosition());
            }
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
            Toast.makeText(this, "SEPTA api error. Please select different stop.", Toast.LENGTH_LONG).show();
        }
    }
}
