package edu.temple.businfo;

import android.content.Context;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class SeptaInterface {

    public static final String BUS_STOP_LOCATIONS_RESPONSE = "bus_stops_response";
    public static final String BUS_LOCATIONS_RESPONSE = "bus_locations_response";
    public static final String BUS_STOP_SCHEDULE_RESPONSE = "bus_stop_schedule_response";
    public static final String BUS_STOP_TIMES_RESPONSE = "bus_stop_times_response";

    private static final String TAG = "SeptaInterface ===>>>";
    private static final String API_DOMAIN = "https://www3.septa.org/hackathon";
    static final String LOCATIONS_EXT = "/locations/get_locations.php";
    static final String ALL_BUS_LOCATIONS_EXT = "/TransitViewAll";
    static final String BUS_LOCATIONS_BY_ROUTE_EXT = "/TransitView";
    static final String BUS_SCHEDULES_EXT = "/BusSchedules/";

    private final String LOCATION_TYPE_BUS_STOP = "bus_stops";

    private final String BUS_STOP_RADIUS = ".25"; //quarter mile radius | approx. 400 meters

    private RequestQueue queue;
    private SeptaResponseListener callback;

    public SeptaInterface(Context context){
        this.queue = Volley.newRequestQueue( context );
        this.callback = (SeptaResponseListener) context;
    }


    /**
     * get bus stop locations from septa api
     *
     * @param lat
     * @param lon
     */
    public void getBusStops( Double lat, Double lon ) throws UnsupportedEncodingException {

        String url = API_DOMAIN + LOCATIONS_EXT + '?'
                + "lat=" + URLEncoder.encode( String.valueOf(lat), "UTF-8" )
                + "&lon=" + URLEncoder.encode( String.valueOf(lon), "UTF-8" )
                + "&type=" + URLEncoder.encode( LOCATION_TYPE_BUS_STOP, "UTF-8" )
                + "&radius=" + URLEncoder.encode( BUS_STOP_RADIUS, "UTF-8" );

        this.makeVolleyRequest( url, BUS_STOP_LOCATIONS_RESPONSE, 0 );
    }

    /**
     * Request bus stop schedule from SEPTA api
     *
     * @param stop_id
     * @throws UnsupportedEncodingException
     */
    public void getBusSchedule( int stop_id ) throws UnsupportedEncodingException {

        String url = API_DOMAIN + BUS_SCHEDULES_EXT + '?'
                + "stop_id=" + URLEncoder.encode( String.valueOf(stop_id), "UTF-8" );

        this.makeVolleyRequest( url, BUS_STOP_SCHEDULE_RESPONSE, stop_id );
    }

    /**
     * request bus locations from SEPTA api
     *
     * @param route
     */
    public void getBusLocations( String route ) {
        String url = API_DOMAIN + BUS_LOCATIONS_BY_ROUTE_EXT + '/' + route;
        this.makeVolleyRequest( url, BUS_LOCATIONS_RESPONSE, 0 );
    }

    /**
     * get bus stop arrival times by trip number and bus stop
     * I couldn't find an api from SEPTA to get this info so I
     * downloaded the GTFS schedule data from SEPTA and created my own api for it
     *
     *
     * @param stop_id
     * @param trip_ids
     * @throws UnsupportedEncodingException
     */
    public void getBusStopTimes( int stop_id, String trip_ids ) throws UnsupportedEncodingException {
        String url = "https://findmeapp.tech/septa/bus-stop-times?"
                + "stop_id=" + URLEncoder.encode( String.valueOf(stop_id), "UTF-8" )
                + "&trip_ids=" + URLEncoder.encode( trip_ids, "UTF-8" );
        this.makeVolleyRequest( url, BUS_STOP_TIMES_RESPONSE, 0 );
    }

    /**
     * make post request to webserver api
     *
     * @param url
     */
    private void makeVolleyRequest( String url, final String type, final int stop_id ){

        StringRequest postRequest = new StringRequest(Request.Method.GET, url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    if( type.equals(BUS_STOP_LOCATIONS_RESPONSE) ) {
                        parseBusStopResponse(response);
                    }else if( type.equals(BUS_STOP_SCHEDULE_RESPONSE) ){
                        parseBusStopScheduleResponse(response, stop_id);
                    }else if( type.equals(BUS_LOCATIONS_RESPONSE) ){
                        parseBusLocationsResponse(response);
                    }else{
                        parseBusStopTimesResponse(response);
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    NetworkResponse response = error.networkResponse;
                    if ( error.getMessage() == null && error instanceof ServerError && response != null) {
                        callback.errorResponse("SEPTA API error.", type);
                    }else {
                        callback.errorResponse(error.getMessage(), type);
                    }
                }
            }
        );

        queue.add( postRequest );
    }

    /**
     * parses the response from the api
     * calls response on success
     * calls errorResponse on failure
     *
     * @param response
     */
    private void parseBusStopResponse(String response){
        System.out.println(TAG + response);
        try {
            JSONArray result = new JSONArray(response);
            callback.response( result, BUS_STOP_LOCATIONS_RESPONSE );

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void parseBusStopScheduleResponse(String response, int stop_id){
        System.out.println(TAG + response);
        try {
            JSONObject data = new JSONObject(response);
            JSONArray result = new JSONArray();
            result.put(stop_id);
            result.put( data );
            callback.response( result, BUS_STOP_SCHEDULE_RESPONSE );

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void parseBusLocationsResponse(String response){
        System.out.println(TAG + response);
        try {
            JSONObject result = new JSONObject(response);
            callback.response( result.getJSONArray("bus"), BUS_LOCATIONS_RESPONSE );

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void parseBusStopTimesResponse(String response){
        System.out.println(TAG + response);
        try {
            JSONObject result = new JSONObject(response);
            callback.response( result.getJSONArray("data"), BUS_STOP_TIMES_RESPONSE );

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Interface to get response from api
     * must be implemented by activity that uses the db interface
     */
    public interface SeptaResponseListener {
        //returns data on success
        void response( JSONArray data, String response_for );

        //returns error message on error
        void errorResponse( String error, String response_for );
    }

}
