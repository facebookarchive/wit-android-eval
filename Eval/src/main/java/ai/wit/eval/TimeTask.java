package ai.wit.eval;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by oliv on 11/6/13.
 */
public class TimeTask extends AsyncTask<String, String, String> {
    @Override
    protected String doInBackground(String... params) {
        try {
            Log.d("Time", "Requesting ...." + params[0]);
            //First search for the image
            final String location = params[0];
            final String _urlTime;
            URL url = new URL(
                    "http://maps.googleapis.com/maps/api/geocode/json?sensor=false&address="+ URLEncoder.encode(location, "utf-8"));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.addRequestProperty("Referer", "www.google.com");
                String line;
                StringBuilder builder = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                Log.d("Time", "Received Location: " + builder.toString());
                JSONObject json = new JSONObject(builder.toString());
                JSONObject loca = json.getJSONArray("results").getJSONObject(0).getJSONObject("geometry").getJSONObject("location");
                double lat = loca.getDouble("lat");
                double lng = loca.getDouble("lng");
                _urlTime = String.format("https://maps.googleapis.com/maps/api/timezone/json?location=%s,%s&timestamp=1331161200&sensor=false", lat, lng);
            } finally {
                connection.disconnect();
            }

            url = new URL(_urlTime);
            connection = (HttpURLConnection) url.openConnection();
            try {
                connection.addRequestProperty("Referer", "www.google.com");
                String line;
                StringBuilder builder = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                Log.d("Time", "Received Timezone: " + builder.toString());
                JSONObject json = new JSONObject(builder.toString());
                String timezone = json.getString("timeZoneId");
                DateFormat format = new SimpleDateFormat("H:mm a");
                format.setTimeZone(TimeZone.getTimeZone(timezone));
                return format.format(new Date());

            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            Log.e("Time", "An error occurred during the time check", e);
        }
        return new SimpleDateFormat("H:mma").format(new Date());
    }
}
