package org.tmreynolds.humanproject.models;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * Created by tmrdev on 9/15/16.
 */
public class Distance extends RealmObject {

    //@PrimaryKey
    //private int id;

    private double longitude;
    private double latitude;
    private Date lastUpdated;

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
