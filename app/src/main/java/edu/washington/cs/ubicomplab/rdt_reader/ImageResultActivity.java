package edu.washington.cs.ubicomplab.rdt_reader;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static java.text.DateFormat.getDateTimeInstance;

public class ImageResultActivity extends AppCompatActivity implements View.OnClickListener, SettingDialogFragment.SettingDialogListener{

    Bitmap mBitmapToSave;
    byte[] mByteArray;
    boolean isImageSaved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_result);
        initViews();
    }

    private void initViews() {
        if (getIntent().hasExtra("RDTCaptureByteArray")) {
            mByteArray = getIntent().getExtras().getByteArray("RDTCaptureByteArray");
            mBitmapToSave = BitmapFactory.decodeByteArray(mByteArray, 0, mByteArray.length);

            ImageView resultImageView = findViewById(R.id.RDTImageView);
            resultImageView.setImageBitmap(BitmapFactory.decodeByteArray(mByteArray, 0, mByteArray.length));
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

            String iconsStoragePath = Environment.getExternalStorageDirectory() + "/Pictures/" +"/RDTImageCaptures/";
            File sdIconStorageDir = new File(iconsStoragePath);

            //create storage directories, if they don't exist
            sdIconStorageDir.mkdirs();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

            try {
                String filePath = sdIconStorageDir.toString() + String.format("/%s.jpg", sdf.format(new Date()));
                FileOutputStream fileOutputStream = new FileOutputStream(filePath);

                fileOutputStream.write(mByteArray);

                fileOutputStream.flush();
                fileOutputStream.close();

                isImageSaved = true;

                Toast.makeText(this,"Image is successfully saved!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.w("TAG", "Error saving image file: " + e.getMessage());
            }
        } else if (view.getId() == R.id.sendButton) {
            Intent data = new Intent();
            data.putExtra("RDTCaptureByteArray", mByteArray);
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
