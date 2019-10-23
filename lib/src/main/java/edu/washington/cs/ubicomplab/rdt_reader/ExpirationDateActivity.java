/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader;

import static com.google.android.gms.vision.Frame.ROTATION_90;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.*;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.Menu;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.Line;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.StringTokenizer;

public class ExpirationDateActivity extends AppCompatActivity implements CvCameraViewListener2, SettingDialogFragment.SettingDialogListener {

    private RDTCameraView mOpenCvCameraView;
    private TextRecognizer mTextRecognizer;
    private TextView mExpDateResultView;
    private ImageButton mFlashButton;
    private boolean mFlashOn;

    private OCRTask ocrTask;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expiration_date);
        mFlashOn = false;
        initViews();
    }

    private void initViews() {
        setTitle("Expiration Date Checker");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mOpenCvCameraView = findViewById(R.id.exp_date_check_camera_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mTextRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();

        mExpDateResultView = findViewById(R.id.exp_date_result_view);

        mFlashButton = findViewById(R.id.btn_exp_flash_toggle);
        mFlashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFlashOn) {
                    mOpenCvCameraView.turnOffTheFlash();
                    mFlashOn = false;
                } else {
                    mOpenCvCameraView.turnOnTheFlash();
                    mFlashOn = true;
                }
                updateFlashIndicators(mFlashOn);
            }
        });
    }

    private void updateFlashIndicators(boolean isFlashOn) {
        int drawableId = isFlashOn ? R.drawable.ic_toggle_flash_off : R.drawable.ic_toggle_flash_on;
        Drawable drawable = ContextCompat.getDrawable(getApplicationContext(), drawableId);
        mFlashButton.setBackground(drawable);

        TextView tvFlashOnStatus = findViewById(R.id.exp_flash_on_status);
        int stringId = isFlashOn ? R.string.light_off : R.string.light_on;
        tvFlashOnStatus.setText(stringId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    /*Activity callbacks*/
    @Override
    protected void onPause() {
        super.onPause();
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    /*OpenCV JavaCameraView callbacks*/
    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        System.gc();

        if (ocrTask == null || ocrTask.getStatus() == AsyncTask.Status.FINISHED) {
            ocrTask = new OCRTask();
            ocrTask.execute(inputFrame.rgba().clone());
        }

        return inputFrame.rgba();
    }

    /*Private methods*/
    private Date readExpirationDate(Mat inputFrame) {
        Log.d(TAG, "FRAME SIZE: " + inputFrame.size().toString());
        Bitmap tempBitmap = Bitmap.createBitmap(inputFrame.cols() / 2, inputFrame.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(inputFrame.submat(new Rect(inputFrame.cols() / 2, 0, inputFrame.cols() / 2, inputFrame.rows())), tempBitmap);

        Frame frame = new Frame.Builder().setBitmap(tempBitmap).setRotation(ROTATION_90).build();
        SparseArray<TextBlock> items = mTextRecognizer.detect(frame);

        Date expDate = new Date(0);
        SimpleDateFormat df = new SimpleDateFormat("MMMyyyy");
        String startWord = "exp";

        for (int i = 0; i < items.size(); ++i) {
            TextBlock item = items.valueAt(i);
            for (Text currText : item.getComponents()) {
                String str = currText.getValue();
                Log.d(TAG, "DETECTED LINE: " + str);

                str = str.toLowerCase();
                str = str.replaceAll(" ", "");
                str = str.replace(",", ".");
                str = str.replaceAll("\\.","");
                Log.d(TAG, "CLEANED LINE: " + str);

                if (str.startsWith(startWord)) {
                    int indexOfStartWord = str.indexOf(startWord);
                    str = str.substring(indexOfStartWord+startWord.length());
                    try {
                        expDate = df.parse(str).after(expDate) ? df.parse(str) : expDate;
                    } catch (ParseException pe) {
                        Log.d(TAG, pe.getMessage());
                    }
                    Log.d(TAG, "PARSED DATE: " + expDate.toString());
                }
            }
        }

        return expDate;
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

        setContentView(R.layout.activity_expiration_date);
        initViews();
    }

    private class OCRTask extends AsyncTask<Mat, Integer, Date> {

        @Override
        protected Date doInBackground(Mat... mats) {
            Mat inputFrame = mats[0];

            Date expDate = readExpirationDate(inputFrame);

            inputFrame.release();

            return expDate;
        }

        protected void onPostExecute(final Date expDate) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Date now = new Date();
                    if (expDate.getTime() == new Date(0).getTime()) {
                        mExpDateResultView.setText(getResources().getText(R.string.exp_date_undetected));
                    } else {
                        onResult(expDate.toString(), now.before(expDate));
                    }
                }
            });
        }
    }

    protected void onResult(String Date, boolean isValid) {
        View viewPort = findViewById(R.id.exp_date_check_viewport);
        View cameraControlLayout = findViewById(R.id.exp_date_camera_controls);
        cameraControlLayout.setBackgroundColor(Color.parseColor("#00ff0000"));
        findViewById(R.id.light_toggle_layout).setVisibility(View.GONE);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mExpDateResultView.getLayoutParams();
        params.bottomMargin = 50;
        mExpDateResultView.setPadding(20, 0, 0, 20);
        mExpDateResultView.setGravity(Gravity.CENTER);
        mExpDateResultView.setLayoutParams(params);
        if (isValid) {
            mExpDateResultView.setText(getResources().getText(R.string.exp_date_valid));
            viewPort.setBackgroundColor(getResources().getColor(R.color.green_overlay));
        } else {
            mExpDateResultView.setText(getResources().getText(R.string.exp_date_expired));
            viewPort.setBackgroundColor(getResources().getColor(R.color.red_overlay));
        }
    }
}
