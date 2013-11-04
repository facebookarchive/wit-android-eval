/*
    Copyright 2013 Wit Inc. All rights reserved.
 */
package ai.wit.eval;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.UUID;

import ai.wit.sdk.IWitListener;
import ai.wit.sdk.Wit;
import ai.wit.wit.R;

public class MainActivity extends Activity implements IWitListener {

    private static final int RESULT_SETTINGS = 1;
    private static final int REC_KEY = 0;
    private static final int ALARM_KEY = 0;
    private static final int TIMESTAMP_KEY = 1;
    private PebbleKit.PebbleDataReceiver dataReceiver;
    private PebbleKit.PebbleAckReceiver ackReceiver;
    private PebbleKit.PebbleNackReceiver nackReceiver;
    private static final UUID WIT_ALARM_UUID = UUID.fromString("ee7e8823-a43c-49a4-b677-7a6980e8c088");
    private TextView _txtText;
    private TextView _jsonView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _txtText = (TextView) findViewById(R.id.txtText);
        _jsonView = (TextView) findViewById(R.id.jsonView);
        _jsonView.setMovementMethod(new ScrollingMovementMethod());
        setWitSetting();
    }

    private void setWitSetting() {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        String access_token = sharedPrefs.getString("access_token", "No accessToken");
        //Initialize Fragment
        Wit wit_fragment = (Wit) getFragmentManager().findFragmentByTag("wit_fragment");
        if (wit_fragment != null) {
            wit_fragment.setAccessToken(access_token);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent i = new Intent(this, SettingsActivity.class);
                startActivityForResult(i, RESULT_SETTINGS);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RESULT_SETTINGS:
                setWitSetting();
                break;
        }
    }

    @Override
    public void witDidGraspIntent(String intent, HashMap<String, JsonObject> entities, String body, double confidence, Error error) {
        _txtText.setText(body);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonOutput = gson.toJson(entities);
        _jsonView.setText(Html.fromHtml("<span><b>Intent: " + intent + "<b></span><br/>") + jsonOutput + Html.fromHtml("<br/><span><b>Confidence: " + confidence + "<b></span>"));
        if (intent != null && intent.equals("alarm")) {
            //Send to pebble
            String from = entities.get("datetime").getAsJsonObject("value").get("from").getAsString();
            try {
                Date time = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(from);
                PebbleDictionary data = new PebbleDictionary();
                data.addString(ALARM_KEY, "set_alarm");
                int gmtOffset = TimeZone.getDefault().getRawOffset();
                String timeToSend = String.valueOf((time.getTime() + gmtOffset) / 1000);
                Log.d("Pebble time" , timeToSend);
                data.addString(TIMESTAMP_KEY, timeToSend);
                PebbleKit.sendDataToPebble(getApplicationContext(), WIT_ALARM_UUID, data);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        // Always deregister any Activity-scoped BroadcastReceivers when the Activity is paused
        if (dataReceiver != null) {
            unregisterReceiver(dataReceiver);
            dataReceiver = null;
        }

        if (ackReceiver != null) {
            unregisterReceiver(ackReceiver);
            ackReceiver = null;
        }

        if (nackReceiver != null) {
            unregisterReceiver(nackReceiver);
            nackReceiver = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // In order to interact with the UI thread from a broadcast receiver, we need to perform any updates through
        // an Android handler. For more information, see: http://developer.android.com/reference/android/os/Handler
        // .html
        final Handler handler = new Handler();

        // To receive data back from a watch-app, android
        // applications must register a "DataReceiver" to operate on the
        // dictionaries received from the watch.
        dataReceiver = new PebbleKit.PebbleDataReceiver(WIT_ALARM_UUID) {
            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        // All data received from the Pebble must be ACK'd, otherwise you'll hit time-outs in the
                        // watch-app which will cause the watch to feel "laggy" during periods of frequent
                        // communication.
                        PebbleKit.sendAckToPebble(context, transactionId);

                        if (!data.iterator().hasNext()) {
                            return;
                        }

                        final String recKey = data.getString(REC_KEY);
                        if (recKey != null) {
                            Log.d("Pebble Input: ", recKey);
                            Wit wit_fragment = (Wit) getFragmentManager().findFragmentByTag("wit_fragment");
                            if (wit_fragment != null) {
                                wit_fragment.triggerRec();
                            }
                        }
                    }
                });
            }
        };

        PebbleKit.registerReceivedDataHandler(this, dataReceiver);

        ackReceiver = new PebbleKit.PebbleAckReceiver(WIT_ALARM_UUID) {
            @Override
            public void receiveAck(final Context context, final int transactionId) {
                Log.d("Pebble ACK ", String.valueOf(transactionId));
            }
        };

        PebbleKit.registerReceivedAckHandler(this, ackReceiver);


        nackReceiver = new PebbleKit.PebbleNackReceiver(WIT_ALARM_UUID) {
            @Override
            public void receiveNack(final Context context, final int transactionId) {
                Log.d("Pebble NACK ", String.valueOf(transactionId));
            }
        };

        PebbleKit.registerReceivedNackHandler(this, nackReceiver);
    }

}
