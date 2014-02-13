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
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

import ai.wit.sdk.IWitListener;
import ai.wit.sdk.Wit;
import ai.wit.wit.R;

public class MainActivity extends Activity implements IWitListener, TextToSpeech.OnInitListener {

    private static final int RESULT_SETTINGS = 1;
    private static final int REC_KEY = 0;
    private static final int MY_DATA_CHECK_CODE = 3;
    private PebbleKit.PebbleDataReceiver dataReceiver;
    private PebbleKit.PebbleAckReceiver ackReceiver;
    private PebbleKit.PebbleNackReceiver nackReceiver;
    private static final UUID WIT_ALARM_UUID = UUID.fromString("2342a32d-1941-4d7b-ba03-55f824d8bde1");
    private TextView _txtText;
    private TextView _jsonView;
    private ImageView _imageView;
    private PebbleQueue _pebbleQueue;
    private double _threshold_ok;
    //TTS object
    private TextToSpeech myTTS;
    private PebbleConnector _pebbleConnector;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _txtText = (TextView) findViewById(R.id.txtText);
        _jsonView = (TextView) findViewById(R.id.jsonView);
        _imageView = (ImageView) findViewById(R.id.imageView);
        _jsonView.setMovementMethod(new ScrollingMovementMethod());
        setWitSetting();
        //Link pebble
        linkPebble();
        _pebbleQueue = new PebbleQueue(getApplicationContext(), WIT_ALARM_UUID);
        //check for TTS data
        Intent checkTTSIntent = new Intent();
        checkTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkTTSIntent, MY_DATA_CHECK_CODE);
        _pebbleConnector = new PebbleConnector();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Unlink Pebble
        unlinkPebble();
    }

    private void setWitSetting() {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        String access_token = sharedPrefs.getString("access_token", "No accessToken");
        _threshold_ok = Float.parseFloat(sharedPrefs.getString("threshold", "0.5f"));
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
            case MY_DATA_CHECK_CODE:
                if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                    //the user has the necessary data - create the TTS
                    myTTS = new TextToSpeech(this, this);
                    _pebbleConnector.SetTTS(myTTS);
                }
                else {
                    //no data - install it now
                    Intent installTTSIntent = new Intent();
                    installTTSIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(installTTSIntent);
                }
                break;
        }
    }

    @Override
    public void witDidGraspIntent(String intent, HashMap<String, JsonObject> entities, String body, double confidence, Error error) {
        _txtText.setText(body);
        if (confidence > _threshold_ok) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonOutput = gson.toJson(entities);
            _jsonView.setText(Html.fromHtml("<span><b>Intent: " + intent + "<b></span><br/>") + jsonOutput + Html.fromHtml("<br/><span><b>Confidence: " + confidence + "<b></span>"));
            _pebbleConnector.processIntent(_pebbleQueue, intent.toLowerCase(), entities, _imageView);
        }
        else {
            _jsonView.setText("Wit didn't catch that\n\n???");
            _pebbleConnector.SendTextToPebble(_pebbleQueue, "didn't catch that..", "?", "", "text");
        }
    }


    protected void unlinkPebble() {
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

    protected void linkPebble() {
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
                            Log.d("Wit ", "Pebble Input: " + recKey);

                            Wit wit_fragment = (Wit) getFragmentManager().findFragmentByTag("wit_fragment");
                            if (wit_fragment != null) {
                                wit_fragment.triggerRec(true);
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
                _pebbleQueue.Ack(transactionId);
            }
        };

        PebbleKit.registerReceivedAckHandler(this, ackReceiver);


        nackReceiver = new PebbleKit.PebbleNackReceiver(WIT_ALARM_UUID) {
            @Override
            public void receiveNack(final Context context, final int transactionId) {
                Log.d("Pebble NACK ", String.valueOf(transactionId));
                _pebbleQueue.Ack(transactionId);
            }
        };

        PebbleKit.registerReceivedNackHandler(this, nackReceiver);
    }

    @Override
    public void onInit(int status) {
        //check for successful instantiation
        if (status == TextToSpeech.SUCCESS) {
            if(myTTS.isLanguageAvailable(Locale.US)==TextToSpeech.LANG_AVAILABLE)
                myTTS.setLanguage(Locale.US);
        }
        else if (status == TextToSpeech.ERROR) {
            Toast.makeText(this, "Sorry! Text To Speech failed...", Toast.LENGTH_LONG).show();
        }
    }

    public void speak(String speech) {
        myTTS.speak(speech, TextToSpeech.QUEUE_FLUSH, null);
    }
}
