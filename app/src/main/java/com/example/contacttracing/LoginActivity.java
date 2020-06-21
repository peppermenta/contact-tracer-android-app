package com.example.contacttracing;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    private EditText usernameEditText;
    private EditText passwordEditText;
    public String loginEndpoint = "<Server IP Address>/auth/login";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameEditText = (EditText)findViewById(R.id.username_editText);
        passwordEditText = (EditText)findViewById(R.id.password_editText);
    }

    @Override
    public void onStart() {
        super.onStart();
        if(PreferenceData.getUserLoggedInStatus(getApplicationContext()))
        {
            startActivity(new Intent(this,MainActivity.class));
        }
    }

    public void login(View view) throws IOException {
        String username = usernameEditText.getText().toString();
        String password = passwordEditText.getText().toString();

        String[] loginReqArgs = {username,password};
        new loginRequest().execute(loginReqArgs);
    }

    public void goToRegistration(View view) {
        Intent registrationIntent = new Intent(this,RegisterActivity.class);
        startActivity(registrationIntent);
    }

    /**
     * Make a Login Request to the Server
     * Handled as an Asynchronous Task
     */
    private class loginRequest extends AsyncTask<String,Void,String> {
        private Exception exception;
        @Override
        protected String doInBackground(String...strings) {
            String username = strings[0];
            String password = strings[1];

            //Set the POST Body
            String jsonInputString = String.format("{\"username\":\"%s\",\"password\":\"%s\"}",username,password);

            try {

                URL obj = new URL(loginEndpoint);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();

                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type","application/json");

                //Write the POST Body to Output Stream
                con.setDoOutput(true);
                OutputStream os = con.getOutputStream();
                byte[] input = jsonInputString.getBytes("UTF-8");
                os.write(input,0,input.length);
                os.flush();
                os.close();

                //Initiates the connection
                int responseCode = con.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    //Read the response from the Server
                    BufferedReader in = new BufferedReader(new InputStreamReader((con.getInputStream())));
                    String inputLine;

                    StringBuffer response = new StringBuffer();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    return response.toString();
                } else {
                    return "Cannot Connect to Server, Try Again Later";
                }
            } catch(Exception e) {
                this.exception = e;
                return "Cannot Connect to Server, Try Again Later";
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            if(s.equals("Username does not Exist")||s.equals("Password is Invalid")||s.equals("Cannot Connect to Server, Try Again Later"))
            {
                Toast.makeText(LoginActivity.this, s , Toast.LENGTH_SHORT).show();
            } else {
                /*Set the Shared Preferences to Contain the User's JWT*/
                PreferenceData.setUserLoggedInStatus(getApplicationContext(),true);
                PreferenceData.setUserJwt(getApplicationContext(),s);
                startActivity(new Intent(LoginActivity.this,MainActivity.class));
            }
        }
    }
}

