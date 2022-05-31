/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader.activities;

import static edu.washington.cs.ubicomplab.rdt_reader.core.Constants.*;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import edu.washington.cs.ubicomplab.rdt_reader.R;
import edu.washington.cs.ubicomplab.rdt_reader.fragments.SettingsDialogFragment;
import edu.washington.cs.ubicomplab.rdt_reader.interfaces.SettingsDialogListener;
import edu.washington.cs.ubicomplab.rdt_reader.core.Constants;

/**
 * The main {@link android.app.Activity} from which other activities are launched, allowing users
 * to select the target RDT and modify quality thresholds (for debugging purposes only)
 */
public class MainActivity extends AppCompatActivity implements
        View.OnClickListener, SettingsDialogListener {
    /**
     * {@link android.app.Activity} onCreate()
     * @param savedInstanceState: the bundle object in case this is launched from an intent
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("RDT Image Capture");
        setContentView(R.layout.activity_main);

        // Create folders for saving the images on the device's SD card
        new File(Constants.RDT_IMAGE_DIR).mkdirs();
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.parse("file://" + Constants.RDT_IMAGE_DIR)));

        // Initialize UI elements
        initViews();

        // Loads image quality settings from user preferences
        loadUserPrefs();
    }

    /**
     * Initializes UI elements and checks permissions
     */
    private void initViews() {
        // Initialize buttons
        Button mImageQualityButton = findViewById(R.id.imageQualityButton);
        Button mSettingsyButton = findViewById(R.id.settingsButton);
        mImageQualityButton.setOnClickListener(this);
        mSettingsyButton.setOnClickListener(this);

        // Make a list of required permissions
        ArrayList<String> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Manifest.permission.CAMERA);
        requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        // Find out which permissions are missing
        ArrayList<String> missingPermissions = new ArrayList<>();
        for (String s: requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, s)
                    != PackageManager.PERMISSION_GRANTED)
                missingPermissions.add(s);
        }

        // Request missing permissions
        if (missingPermissions.size() > 0)
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[missingPermissions.size()]),
                    MY_PERMISSION_REQUEST_CODE);
    }

    /**
     * Loads the user's preferences
     */
    private void loadUserPrefs() {
        // Get the user's preferences
        Context context = getApplicationContext();
        SharedPreferences sharedPref = context.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        // Extract user's preferences and update the app's settings
        SharedPreferences.Editor editor = sharedPref.edit();
        String languagePref = getString(R.string.preference_language);
        if (sharedPref.contains(languagePref))
            Constants.LANGUAGE = sharedPref.getString(languagePref,
                    Constants.LANGUAGE);
        else
            editor.putString(languagePref,
                    Constants.LANGUAGE);

        String underExposurePref = getString(R.string.preference_under_exposure);
        if (sharedPref.contains(underExposurePref))
            Constants.UNDER_EXPOSURE_THRESHOLD = sharedPref.getFloat(underExposurePref,
                    (float) Constants.UNDER_EXPOSURE_THRESHOLD);
        else
            editor.putFloat(underExposurePref, (float) Constants.UNDER_EXPOSURE_THRESHOLD);

        String overExposurePref = getString(R.string.preference_over_exposure);
        if (sharedPref.contains(overExposurePref))
            Constants.OVER_EXPOSURE_WHITE_COUNT = sharedPref.getFloat(overExposurePref,
                    (float) Constants.OVER_EXPOSURE_WHITE_COUNT);
        else
            editor.putFloat(getString(R.string.preference_over_exposure),
                    (float) Constants.OVER_EXPOSURE_WHITE_COUNT);

        String sharpnessPref = getString(R.string.preference_sharpness);
        if (sharedPref.contains(sharpnessPref))
            Constants.SHARPNESS_THRESHOLD = sharedPref.getFloat(sharpnessPref,
                    (float) Constants.SHARPNESS_THRESHOLD);
        else
            editor.putFloat(sharpnessPref,
                    (float) Constants.SHARPNESS_THRESHOLD);

        String positionPref = getString(R.string.preference_position);
        if (sharedPref.contains(positionPref))
            Constants.POSITION_THRESHOLD = sharedPref.getFloat(positionPref,
                    (float) Constants.POSITION_THRESHOLD);
        else
            editor.putFloat(positionPref,
                    (float) Constants.POSITION_THRESHOLD);

        String sizePref = getString(R.string.preference_size);
        if (sharedPref.contains(sizePref))
            Constants.SIZE_THRESHOLD = sharedPref.getFloat(sizePref,
                    (float) Constants.SIZE_THRESHOLD);
        else
            editor.putFloat(sizePref, (float)Constants.SIZE_THRESHOLD);
        editor.apply();

        // Change the language locale
        Resources res = getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        android.content.res.Configuration conf = res.getConfiguration();
        conf.setLocale(new Locale(Constants.LANGUAGE));
        res.updateConfiguration(conf, dm);
        setContentView(R.layout.activity_main);
    }

    /**
     * {@link android.app.Activity} onCreateOptionsMenu()
     * @param menu: the Activity's menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    /**
     * {@link android.app.Activity} onOptionsItemSelected()
     * @param item: the item that was selected from the Activity's menu
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                // Go to the settings page
                SettingsDialogFragment dialog = new SettingsDialogFragment();
                dialog.show(getFragmentManager(), "Setting Dialog");
                return true;
            default:
                return false;
        }
    }

    /**
     * {@link android.app.Activity} onBackPressed()
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    /**
     * The listener for all of the Activity's buttons
     * @param view the button that was selected
     */
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.imageQualityButton) {
            // Launch an instance of the ImageQualityActivity and pass in the target RDT's name
            Spinner rdtName = (Spinner) findViewById(R.id.rdtname);
            Intent intent = new Intent(this, ImageQualityActivity.class);
            intent.putExtra("rdt_name", rdtName.getSelectedItem().toString());
            Log.d(TAG, "RDT Name: " +  rdtName.getSelectedItem().toString());
            startActivity(intent);
        } else if (view.getId() == R.id.settingsButton) {
            // Launch the SettingDialogFragment
            SettingsDialogFragment dialog = new SettingsDialogFragment();
            dialog.show(getFragmentManager(), "Setting Dialog");
        }
    }

    /**
     * {@link SettingsDialogFragment} onClickPositiveButton()
     */
    @Override
    public void onClickPositiveButton() {
        Resources res = getResources();

        // Change locale settings in the app
        DisplayMetrics dm = res.getDisplayMetrics();
        android.content.res.Configuration conf = res.getConfiguration();
        conf.setLocale(new Locale(Constants.LANGUAGE));
        res.updateConfiguration(conf, dm);

        setContentView(R.layout.activity_main);
        initViews();
    }
}
