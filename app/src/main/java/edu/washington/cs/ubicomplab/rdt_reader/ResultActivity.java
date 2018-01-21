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

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.dnn.Importer;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class ResultActivity extends AppCompatActivity {
    private static final String TAG = "rdt-reader:ResultActiv";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);


        if (getIntent().hasExtra("imageFilePath")) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(getIntent().getStringExtra("imageFilePath"));
                bitmap = Bitmap.createScaledBitmap(bitmap, (int)(bitmap.getWidth()/2.626), (int)(bitmap.getHeight()/2.626),false);
                Log.d(TAG, String.format("Bitmap Size: (w,h) (%d,%d)",bitmap.getWidth(),bitmap.getHeight()));

                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap bitmapRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                ImageView resultImageView = findViewById(R.id.resultImageView);
                resultImageView.setImageBitmap(bitmapRotated);

                ImageView resultImageView1 = findViewById(R.id.resultImageView1);
                ImageView resultImageView2 = findViewById(R.id.resultImageView2);

                Bitmap resultBitmap1 = Bitmap.createBitmap(bitmap, 250,265,200,45);
                Bitmap resultBitmap2 = Bitmap.createBitmap(bitmap, 250,80,200,45);

                resultImageView1.setImageBitmap(resultBitmap1);
                resultImageView2.setImageBitmap(resultBitmap2);

                LineChart lc1 = findViewById(R.id.chart1);
                lc1.setData(calculateIntensity(resultBitmap1));
                lc1.invalidate();

                LineChart lc2 = findViewById(R.id.chart2);
                lc2.setData(calculateIntensity(resultBitmap2));
                lc2.invalidate();
            } catch (Exception e) {

            }

        }
    }

    private LineData calculateIntensity(Bitmap bitmap) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bitmap, mat);

        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);

        double[] intensity = new double[mat.cols()];

        for (int i = 0; i < mat.cols(); i++) {
            double sum = 0;
            for (int j = 0; j < mat.rows(); j++) {
                sum+=(255-mat.get(j,i)[0]);
            }
            intensity[i] = sum;
        }

        List<Entry> entries = new ArrayList<Entry>();

        for (int i = 0; i < mat.cols(); i++) {

            entries.add(new Entry((float)i, (float)intensity[i]));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Intensity");

        return new LineData(dataSet);
    }
}
