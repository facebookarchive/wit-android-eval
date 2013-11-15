package ai.wit.eval;

import android.graphics.Bitmap;
import android.graphics.Color;
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

    public static void processIntent(PebbleQueue queue, String intent, HashMap<String, JsonObject> entities, final ImageView _imageView) {
        if (intent != null) {
            if (intent.equals("alarm")) {
                sendAlarmInformationToPebble(queue, entities);
            } else if (intent.equals("image")) {
                String image_keyword = "?";
                if (entities.get("topic") != null) {
                   image_keyword = entities.get("topic").get("value").getAsString();
                }
                SendTextToPebble(queue, image_keyword, "Loading...", "", "text");
                getImageAndSendToPebble(queue, image_keyword, _imageView);
            } else {
                SendTextToPebble(queue, "Intent :", intent, String.format("%s Entities", entities.size()), "text");
            }
        }
        else {
            SendTextToPebble(queue, "Wit didn't catch", "?", "", "text");
        }
    }

    private static void getImageAndSendToPebble(final PebbleQueue queue, final String image_keyword, final ImageView _imageView) {
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
                }
                else {
                    SendTextToPebble(queue, image_keyword, "No image found", "", "text");
                }
            }
        };
        request.execute(image_keyword);
    }

    private static void sendImageToPebble(Bitmap ditheredImage, PebbleQueue queue) {
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
            byte[] current = Arrays .copyOfRange(all, offset, offset + data_size);
            PebbleDictionary data = new PebbleDictionary();
            data.addString(MSG_KEY, "image");
            data.addInt32(IMAGE_OFFSET, offset);
            data.addBytes(IMAGE_KEY, current);
            Log.d("WIT", "Send Image to pebble app: " + data.toJsonString());
            queue.Enqueue(data);
        }
    }

    private static int getPixel(Bitmap img, int x, int y) {
        int pixel = img.getPixel(x, y);
        int R = Color.red(pixel);
        int G = Color.green(pixel);
        int B = Color.blue(pixel);
        return (int) (0.299 * R + 0.587 * G + 0.114 * B);
    }

    private static void setPixel(Bitmap map, int x, int y, int gray) {
        map.setPixel(x, y, Color.rgb(gray, gray, gray));
    }

    private static Bitmap dither(Bitmap img) {
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

    private static void sendAlarmInformationToPebble(PebbleQueue queue, HashMap<String, JsonObject> entities) {
        //Send to pebble
        String from = entities.get("datetime").getAsJsonObject("value").get("from").getAsString();
        try {
            Date dateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(from);
            String date = new SimpleDateFormat("MM/d/yy").format(dateTime);
            String time = new SimpleDateFormat("HH:mm a").format(dateTime);
            SendTextToPebble(queue, "Alarm set to", date, time, "text");
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public static void SendTextToPebble(PebbleQueue queue, String firstLine, String secondLine, String thirdLine, String msgType) {
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
}
