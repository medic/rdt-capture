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
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import edu.washington.cs.ubicomplab.rdt_reader.utils.Constants;

import static java.text.DateFormat.getDateTimeInstance;

public class ImageResultActivity extends AppCompatActivity implements View.OnClickListener, SettingDialogFragment.SettingDialogListener{

    Bitmap mBitmapToSave;
    byte[] capturedByteArray, windowByteArray;
    boolean isImageSaved = false;
    long timeTaken = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_result);
        initViews();
    }

    private void initViews() {
        Intent intent = getIntent();

        // Captured image
        if (intent.hasExtra("captured")) {
            capturedByteArray = intent.getExtras().getByteArray("captured");
            mBitmapToSave = BitmapFactory.decodeByteArray(capturedByteArray, 0, capturedByteArray.length);

            ImageView resultImageView = findViewById(R.id.RDTImageView);
            resultImageView.setImageBitmap(BitmapFactory.decodeByteArray(capturedByteArray, 0, capturedByteArray.length));
        }

        // Enhanced image
        if (intent.hasExtra("window")) {
            windowByteArray = intent.getExtras().getByteArray("window");
            mBitmapToSave = BitmapFactory.decodeByteArray(windowByteArray, 0, windowByteArray.length);

            ImageView windowImageView = findViewById(R.id.WindowImageView);
            windowImageView.setImageBitmap(BitmapFactory.decodeByteArray(windowByteArray, 0, windowByteArray.length));
        }

        // Capture time
        if (intent.hasExtra("timeTaken")) {
            timeTaken = intent.getLongExtra("timeTaken", 0);
            TextView timeTextView = findViewById(R.id.TimeTextView);
            timeTextView.setText(String.format("%.2f seconds", timeTaken/1000.0));
        }

        // Top line
        if (intent.hasExtra("topLine")) {
            boolean topLine = intent.getBooleanExtra("topLine", false);
            TextView topLineTextView = findViewById(R.id.topLineTextView);
            topLineTextView.setText(String.format("%s", topLine ?"True":"False"));
        }
        if (intent.hasExtra("topLineName")) {
            String topLineName = intent.getStringExtra("topLineName");
            TextView topLineNameTextView = findViewById(R.id.topLineNameTextView);
            topLineNameTextView.setText(topLineName);
        }

        // Middle line
        if (intent.hasExtra("middleLine")) {
            boolean middleLine = intent.getBooleanExtra("middleLine", false);
            TextView middleLineTextView = findViewById(R.id.middleLineTextView);
            middleLineTextView.setText(String.format("%s", middleLine ?"True":"False"));
        }
        if (intent.hasExtra("middleLineName")) {
            String middleLineName = intent.getStringExtra("middleLineName");
            TextView middleLineNameTextView = findViewById(R.id.middleLineNameTextView);
            middleLineNameTextView.setText(middleLineName);
        }

        // Bottom line
        if (intent.hasExtra("bottomLine")) {
            boolean bottomLine = intent.getBooleanExtra("bottomLine", false);
            TextView bottomLineTextView = findViewById(R.id.bottomLineTextView);
            bottomLineTextView.setText(String.format("%s", bottomLine ?"True":"False"));
        }
        if (intent.hasExtra("bottomLineName")) {
            String bottomLineName = intent.getStringExtra("bottomLineName");
            TextView bottomLineNameTextView = findViewById(R.id.bottomLineNameTextView);
            bottomLineNameTextView.setText(bottomLineName);
        }

        // Buttons
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
            if (isImageSaved) {
                Toast.makeText(this,"Image is already saved.", Toast.LENGTH_LONG).show();
                return;
            }

            File sdIconStorageDir = new File(Constants.RDT_IMAGE_DIR);

            //create storage directories, if they don't exist
            sdIconStorageDir.mkdirs();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS");

            try {
                // Save the full image
                String filePath = sdIconStorageDir.toString() + String.format("/%s-%08dms_full.jpg", sdf.format(new Date()), timeTaken);
                FileOutputStream fileOutputStream = new FileOutputStream(filePath);
                fileOutputStream.write(capturedByteArray);
                fileOutputStream.flush();
                fileOutputStream.close();

                // Save the enhanced image
                filePath = sdIconStorageDir.toString() + String.format("/%s-%08dms_cropped.jpg", sdf.format(new Date()), timeTaken);
                fileOutputStream = new FileOutputStream(filePath);
                fileOutputStream.write(windowByteArray);
                fileOutputStream.flush();
                fileOutputStream.close();

                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + filePath)));
                isImageSaved = true;
                Toast.makeText(this,"Image is successfully saved!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.w("TAG", "Error saving image file: " + e.getMessage());
            }
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
