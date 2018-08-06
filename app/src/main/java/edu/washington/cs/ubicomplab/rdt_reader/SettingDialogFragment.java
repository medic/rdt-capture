package edu.washington.cs.ubicomplab.rdt_reader;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;

import java.util.Locale;

/**
 * Created by cjparkuw on 3/14/2018.
 */

public class SettingDialogFragment extends DialogFragment implements RadioGroup.OnCheckedChangeListener {

    SeekBar mSharpnessBar;
    SeekBar mOverExpBar;
    SeekBar mUnderExpBar;
    SeekBar mShadowBar;
    SeekBar mSizeBar;
    SeekBar mPositionBar;
    RadioButton mEnRadioButton;
    RadioButton mFrRadioButton;
    RadioButton mBmRadioButton;
    RadioGroup mLangGroup;

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int i) {
        if (i == R.id.enButton) {
            Constants.LANGUAGE = "en";
        } else if (i == R.id.frButton){
            Constants.LANGUAGE = "fr";
        } else  if (i == R.id.bmButton) {
            Constants.LANGUAGE = "bm";
        }
    }

    public interface SettingDialogListener {
        void onClickPositiveButton();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        View dialogView = inflater.inflate(R.layout.dialog_setting, null);

        mSharpnessBar = dialogView.findViewById(R.id.sharpnessBar);
        mOverExpBar = dialogView.findViewById(R.id.overExpBar);
        mUnderExpBar = dialogView.findViewById(R.id.underExpBar);
        mShadowBar = dialogView.findViewById(R.id.shadowBar);
        mSizeBar = dialogView.findViewById(R.id.sizeBar);
        mPositionBar = dialogView.findViewById(R.id.positionBar);
        mEnRadioButton = dialogView.findViewById(R.id.enButton);
        mFrRadioButton = dialogView.findViewById(R.id.frButton);
        mBmRadioButton = dialogView.findViewById(R.id.bmButton);
        mLangGroup = dialogView.findViewById(R.id.langGroup);

        mSharpnessBar.setMax(100);
        mSharpnessBar.setProgress((int)(Constants.BLUR_THRESHOLD*100));

        mOverExpBar.setMax(255);
        mOverExpBar.setProgress((int)(Constants.OVER_EXP_THRESHOLD));

        mUnderExpBar.setMax(255);
        mUnderExpBar.setProgress((int)(Constants.UNDER_EXP_THRESHOLD));

        mSizeBar.setMax(20);
        mSizeBar.setProgress((int)(1/Constants.SIZE_THRESHOLD));

        mPositionBar.setMax(20);
        mPositionBar.setProgress((int)(1/Constants.POSITION_THRESHOLD));

        mLangGroup.setOnCheckedChangeListener(this);

        if (Constants.LANGUAGE.equals("fr")) {
            mFrRadioButton.setChecked(true);
        } else if (Constants.LANGUAGE.equals("en")) {
            mEnRadioButton.setChecked(true);
        } else if (Constants.LANGUAGE.equals("bm")) {
            mBmRadioButton.setChecked(true);
        }

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setTitle(getString(R.string.settings))
                .setView(dialogView)
                // Add action buttons
                .setPositiveButton(getString(R.string.done), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        UpdateConstants();
                        SettingDialogListener activity = (SettingDialogListener) getActivity();
                        activity.onClickPositiveButton();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        return builder.create();
    }

    private void UpdateConstants() {
        Constants.BLUR_THRESHOLD = (double)mSharpnessBar.getProgress()/100.0;
        Constants.OVER_EXP_THRESHOLD =  mOverExpBar.getProgress();
        Constants.UNDER_EXP_THRESHOLD = mUnderExpBar.getProgress();
        //Constants.SHADOW mShadowBar.getProgress();
        Constants.SIZE_THRESHOLD = 1.0/(double)mSizeBar.getProgress();
        Constants.POSITION_THRESHOLD = 1.0/(double)mPositionBar.getProgress();

        Context context = getActivity();
        SharedPreferences sharedPref = context.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(getString(R.string.preference_language), Constants.LANGUAGE);
        editor.putFloat(getString(R.string.preference_over_exposure), (float)Constants.OVER_EXP_THRESHOLD);
        editor.putFloat(getString(R.string.preference_under_exposure), (float)Constants.UNDER_EXP_THRESHOLD);
        editor.putFloat(getString(R.string.preference_sharpness), (float)Constants.BLUR_THRESHOLD);
        editor.putFloat(getString(R.string.preference_position), (float)Constants.POSITION_THRESHOLD);
        editor.putFloat(getString(R.string.preference_size), (float)Constants.SIZE_THRESHOLD);
        editor.apply();

        Log.d(Constants.TAG, String.format("UODATED SETTINGS: BLUR: %.2f, SIZE: %.2f",  Constants.BLUR_THRESHOLD, Constants.SIZE_THRESHOLD));
    }
}
