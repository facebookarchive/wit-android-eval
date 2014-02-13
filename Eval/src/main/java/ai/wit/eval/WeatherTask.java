package ai.wit.eval;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by oliv on 11/6/13.
 */
public class WeatherTask extends AsyncTask<String, String, List<String>> {
    @Override
    protected List<String> doInBackground(String... params) {
        try {
            Log.d("Weather", "Requesting ...." + params[0]);
            //First search for the image
            final String location = params[0];
            final String _urlWeather;
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
                _urlWeather = String.format("https://api.forecast.io/forecast/33cce720635ff164ad9a9e12fcd9b3ba/%s,%s", lat, lng);
            } finally {
                connection.disconnect();
            }

            url = new URL(_urlWeather);
            connection = (HttpURLConnection) url.openConnection();
            try {
                connection.addRequestProperty("Referer", "https://developer.forecast.io/");
                String line;
                StringBuilder builder = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                Log.d("Weather", "Received weather: " + builder.toString());
                JSONObject json = new JSONObject(builder.toString());
                List<String> result = new ArrayList<String>();
                result.add(json.getJSONObject("daily").getString("summary"));
                result.add(json.getJSONObject("currently").getString("summary"));
                return  result;
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            Log.e("Time", "An error occurred during the time check", e);
        }
        return Arrays.asList("unknown weather at this location", "Unknown");
    }
}
