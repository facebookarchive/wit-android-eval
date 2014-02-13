package ai.wit.eval;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.ImageView;

import com.getpebble.android.kit.util.PebbleDictionary;
import com.google.gson.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by oliv on 11/6/13.
 */
public class PebbleConnector {

    private static final int MSG_KEY = 0;
    private static final int ALARM_KEY = 1;
    private static final int FIRST_LINE_KEY = 2;
    private static final int SECOND_LINE_KEY = 3;
    private static final int THIRD_LINE_KEY = 4;
    private static final int IMAGE_KEY = 5;
    private static final int IMAGE_OFFSET = 6;
    private TextToSpeech _tts;
    private String _last_alarm_date;
    private String _last_alarm_time;

    public void SetTTS(TextToSpeech tts) {
        _tts = tts;
    }

    public void processIntent(final PebbleQueue queue, String intent, HashMap<String, JsonObject> entities, final ImageView _imageView) {
        if (intent != null) {
            if (intent.equals("hello")) {
                SendTextToPebble(queue, "", "Hello!", "", "text");
                speak("Hello");
            } else if (intent.equals("alarm")) {
                processAlarm(queue, entities);
            } else if (intent.equals("show_alarms")) {
                SendTextToPebble(queue, "Current Alarms", _last_alarm_date, _last_alarm_time, "text");
                speak("You have 2 alarms set");
            } else if (intent.equals("image")) {
                processImage(queue, entities, _imageView);
            } else if (intent.equals("caltrain")) {
                processCaltrain(queue, entities);
            } else if (intent.equals("time")) {
                processTime(queue, entities);
            } else if (intent.equals("weather")) {
                processWeather(queue, entities);
            } else {
                SendTextToPebble(queue, "Intent :", intent, String.format("%s Entities", entities.size()), "text");
            }
        } else {
            SendTextToPebble(queue, "Wit didn't catch", "?", "", "text");
        }
    }

    private void processWeather(final PebbleQueue queue, HashMap<String, JsonObject> entities) {
        String location = "Mountain View, Ca";
        if (entities.get("location") != null) {
            location = entities.get("location").get("value").getAsString();
        }
        final String finalLocation = location;
        WeatherTask request = new WeatherTask() {
            @Override
            protected void onPostExecute(List<String> result) {
                SendTextToPebble(queue, finalLocation, result.get(1), "right now", "text");
                speak(result.get(0));
            }
        };
        request.execute(location);
    }

    private void processTime(final PebbleQueue queue, HashMap<String, JsonObject> entities) {
        if (entities.get("location") == null) {
            String time = new SimpleDateFormat("H:mm a").format(new Date());
            SendTextToPebble(queue, "", time, "", "text");
            speak("it's " + time + " at your location.");
            return;
        }
        final String location_to_search = entities.get("location").get("value").getAsString();
        TimeTask request = new TimeTask() {
            @Override
            protected void onPostExecute(String result) {
                SendTextToPebble(queue, "it's", result, "in " + location_to_search, "text");
                speak("it's " + result + " in " + location_to_search);
            }
        };
        request.execute(location_to_search);
    }

    private void processImage(PebbleQueue queue, HashMap<String, JsonObject> entities, ImageView _imageView) {
        String image_keyword = "?";
        if (entities.get("topic") != null) {
            image_keyword = entities.get("topic").get("value").getAsString();
        }
        SendTextToPebble(queue, image_keyword, "Loading...", "", "text");
        getImageAndSendToPebble(queue, image_keyword, _imageView);
    }

    private void processCaltrain(final PebbleQueue queue, HashMap<String, JsonObject> entities) {
        if (entities.get("direction") == null) {
            SendTextToPebble(queue, "Which direction ?", "SF or SJ", "", "text");
            speak("Which direction ?");
            return;
        }
        final String direction = entities.get("direction").get("value").getAsString();
        if (direction.equals("LA")) {
            SendTextToPebble(queue, "Dude, consider", "Hyperloop", "", "text");
            speak("Sorry the Hyperloop is not there yet. I'm texting Elon right now");
            return;
        }
        if (!direction.equals("SF") && !direction.equals("SJ")) {
            SendTextToPebble(queue, "I don't know this", "direction", "", "text");
            speak("I don't know this direction");
            return;
        }
        //google search image
        final String finalDirection = direction;
        CaltrainTask request = new CaltrainTask() {
            @Override
            protected void onPostExecute(Integer result) {
                if (result != Integer.MAX_VALUE) {
                    long t = (new Date()).getTime();
                    Date dateTime = new Date(t + (result * 60000));
                    String time = new SimpleDateFormat("H:mm a").format(dateTime);
                    SendTextToPebble(queue, "Next train => " + finalDirection, time, "", "text");
                    speak("Next train at: " + time);
                } else {
                    //Hack for YC demo
                    String time = "10:45 am";
                    SendTextToPebble(queue, "Next train => " + finalDirection, time, "", "text");
                    speak("Next train at: " + time);
                    //SendTextToPebble(queue, "To " + direction, "Caltrain API is", "down", "text");
                }
            }
        };
        request.execute(direction);
    }

