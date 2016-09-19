package org.tmreynolds.humanproject;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.tmreynolds.humanproject.models.Distance;
import org.tmreynolds.humanproject.utils.ActivitiesIntentService;
import org.tmreynolds.humanproject.utils.Constants;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;
import rx.Observable;
import rx.Single;
import rx.SingleSubscriber;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks,
        com.google.android.gms.location.LocationListener, ResultCallback<Status> {

    protected static final String TAG = "location-updates-sample";

    // The desired interval for location updates. Inexact. Updates may be more or less frequent.
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 20000;

    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    // Keys for storing activity state in the Bundle.
    protected final static String REQUESTING_LOCATION_UPDATES_KEY = "requesting-location-updates-key";
    protected final static String LOCATION_KEY = "location-key";
    protected final static String LAST_UPDATED_TIME_STRING_KEY = "last-updated-time-string-key";

    public static boolean isLoggedIn = false;
    protected GoogleApiClient mGoogleApiClient;
    protected LocationRequest mLocationRequest;
    protected Location mCurrentLocation;

    // for detecting user motion
    private ActivityDetectionBroadcastReceiver mBroadcastReceiver;

    //Tracks the status of the location updates request.
    protected Boolean mRequestingLocationUpdates;
    //Time when the location was updated represented as a String.
    protected String mLastUpdateTime;
    private static Date lastDBEntry;

    Realm myRealm;

    private TextView distanceResults;
    private Button distanceButtonResults;

    // NOTE: ****** dev testing remove *****
    private TextView mDetectedActivityTextView;

    private static float testDistance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);

        distanceResults = (TextView) findViewById(R.id.distance_results);
        // dev testing remove **********************************
        mDetectedActivityTextView = (TextView) findViewById(R.id.detected_activities_textview);

        mBroadcastReceiver = new ActivityDetectionBroadcastReceiver();

        distanceButtonResults = (Button) findViewById(R.id.distance_results_button);
        distanceButtonResults.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                distanceResults.setText("***************************");
                //RealmResults<Distance> results1 = myRealm.where(Distance.class).findAll();
                RealmResults<Distance> sorted = myRealm.where(Distance.class).findAllSorted("lastUpdated", false);
                String dbResults = "";
                for (Distance d : sorted) {
                    //Log.d("results1", d.getLastUpdated().toString() + " : " + d.getLongitude() + " : " + d.getLatitude());
                    //dbResults+=d.getLastUpdated().toString().substring(0,19) + " : " + d.getLongitude() + " : " + d.getLatitude() + "\n";

                    // test distance
                }
                //distanceResults.setText(dbResults);

                // quick test of distance
                int tD = (int) MainActivity.testDistance;
                String blah = new String(String.valueOf(MainActivity.testDistance));
                distanceResults.setText(blah);
                Log.i(TAG, "current distance: td-> " + blah + " : ");
            }
        });

        /*
         * Login Check
         */
        if (!isLoggedIn) {
            // Intent loginActivity = new Intent(this, LoginActivity.class);
            // startActivityForResult(loginActivity, 9999);
        }

        buildGoogleApiClient();

        // Update values using data stored in the Bundle.
        updateValuesFromBundle(savedInstanceState);

        // Kick off the process of building a GoogleApiClient and requesting the LocationServices
        // API.

        myRealm = Realm.getInstance(this);

        mRequestingLocationUpdates = true;
        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();

        } else {
            // sleep to allow location updates to provide, also testing issues with starting ActivityRecognition
            try {
                Thread.sleep(5000);
                if (mGoogleApiClient.isConnected()) {
                    startLocationUpdates();
                    // start ActivityRecognition, need to manage lifecycle start and stop also
                    requestActivityUpdates();
                    //Log.i(TAG, "longitude--" + mCurrentLocation.getLongitude());
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        myObservable.subscribe(mySubscriber);
    }

    Observable<String> myObservable = Observable.create(
            new Observable.OnSubscribe<String>() {
                @Override
                public void call(Subscriber<? super String> sub) {
                    sub.onNext("Hello, world!");
                    sub.onCompleted();
                }
            }
    );


    Subscriber<String> mySubscriber = new Subscriber<String>() {
        @Override
        public void onNext(String s) {
            //System.out.println(s);
            Log.i(TAG, "dump string -> " + s);
        }

        @Override
        public void onCompleted() { }

        @Override
        public void onError(Throwable e) { }
    };

    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        Log.i(TAG, "Building GoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(ActivityRecognition.API)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }

    // start methods and subclass for ActivityRecognition motion detection
    public String getDetectedActivity(int detectedActivityType) {
        Resources resources = this.getResources();
        switch(detectedActivityType) {
            case DetectedActivity.IN_VEHICLE:
                return resources.getString(R.string.in_vehicle);
            case DetectedActivity.ON_BICYCLE:
                return resources.getString(R.string.on_bicycle);
            case DetectedActivity.ON_FOOT:
                return resources.getString(R.string.on_foot);
            case DetectedActivity.RUNNING:
                return resources.getString(R.string.running);
            case DetectedActivity.WALKING:
                return resources.getString(R.string.walking);
            case DetectedActivity.STILL:
                return resources.getString(R.string.still);
            case DetectedActivity.TILTING:
                return resources.getString(R.string.tilting);
            case DetectedActivity.UNKNOWN:
                return resources.getString(R.string.unknown);
            default:
                return resources.getString(R.string.unidentifiable_activity, detectedActivityType);
        }
    }

    @Override
    public void onResult(@NonNull Status status) {
        if (status.isSuccess()) {
            Log.e(TAG, "Successfully added activity detection.");

        } else {
            Log.e(TAG, "Error: " + status.getStatusMessage());
        }
    }

    public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {

        public ActivityDetectionBroadcastReceiver() {
            Log.i(TAG, "current distance: activity detection broadcast constructor hit");
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<DetectedActivity> detectedActivities = intent.getParcelableArrayListExtra(Constants.STRING_EXTRA);
            String activityString = "";
            for(DetectedActivity activity: detectedActivities){
                activityString +=  "Activity: " + getDetectedActivity(activity.getType()) + ", Confidence: " + activity.getConfidence() + "%\n";
            }
            Log.i(TAG, "current distance: motion ->" + activityString);
            mDetectedActivityTextView.setText(activityString);
        }
    }

    public void requestActivityUpdates() {
        Log.i(TAG, "current distance: requestActivityUpdates top");
        if (!mGoogleApiClient.isConnected()) {
            Log.i(TAG, "current distance: requestActivityUpdates api not connected");
            Toast.makeText(this, "GoogleApiClient not yet connected", Toast.LENGTH_SHORT).show();
        } else {
            Log.i(TAG, "current distance: requestActivityUpdates api client connected");
            // 2nd param sets interval time in milisecons
            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient, Constants.DETECTION_INTERVAL_IN_MILLISECONDS,
                    getActivityDetectionPendingIntent()).setResultCallback(this);
        }
    }

    public void removeActivityUpdates() {
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient,
                getActivityDetectionPendingIntent()).setResultCallback(this);
    }

    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, ActivitiesIntentService.class);

        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // end methods and subclass for ActivityRecognition - motion

    /**
     * Updates fields based on data stored in the bundle.
     *
     * @param savedInstanceState The activity state saved in the Bundle.
     */
    private void updateValuesFromBundle(Bundle savedInstanceState) {
        Log.i(TAG, "Updating values from bundle");
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and make sure that
            // the Start Updates and Stop Updates buttons are correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        REQUESTING_LOCATION_UPDATES_KEY);

            }

            // Update the value of mCurrentLocation from the Bundle and update the UI to show the
            // correct latitude and longitude.
            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that mCurrentLocation
                // is not null.
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(LAST_UPDATED_TIME_STRING_KEY)) {
                mLastUpdateTime = savedInstanceState.getString(LAST_UPDATED_TIME_STRING_KEY);
            }

        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Within {@code onPause()}, we pause location updates, but leave the
        // connection to GoogleApiClient intact.  Here, we resume receiving
        // location updates if the user has requested them.

        if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
            startLocationUpdates();
            requestActivityUpdates();
        }

        // for ActivityRecognition motion
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.STRING_ACTION));

    }

    @Override
    protected void onPause() {
        // Activity Recognition
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onPause();
        // Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    protected void stopLocationUpdates() {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.

        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Connected to GoogleApiClient");
        requestActivityUpdates();

        // If the initial location was never previously requested, we use
        // FusedLocationApi.getLastLocation() to get it. If it was previously requested, we store
        // its value in the Bundle and check for it in onCreate(). We
        // do not request it again unless the user specifically requests location updates by pressing
        // the Start Updates button.
        //
        // Because we cache the value of the initial location in the Bundle, it means that if the
        // user launches the activity,
        // moves to a new location, and then changes the device orientation, the original location
        // is displayed as the activity is re-created.
        if (mCurrentLocation == null) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        }

        // If the user presses the Start Updates button before GoogleApiClient connects, we set
        // mRequestingLocationUpdates to true (see startUpdatesButtonHandler()). Here, we check
        // the value of mRequestingLocationUpdates and if it is true, we start location updates.
        if (mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        //mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    }


    private void getDistance(RealmResults<Distance> pastLocation, Location mCurrentLocation) {
        Location mediaLocation = new Location("assignment-location");
        // distance will be in meters and will need to converted to feet
        float [] dist = new float[1];
        float distanceBetweenInFeet;
        mediaLocation.distanceBetween(pastLocation.last().getLatitude(), pastLocation.last().getLongitude(),
                mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(), dist);
        distanceBetweenInFeet = (float) (dist[0] * 3.28084);
        MainActivity.testDistance = distanceBetweenInFeet;
        Log.i(TAG, "current distance: " + distanceBetweenInFeet);
    }

    /**
     * Callback that fires when the location changes.
     */
    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        // *** NOTE *** Below code will bomb if proper checks are not done to ensure
        // the database and entry exist
        RealmConfiguration config = myRealm.getConfiguration();
        RealmResults<Distance> sorted = null;
        if (new File(config.getPath()).exists()) {
            // exists
            myRealm.beginTransaction();
            sorted = myRealm.where(Distance.class).findAllSorted("lastUpdated", true);
            // db might exist but still need to check if last record exists
            //Log.i(TAG, "sort -> " + sorted.last().getLastUpdated());
            if(sorted != null && sorted.size() > 0) {
                getDistance(sorted, mCurrentLocation);
            }
            myRealm.commitTransaction();

        } else {
            Log.i(TAG, "****** realm db does not exist ****");
        }


        /*
         * NOTE: query must get current date results only
         */

        /*
         * Start by creating a basic distance mapper
         * First need to check if any records exist for user (need user field in db)
         *  - If last record exists, pull from realm and calculate distance from current location
         *  - Then record last distance in db field and save statically in MainActivity variable (as feet or some other value)
         *  - Keep iterating like this for each new location and totaling distance until 1000 feet are reached
         *   - could label entry that reached 1000 for setting milestones
         * - can also keep track of when user is idle by storing comparing distance and storing total time idle up to one hour
         */
        // Create an object
        myRealm.beginTransaction();
        Log.i(TAG, " loc->" + location.getLongitude() + " last updated -> " + mLastUpdateTime);
        Distance distance = myRealm.createObject(Distance.class);
        // Set its fields
        distance.setLastUpdated(new Date());
        distance.setLatitude(mCurrentLocation.getLatitude());
        distance.setLongitude(mCurrentLocation.getLongitude());
        myRealm.commitTransaction();

    }

    /**
     * Requests location updates from the FusedLocationApi.
     */
    protected void startLocationUpdates() {
        Log.i(TAG, "start location updates");
        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "message: _>" + requestCode);
    }

    /**
     * Stores activity data in the Bundle.
     */
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, mRequestingLocationUpdates);
        savedInstanceState.putParcelable(LOCATION_KEY, mCurrentLocation);
        savedInstanceState.putString(LAST_UPDATED_TIME_STRING_KEY, mLastUpdateTime);
        super.onSaveInstanceState(savedInstanceState);
    }
}
