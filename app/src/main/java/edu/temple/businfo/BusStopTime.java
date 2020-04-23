package edu.temple.businfo;

import org.json.JSONException;
import org.json.JSONObject;

public class BusStopTime {

    private int trip_id;
    private String arrival_time;
    private String departure_time;
    private int stop_id;
    private int stop_sequence;

    public BusStopTime(JSONObject args) throws JSONException {
        this.trip_id = args.getInt("trip_id");
        this.arrival_time = args.getString("destination");
        this.departure_time = args.getString("destination");
        this.stop_id = args.getInt("stop_id");
        this.stop_sequence = args.getInt("stop_sequence");
    }

    public int getTripId(){ return trip_id; }

    public String getArrivalTime(){ return arrival_time; }

    public int getStopId(){ return stop_id; }

}
