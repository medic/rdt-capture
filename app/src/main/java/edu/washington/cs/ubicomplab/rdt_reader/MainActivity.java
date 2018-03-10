package edu.washington.cs.ubicomplab.rdt_reader;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.*;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener {

    private Button mExpDateButton;
    private Button mImageQualityButton;
    private Button mSettingsyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle("RDT Image Capture");

        mExpDateButton = findViewById(R.id.expdateButton);
        mImageQualityButton = findViewById(R.id.imagequalButton);

        mExpDateButton.setOnTouchListener(this);
        mImageQualityButton.setOnTouchListener(this);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_CAMERA_REQUEST_CODE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

        if (view.getId() == R.id.expdateButton) {
            Intent intent = new Intent(this, ExpirationDateActivity.class);
            startActivity(intent);
        } else if (view.getId() == R.id.imagequalButton) {
            Intent intent = new Intent(this, ImageQualityActivity.class);
            startActivity(intent);
        }

        return false;
    }
}
