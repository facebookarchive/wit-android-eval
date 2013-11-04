/*
    Copyright 2013 Wit Inc. All rights reserved.
 */
package ai.wit.eval;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.HashMap;

import ai.wit.sdk.IWitListener;
import ai.wit.sdk.Wit;
import ai.wit.wit.R;

public class MainActivity extends Activity implements IWitListener {

    private static final int RESULT_SETTINGS = 1;
    private TextView _txtText;
    private TextView _jsonView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _txtText = (TextView) findViewById(R.id.txtText);
        _jsonView = (TextView) findViewById(R.id.jsonView);
        _jsonView.setMovementMethod(new ScrollingMovementMethod());
    }

    private void setWitSetting(){
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        String access_token = sharedPrefs.getString("access_token", "No accessToken");
        String instance_id = sharedPrefs.getString("instance_id", "No instanceId");
        //Initialize Fragment
        Wit wit_fragment = (Wit) getFragmentManager().findFragmentByTag("wit_fragment");
        if (wit_fragment != null) {
            wit_fragment.setAccessToken(access_token);
            wit_fragment.setInstanceId(instance_id);
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
    }
}
