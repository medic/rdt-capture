package edu.washington.cs.ubicomplab.rdt_reader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.opencv.core.Mat;

public class ImageQualityActivity extends Activity implements ImageQualityView.ImageQualityViewListener, Button.OnClickListener {
    ImageQualityView mImageQualityView;
    Button mFlashButton;
    private boolean mFlashOn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mFlashOn = true;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_quality);
        mImageQualityView = findViewById(R.id.imageQualityView);
        mImageQualityView.setImageQualityViewListener(this);
        mFlashButton = findViewById(R.id.flashButton);
        mFlashButton.setOnClickListener(this);
    }


    @Override
    public void onResume() {
        super.onResume();
        mImageQualityView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mImageQualityView.onPause();
    }

    @Override
    public void onBackPressed() {
        if (mImageQualityView.isExternalIntent()) {
            super.onBackPressed();
        } else {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onRDTCameraReady() {
    }

    @Override
    public ImageQualityView.RDTDectedResult onRDTDetected(
            final ImageProcessor.CaptureResult captureResult,
            final ImageProcessor.InterpretationResult interpretationResult,
            final long timeTaken) {
        Log.i("ImageQualityActivity", "Detected!");
        if (!captureResult.allChecksPassed || interpretationResult == null) {
            return ImageQualityView.RDTDectedResult.CONTINUE;
        }
        Log.i("ImageQualityActivity", "Detected and Passed!");
        final ImageQualityActivity self = this;

        final byte[] captureByteArray = ImageUtil.matToRotatedByteArray(captureResult.resultMat);
        final byte[] windowByteArray = ImageUtil.matToRotatedByteArray(interpretationResult.resultMat);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mImageQualityView.isExternalIntent()) {
                    Intent i = new Intent();

                    i.putExtra("data", captureByteArray);
                    i.putExtra("timeTaken", timeTaken);

                    setResult(Activity.RESULT_OK, i);
                    finish();
                } else {
                    Intent i = new Intent(self, ImageResultActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                    i.putExtra("captured", captureByteArray);
                    i.putExtra("window", windowByteArray);
                    i.putExtra("control", interpretationResult.control);
                    i.putExtra("testA", interpretationResult.testA);
                    i.putExtra("testB", interpretationResult.testB);
                    i.putExtra("timeTaken", timeTaken);
                    startActivity(i);
                }
            }
        });
        return ImageQualityView.RDTDectedResult.STOP;
    }

    @Override
    public void onClick(View v) {
        if (v == mFlashButton) {
            mFlashOn = !mFlashOn;
            mImageQualityView.setFlashEnabled(mFlashOn);
        }
    }
}
