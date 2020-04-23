package edu.temple.businfo;

import com.google.android.gms.maps.model.Marker;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;

public class Bus {
    private int id;
    private String destination;
    private double lat;
    private double lon;
    private Marker mapMarker;
    private int trip;
    private int late;
    private int ETA;

    private Calendar arrival_time;

    public Bus(JSONObject args) throws JSONException {
        this.id = args.getInt("VehicleID");
        this.destination = args.getString("destination");
        this.lat = args.getDouble("lat");
        this.lon = args.getDouble("lng");
        this.trip = args.getInt("trip");
        this.late = args.getInt("late");
    }

    public void setMapMarker(Marker marker){
        this.mapMarker = marker;
    }

    public void setArrivalTime(String time){
        String[] parts = time.split(":");
        arrival_time = Calendar.getInstance();
        arrival_time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
        arrival_time.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
        arrival_time.add(Calendar.MINUTE, late);
        setEta();
    }

    public Boolean isPastStop(){
        Calendar now = Calendar.getInstance();
        return now.after(arrival_time);
    }

    public void setEta(){
        Calendar now = Calendar.getInstance();
        long diff = arrival_time.getTimeInMillis() - now.getTimeInMillis();
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        ETA = (int) minutes;
        mapMarker.setSnippet("ETA: " + ETA);
    }

    public int getId(){ return id; }

    public String getDestination(){ return destination; }

    public double getLat(){ return lat; }

    public double getLon(){ return lon; }

    public int getTrip(){ return trip; }

    public int getLate(){ return late; }

    public int getETA(){ return ETA; }

    public Marker getMapMarker(){ return mapMarker; }

    public Boolean isForDestination( String destination ){
        if( destination != null ){
            return destination.equals(destination);
        }

        return false;
    }
}
