package edu.temple.businfo;

import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class BusStop {
    private int id;
    private String name;
    private double lat;
    private double lon;
    private Marker mapMarker;
    private ArrayList<String> routes;
    private String direction_desc;

    public BusStop(JSONObject args) throws JSONException {
        this.id = args.getInt("location_id");
        this.name = args.getString("location_name");
        this.lat = args.getDouble("location_lat");
        this.lon = args.getDouble("location_lon");
    }

    public void setMapMarker(Marker marker){
        this.mapMarker = marker;
    }

    public Boolean hasMarker(){
        return mapMarker != null;
    }

    public void setRoutes(ArrayList<String> routes){
        this.routes = routes;
    }

    public void setDirection(String direction_desc){
        this.direction_desc = direction_desc;
    }

    public int getId(){ return id; }

    public String getName(){ return name; }

    public double getLat(){ return lat; }

    public double getLon(){ return lon; }

    public Marker getMapMarker(){ return mapMarker; }

    public ArrayList<String> getRoutes(){ return routes; }

    public Boolean isForDestination( String destination ){
        if( direction_desc != null ){
            return direction_desc.equals(destination);
        }

        return false;
    }
}
