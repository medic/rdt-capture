/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;

import edu.washington.cs.ubicomplab.rdt_reader.R;
import edu.washington.cs.ubicomplab.rdt_reader.interfaces.SettingsDialogListener;
import edu.washington.cs.ubicomplab.rdt_reader.core.Constants;

/**
 * Fragment view for allowing the end-user to modify the quality check thresholds
 */
public class SettingsDialogFragment extends DialogFragment
        implements RadioGroup.OnCheckedChangeListener {

    // Threshold setting UI elements
    private SeekBar mSharpnessBar;
    private SeekBar mUnderExpBar;
    private SeekBar mOverExpBar;
    private SeekBar mPositionBar;
    private SeekBar mSizeBar;

    // Language setting UI elements
    RadioButton mEnRadioButton;
    RadioButton mFrRadioButton;
    RadioButton mBmRadioButton;
    RadioGroup mLangGroup;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_setting, null);

        // Assign all of the UI elements
        mSharpnessBar = dialogView.findViewById(R.id.sharpnessBar);
        mOverExpBar = dialogView.findViewById(R.id.overExpBar);
        mUnderExpBar = dialogView.findViewById(R.id.underExpBar);
        mPositionBar = dialogView.findViewById(R.id.positionBar);
        mSizeBar = dialogView.findViewById(R.id.sizeBar);

        mEnRadioButton = dialogView.findViewById(R.id.enButton);
        mFrRadioButton = dialogView.findViewById(R.id.frButton);
        mBmRadioButton = dialogView.findViewById(R.id.bmButton);
        mLangGroup = dialogView.findViewById(R.id.langGroup);

        // Set the initial values for the UI elements
        mSharpnessBar.setMax(100);
        mSharpnessBar.setProgress((int) (Constants.SHARPNESS_THRESHOLD*100));
        mUnderExpBar.setMax(255);
        mUnderExpBar.setProgress((int) (Constants.UNDER_EXPOSURE_THRESHOLD));
        mOverExpBar.setMax(300);
        mOverExpBar.setProgress(mOverExpBar.getMax() - (int) (Constants.OVER_EXPOSURE_WHITE_COUNT));
        mPositionBar.setMax(20);
        mPositionBar.setProgress((int) (1/Constants.POSITION_THRESHOLD));
        mSizeBar.setMax(20);
        mSizeBar.setProgress((int) (1/Constants.SIZE_THRESHOLD));

        mLangGroup.setOnCheckedChangeListener(this);
        if (Constants.LANGUAGE.equals("fr"))
            mFrRadioButton.setChecked(true);
        else if (Constants.LANGUAGE.equals("en"))
            mEnRadioButton.setChecked(true);
        else if (Constants.LANGUAGE.equals("bm"))
            mBmRadioButton.setChecked(true);

        // Inflate and set the layout for the dialog
        builder.setTitle(getString(R.string.settings))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.done), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        updateConstants();
                        SettingsDialogListener activity = (SettingsDialogListener) getActivity();
                        activity.onClickPositiveButton();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        return builder.create();
    }

    /**
     * Update the language settings based on radio button selection
     * @param radioGroup: the radio group
     * @param i: the ID of the element that was selected
     */
    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int i) {
        switch (i) {
            case R.id.enButton:
                Constants.LANGUAGE = "en";
                break;
            case R.id.frButton:
                Constants.LANGUAGE = "fr";
                break;
            case R.id.bmButton:
                Constants.LANGUAGE = "bm";
                break;
            default:
                Constants.LANGUAGE = "en";
                break;
        }
    }

    /**
     * Update the constants based on user input
     */
    private void updateConstants() {
        // Calculate the values based on seekbar position
        Constants.SHARPNESS_THRESHOLD = (double) mSharpnessBar.getProgress()/100.0;
        Constants.OVER_EXPOSURE_WHITE_COUNT =  mOverExpBar.getMax() - mOverExpBar.getProgress();
        Constants.UNDER_EXPOSURE_THRESHOLD = mUnderExpBar.getProgress();
        Constants.SIZE_THRESHOLD = 1.0/(double)mSizeBar.getProgress();
        Constants.POSITION_THRESHOLD = 1.0/(double)mPositionBar.getProgress();

        // Get the user's preferences
        Context context = getActivity();
        SharedPreferences sharedPref = context.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        // Update the preferences
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(getString(R.string.preference_language), Constants.LANGUAGE);
        editor.putFloat(getString(R.string.preference_under_exposure), (float) Constants.UNDER_EXPOSURE_THRESHOLD);
        editor.putFloat(getString(R.string.preference_over_exposure), (float) Constants.OVER_EXPOSURE_WHITE_COUNT);
        editor.putFloat(getString(R.string.preference_sharpness), (float) Constants.SHARPNESS_THRESHOLD);
        editor.putFloat(getString(R.string.preference_position), (float) Constants.POSITION_THRESHOLD);
        editor.putFloat(getString(R.string.preference_size), (float) Constants.SIZE_THRESHOLD);
        editor.apply();
    }
}
