package ai.wit.eval;

import android.os.AsyncTask;
import android.util.Log;

import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

/**
 * Created by oliv on 11/6/13.
 */
public class CaltrainTask extends AsyncTask<String, String, Integer> {

    private static String TOKEN_ID = "c023aea9-5310-4b01-8874-26aa735140d7";
    private static Integer NB_STOP_CODE = 70211; //Going north
    private static Integer SB_STOP_CODE = 70212; //Going south

    @Override
    protected Integer doInBackground(String... params) {
        int timeUntilNext = Integer.MAX_VALUE;
        try {
            Log.d("Caltrain", "Requesting caltrain ...." + params[0]);
            //First search for the image
            final String direction = params[0];
            URL url = new URL(
                    String.format("http://services.my511.org/Transit2.0/GetNextDeparturesByStopCode.aspx?token=%s&stopCode=%s",
                            TOKEN_ID,
                            direction.equals("SF") ? NB_STOP_CODE : SB_STOP_CODE));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                String line;
                StringBuilder builder = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                XPathFactory xpathFactory = XPathFactory.newInstance();
                XPath xpath = xpathFactory.newXPath();
                InputSource source = new InputSource(new StringReader(builder.toString()));
                NodeList departures = (NodeList) xpath.evaluate("//DepartureTime", source, XPathConstants.NODESET);
                for (int i = 0; i < departures.getLength(); i++) {
                    Integer in = Integer.valueOf(departures.item(i).getFirstChild().getNodeValue());
                    if (in < timeUntilNext) {
                        timeUntilNext = in;
                    }
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            Log.e("Caltrain", "An error occurred during the request of the caltrain", e);
        }
        return timeUntilNext;
    }
}
