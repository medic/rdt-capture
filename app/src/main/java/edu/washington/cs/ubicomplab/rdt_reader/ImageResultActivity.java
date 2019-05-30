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

import static java.text.DateFormat.getDateTimeInstance;

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
            if (isImageSaved) {
                Toast.makeText(this,"Image is already saved.", Toast.LENGTH_LONG).show();
                return;
            }

            File sdIconStorageDir = new File(Constants.RDT_IMAGE_DIR);

            //create storage directories, if they don't exist
            sdIconStorageDir.mkdirs();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS");

            try {
                String filePath = sdIconStorageDir.toString() + String.format("/%s-%08dms.jpg", sdf.format(new Date()), timeTaken);
                FileOutputStream fileOutputStream = new FileOutputStream(filePath);

                fileOutputStream.write(capturedByteArray);

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
