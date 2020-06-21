package com.example.contacttracing;

import androidx.appcompat.app.AppCompatActivity;

import android.content.AsyncQueryHandler;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RegisterActivity extends AppCompatActivity {
    private EditText usernameEditText;
    private EditText passwordEditText;
    public String loginEndpoint = "http://192.168.2.13:5000/auth/register";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

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

    /**
     * Onclick Function for Register Button
     */
    public void register(View view) {
        String[] requestArgs = new String[2];
        requestArgs[0] = usernameEditText.getText().toString();
        requestArgs[1] = passwordEditText.getText().toString();
        new registerRequest().execute(requestArgs);
    }

    /**
     * Onclick Method for goToLogin Button
     */
    public void goToLogin(View view) {
        Intent loginIntent = new Intent(this,LoginActivity.class);
        startActivity(loginIntent);
    }

    /**
     * Asynchronous Task to make a Register Request to the server
     */
    private class registerRequest extends AsyncTask<String,Void,String> {

        @Override
        protected String doInBackground(String... strings) {
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
                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    //Read the response from the Server
                    BufferedReader in = new BufferedReader(new InputStreamReader((con.getInputStream())));
                    String inputLine;

                    StringBuffer response = new StringBuffer();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    return response.toString();
                } else if(responseCode==HttpURLConnection.HTTP_CONFLICT){
                return "Username Already Exists";
                } else if(responseCode==HttpURLConnection.HTTP_INTERNAL_ERROR){
                    return "Error, Try Again Later";
                } else {
                    return "Cannot Connect to Server, Try Again Later";
                }
            } catch(Exception e) {
                return "Cannot Connect to Server, Try Again Later";
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if(s.equals("Username Already Exists")||s.equals("Error, Try Again Later")||s.equals("Cannot Connect to Server, Try Again Later"))
            {
                Toast.makeText(RegisterActivity.this, s , Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(RegisterActivity.this, "Successfully Registered New User", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(RegisterActivity.this,LoginActivity.class));
            }
        }
    }
}