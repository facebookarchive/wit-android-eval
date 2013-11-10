package ai.wit.eval;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Created by oliv on 11/6/13.
 */
public class DownloadImagesTask extends AsyncTask<String, String, Bitmap> {
    @Override
    protected Bitmap doInBackground(String... params) {
        Bitmap response = null;
        try {
            Log.d("DownloadImage", "Requesting ...." + params[0]);
            //First search for the image
            final String image_keyword = params[0];
            final String image_url;
            URL url = new URL(
                    "https://ajax.googleapis.com/ajax/services/search/images?v=1.0&imgsz=small&"
                            + "q=" + URLEncoder.encode(image_keyword, "utf-8"));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.addRequestProperty("Referer", "www.google.com");

                String line;
                StringBuilder builder = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                JSONObject json = new JSONObject(builder.toString());
                image_url = json.getJSONObject("responseData").getJSONArray("results").getJSONObject(0).getString("url");
            } finally {
                connection.disconnect();
            }

            url = new URL(image_url);
            connection = (HttpURLConnection) url.openConnection();
            try {
                final InputStream in = new BufferedInputStream(connection.getInputStream());
                response = BitmapFactory.decodeStream(in);
                in.close();
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            Log.e("DownloadImage", "An error occurred during the download of the image", e);
        }
        return response;
    }
}
