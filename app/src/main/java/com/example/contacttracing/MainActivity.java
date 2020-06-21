package com.example.contacttracing;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.icu.text.Edits;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.JsonReader;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.auth0.android.jwt.JWT;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import io.jsonwebtoken.Jwts;

public class MainActivity extends AppCompatActivity {

    private TextView statusTextView;

    public int REQUEST_ENABLE_BT = 1;

    private static final String apiEndpoint = "<Server IP Address>/api/infecteds";

    private ContactIdDatabase contactDb;
    private Handler statusCheckHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btCheck();

        statusTextView = findViewById(R.id.status_textView);

        contactDb = ContactIdDatabase.getInstance(this);
        statusCheckHandler = new Handler();
    }


    @Override
    public void onStart() {
        super.onStart();
        if(!PreferenceData.getUserLoggedInStatus(getApplicationContext())) {
            startActivity(new Intent(this,LoginActivity.class));
        } else {
            //Check the Infection Status every time the Main Activity Starts
            new infectionStatusCheck().execute();

            Intent scannerIntent = new Intent(this,ContactScanService.class);
            Intent serverIntent = new Intent(this,GattServerService.class);

            startService(scannerIntent);
            startService(serverIntent);
        }
    }

    /*Check and Request for Required Bluetooth Permissions*/
    public void btCheck() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "The permission to get BLE location data is required", Toast.LENGTH_SHORT).show();

            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        
        if(requestCode == REQUEST_ENABLE_BT) {
            if(resultCode==RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), "Please turn on Bluetooth", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * OnClick function for the Log Out Button
     */
    public void logOut(View view) {
        PreferenceData.setUserLoggedInStatus(getApplicationContext(),false);
        Toast.makeText(this, "You have been Logged Out", Toast.LENGTH_SHORT).show();

        //Stop the GATT Server and the ContactScan Services
        Intent serverIntent = new Intent(this, GattServerService.class);
        Intent scanIntent = new Intent(this,ContactScanService.class);

        stopService(serverIntent);
        stopService(scanIntent);

        //Send User to the LoginActivity
        startActivity(new Intent(this,LoginActivity.class));
    }

    /**
     * Onclick Function for the 'Report as Infected' Button
     */
    public void reportBtnClick(View view) {
        new reportInfected().execute();
    }

    /**
     * Asynchronous Task to make a Request to the server to update the status of the user to 'Infected'
     * Called when the User reports self as infected
     */
    private class reportInfected extends AsyncTask<Void,Void,Boolean> {
        private Exception exception;

        private String authHeader = String.format("Bearer %s", PreferenceData.getUserJwt(getApplicationContext()));

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                URL obj = new URL(apiEndpoint);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();

                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("Authorization", authHeader);

                //Initiates the connection
                int responseCode = con.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));

                    StringBuffer response = new StringBuffer();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    if(response.equals("Request not Authenticated")||response.equals("Error")) {
                        return false;
                    }
                } else {
                    return false;
                }
            } catch (Exception e) {
                this.exception = e;
                return false;
            }
            return true;
        }

        protected void onPostExecute(Boolean httpReqSuccess) {
            if (!httpReqSuccess) {
                Toast.makeText(getApplicationContext(), "Unable to contact Server, Please try again Later", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Set status to \'Infected\'", Toast.LENGTH_SHORT).show();
            }
        }

    }


    /**
     * Asynchronous Task to check for Infection Status of the User
     * Makes a request to the DB for all Contact IDs recorded in the previous 14 days
     */
    private class infectionStatusCheck extends AsyncTask<Void,Void,ContactID[]> {
        @Override
        protected ContactID[] doInBackground(Void ...voids) {
            return contactDb.contactIDDao().getRecentContactIDs(System.currentTimeMillis());
        }

        @Override
        protected void onPostExecute(ContactID[] contactIDs) {
            super.onPostExecute(contactIDs);

            new getInfecteds().execute(contactIDs);
        }
    }

    /**"
     * Asynchronous Task to make a GET Request from the Server to obtain the list of IDs of Users who have declared themselves as Infected
     * Compares this list to the list of Contact IDs recorded in the previous 14 days, and displays an appropriate message
     */
    private class getInfecteds extends AsyncTask<ContactID,Void,String> {
        private String authHeader = String.format("Bearer %s", PreferenceData.getUserJwt(getApplicationContext()));

        @Override
        protected String doInBackground(ContactID... contactIDS) {
            try {
                URL obj = new URL(apiEndpoint);
                HttpURLConnection con = (HttpURLConnection)obj.openConnection();

                //Set the required headers
                con.setRequestMethod("GET");
                con.setRequestProperty("Authorization", authHeader);

                //Initiates the connection
                int responseCode = con.getResponseCode();

                if(responseCode==HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader((con.getInputStream())));
                    String inputLine;

                    //Read the response from the server
                    StringBuffer response = new StringBuffer();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    if(response.toString().equals("Request not Authenticated")||response.toString().equals("Error")) {
                        return "Failed";
                    } else {
                        try {
                            JSONArray listInfecteds = new JSONArray(response.toString());
                            for (int i = 0; i < listInfecteds.length(); i++) {
                                String currInfectedUuid = listInfecteds.getJSONObject(i).getString("_id");
                                for(ContactID contactID : contactIDS) {
                                    if(contactID.uuid.equals(currInfectedUuid)) {
                                        return "At Risk";
                                    }
                                }
                            }
                            return "Safe";
                        } catch (JSONException e) {
                            return "Failed";
                        }
                    }
                } else {
                    return "Failed";
                }
            } catch (Exception e) {
                return "Failed";
            }
        }
        
        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if(s.equals("Failed")) {
                Toast.makeText(getApplicationContext(), "Unable to Check for Contact Status. Please reload", Toast.LENGTH_SHORT).show();
            } else if(s.equals("Safe")) {
                statusTextView.setText("Safe, no contact with Infected Person");
            } else if(s.equals("At Risk")) {
                statusTextView.setText("WARNING: You have been in contact with an infected person in the last 14 days");
            }
        }
    }

}
