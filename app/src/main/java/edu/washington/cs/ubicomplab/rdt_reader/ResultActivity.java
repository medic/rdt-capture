package edu.washington.cs.ubicomplab.rdt_reader;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TabHost;

public class ResultActivity extends AppCompatActivity {
    private static final String TAG = "rdt-reader:ResultActiv";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);


        if (getIntent().hasExtra("imageFilePath")) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(getIntent().getStringExtra("imageFilePath"));
                bitmap = Bitmap.createScaledBitmap(bitmap, (int)(bitmap.getWidth()/2.65), (int)(bitmap.getHeight()/2.65),false);
                Log.d(TAG, String.format("Bitmap Size: (w,h) (%d,%d)",bitmap.getWidth(),bitmap.getHeight()));

                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap bitmapRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                ImageView resultImageView = findViewById(R.id.resultImageView);
                resultImageView.setImageBitmap(bitmapRotated);

                ImageView resultImageView1 = findViewById(R.id.resultImageView1);
                Bitmap resultBitmap1 = Bitmap.createBitmap(bitmap, 250,80,200,45);
                ImageView resultImageView2 = findViewById(R.id.resultImageView2);
                Bitmap resultBitmap2 = Bitmap.createBitmap(bitmap, 250,270,200,45);

                resultImageView1.setImageBitmap(resultBitmap1);
                resultImageView2.setImageBitmap(resultBitmap2);
            } catch (Exception e) {

            }

        }
    }
}
