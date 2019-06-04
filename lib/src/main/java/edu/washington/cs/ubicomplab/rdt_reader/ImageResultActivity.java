/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import static edu.washington.cs.ubicomplab.rdt_reader.ImageUtil.saveImage;

public class ImageResultActivity extends AppCompatActivity implements View.OnClickListener, SettingDialogFragment.SettingDialogListener{

    Bitmap mBitmapToSave;
    byte[] capturedByteArray, windowByteArray;
    boolean isImageSaved = false;
    long timeTaken = 0;
    boolean control, testA, testB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_result);


        initViews();
    }

    private void initViews() {
        Intent i = getIntent();

        if (getIntent().hasExtra("captured")) {
            capturedByteArray = getIntent().getExtras().getByteArray("captured");
            mBitmapToSave = BitmapFactory.decodeByteArray(capturedByteArray, 0, capturedByteArray.length);

            ImageView resultImageView = findViewById(R.id.RDTImageView);
            resultImageView.setImageBitmap(BitmapFactory.decodeByteArray(capturedByteArray, 0, capturedByteArray.length));
        }

        if (getIntent().hasExtra("window")) {
            windowByteArray = getIntent().getExtras().getByteArray("window");
            mBitmapToSave = BitmapFactory.decodeByteArray(windowByteArray, 0, windowByteArray.length);

            ImageView windowImageView = findViewById(R.id.WindowImageView);
            windowImageView.setImageBitmap(BitmapFactory.decodeByteArray(windowByteArray, 0, windowByteArray.length));
        }

        if (getIntent().hasExtra("timeTaken")) {
            timeTaken = getIntent().getLongExtra("timeTaken", 0);
            TextView timeTextView = findViewById(R.id.TimeTextView);
            timeTextView.setText(String.format("%.2f seconds", timeTaken/1000.0));
        }

        if (getIntent().hasExtra("control")) {
            control = getIntent().getBooleanExtra("control", false);
            TextView controlTextView = findViewById(R.id.ControlTextView);
            controlTextView.setText(String.format("%s", control?"True":"False"));
        }

        if (getIntent().hasExtra("testA")) {
            testA = getIntent().getBooleanExtra("testA", false);
            TextView testATextView = findViewById(R.id.TestATextView);
            testATextView.setText(String.format("%s", testA?"True":"False"));
        }

        if (getIntent().hasExtra("testB")) {
            testB = getIntent().getBooleanExtra("testB", false);
            TextView testBTextView = findViewById(R.id.TestBTextView);
            testBTextView.setText(String.format("%s", testB?"True":"False"));
        }

        Button saveImageButton = findViewById(R.id.saveButton);
        saveImageButton.setOnClickListener(this);
        Button sendImageButton = findViewById(R.id.sendButton);
        sendImageButton.setOnClickListener(this);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.saveButton) {
            saveImage(getApplicationContext(), capturedByteArray, timeTaken, null);
            isImageSaved = true;
            if (isImageSaved) {
                Toast.makeText(this,"Image is already saved.", Toast.LENGTH_LONG).show();
                return;
            }
            isImageSaved = true;
        } else if (view.getId() == R.id.sendButton) {
            Intent data = new Intent();
            data.putExtra("RDTCaptureByteArray", capturedByteArray);
            setResult(RESULT_OK, data);
            finish();

            Toast.makeText(this,"Image is successfully sent!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onClickPositiveButton() {
        Resources res = getResources();
        // Change locale settings in the app.
        DisplayMetrics dm = res.getDisplayMetrics();
        android.content.res.Configuration conf = res.getConfiguration();
        conf.setLocale(new Locale(Constants.LANGUAGE)); // API 17+ only.
        // Use conf.locale = new Locale(...) if targeting lower versions
        res.updateConfiguration(conf, dm);

        setContentView(R.layout.activity_image_quality);
        initViews();
    }
}