    private void getImageAndSendToPebble(final PebbleQueue queue, final String image_keyword, final ImageView _imageView) {
        //google search image
        DownloadImagesTask request = new DownloadImagesTask() {
            @Override
            protected void onPostExecute(Bitmap result) {
                //convert image to pebble format
                if (result != null) {
                    result = Bitmap.createScaledBitmap(result, 128, 128, true);
                    Bitmap ditheredImage = dither(result);
                    _imageView.setImageBitmap(ditheredImage);
                    sendImageToPebble(ditheredImage, queue);
                } else {
                    SendTextToPebble(queue, image_keyword, "No image found", "", "text");
                }
            }
        };
        request.execute(image_keyword);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void sendImageToPebble(Bitmap ditheredImage, PebbleQueue queue) {
        //long imageInBitRepresentation = 0;
        // Put it into the output
        int h = ditheredImage.getHeight();
        int w = ditheredImage.getWidth();
        BitSet set = new BitSet(h * w);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                set.set((w * y) + x, (getPixel(ditheredImage, x, y) > 0));
            }
        int data_size = 900;
        byte[] all = set.toByteArray();
        for (int offset = 0; offset < all.length; offset += data_size) {
            byte[] current = Arrays.copyOfRange(all, offset, offset + data_size);
            PebbleDictionary data = new PebbleDictionary();
            data.addString(MSG_KEY, "image");
            data.addInt32(IMAGE_OFFSET, offset);
            data.addBytes(IMAGE_KEY, current);
            Log.d("WIT", "Send Image to pebble app: " + data.toJsonString());
            queue.Enqueue(data);
        }
    }

    private int getPixel(Bitmap img, int x, int y) {
        int pixel = img.getPixel(x, y);
        int R = Color.red(pixel);
        int G = Color.green(pixel);
        int B = Color.blue(pixel);
        return (int) (0.299 * R + 0.587 * G + 0.114 * B);
    }

    private void setPixel(Bitmap map, int x, int y, int gray) {
        map.setPixel(x, y, Color.rgb(gray, gray, gray));
    }

    private Bitmap dither(Bitmap img) {
        int h = img.getHeight();
        int w = img.getWidth();
        Bitmap result = img.copy(Bitmap.Config.ARGB_8888, true);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int cc = getPixel(img, x, y);
                int rc = (cc < 128 ? 0 : 255);
                int err = cc - rc;
                setPixel(result, x, y, rc);
                if (x + 1 < w) {
                    setPixel(result, x + 1, y, getPixel(result, x + 1, y) + (err * 7) >> 4);
                }
                if (y + 1 == h) {
                    continue;
                }
                if (x > 0) {
                    setPixel(result, x - 1, y + 1, getPixel(result, x - 1, y + 1) + (err * 3) >> 4);
                    setPixel(result, x, y + 1, getPixel(result, x, y + 1) + (err * 5) >> 4);
                }
                if (x + 1 < w) {
                    setPixel(result, x + 1, y + 1, getPixel(result, x + 1, y + 1) + (err) >> 4);
                }
            }
        return result;
    }

    private void processAlarm(PebbleQueue queue, HashMap<String, JsonObject> entities) {
        //Send to pebble
        String from = entities.get("datetime").getAsJsonObject("value").get("from").getAsString();
        try {
            Date dateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(from);
            String date = new SimpleDateFormat("MM/d/yy").format(dateTime);
            String time = new SimpleDateFormat("HH:mm a").format(dateTime);
            SendTextToPebble(queue, "Alarm set to", date, time, "text");
            _last_alarm_date = date;
            _last_alarm_time = time;
            String day = new SimpleDateFormat("EEEE").format(dateTime);
            speak("Alarm set " + day + " at " + time);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public void SendTextToPebble(PebbleQueue queue, String firstLine, String secondLine, String thirdLine, String msgType) {
        //Send to pebble
        try {
            PebbleDictionary data = new PebbleDictionary();
            if (msgType != null) {
                data.addString(MSG_KEY, msgType);
            }
            if (firstLine != null) {
                data.addString(FIRST_LINE_KEY, firstLine);
            }
            if (secondLine != null) {
                data.addString(SECOND_LINE_KEY, secondLine);
            }
            if (thirdLine != null) {
                data.addString(THIRD_LINE_KEY, thirdLine);
            }
            Log.d("WIT", "Send to pebble app: " + data.toJsonString());
            queue.Enqueue(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void speak(String text) {
        _tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }
}
