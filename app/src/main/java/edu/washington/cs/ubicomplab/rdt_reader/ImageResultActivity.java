package edu.washington.cs.ubicomplab.rdt_reader;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static java.text.DateFormat.getDateTimeInstance;

public class ImageResultActivity extends AppCompatActivity implements View.OnClickListener{

    Bitmap mBitmapToSave;
    boolean isImageSaved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_result);

        if (getIntent().hasExtra("RDTCapturePath")) {
            Bitmap bitmap = BitmapFactory.decodeFile(getIntent().getStringExtra("RDTCapturePath"));
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            mBitmapToSave = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            ImageView resultImageView = findViewById(R.id.RDTImageView);
            resultImageView.setImageBitmap(mBitmapToSave);
        }

        Button saveImageButton = findViewById(R.id.saveButton);
        saveImageButton.setOnClickListener(this);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(this, ImageQualityActivity.class);
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
                String filePath = sdIconStorageDir.toString() + String.format("/%s.png", sdf.format(new Date()));
                FileOutputStream fileOutputStream = new FileOutputStream(filePath);

                BufferedOutputStream bos = new BufferedOutputStream(fileOutputStream);

                //choose another format if PNG doesn't suit you
                mBitmapToSave.compress(Bitmap.CompressFormat.PNG, 100, bos);

                bos.flush();
                bos.close();

                isImageSaved = true;

                Toast.makeText(this,"Image is successfully saved!", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Log.w("TAG", "Error saving image file: " + e.getMessage());
            }
        }
    }
}
