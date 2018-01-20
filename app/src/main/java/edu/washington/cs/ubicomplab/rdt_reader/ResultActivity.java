package edu.washington.cs.ubicomplab.rdt_reader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        /*if(getIntent().hasExtra("byteArray")) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(
                            getIntent().getByteArrayExtra("byteArray"),0, getIntent().getByteArrayExtra("byteArray").length);

            ImageView resultImageView = findViewById(R.id.resultImageView);

            resultImageView.setImageBitmap(bitmap);
        }*/

        if (getIntent().hasExtra("imageFilePath")) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(getIntent().getStringExtra("imageFilePath"));

                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                ImageView resultImageView = findViewById(R.id.resultImageView);
                resultImageView.setImageBitmap(bitmap);
            } catch (Exception e) {

            }

        }
    }
}
