package edu.washington.cs.ubicomplab.rdt_reader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.DEFAULT_RDT_NAME;

public class ImageQualityActivity extends Activity implements ImageQualityView.ImageQualityViewListener {
    ImageQualityView mImageQualityView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Bundle b = getIntent().getExtras();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_quality);
        mImageQualityView = findViewById(R.id.imageQualityView);
        mImageQualityView.setImageQualityViewListener(this);
        if (b != null && b.containsKey("rdt_name")) {
            String rdtName = b.getString("rdt_name");
            mImageQualityView.setRDTName(rdtName);
        } else {
            mImageQualityView.setRDTName(DEFAULT_RDT_NAME);
        }
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

        final byte[] captureByteArray = ImageUtil.matToRotatedByteArray(captureResult.resultMat);
        final byte[] windowByteArray = ImageUtil.matToRotatedByteArray(interpretationResult.resultMat);

        useCapturedImage(captureByteArray, windowByteArray, interpretationResult, timeTaken);

        return ImageQualityView.RDTDectedResult.STOP;
    }

    public void useCapturedImage(byte[] captureByteArray, byte[] windowByteArray, ImageProcessor.InterpretationResult interpretationResult, long timeTaken) {
        moveToResultActivity(captureByteArray, windowByteArray, interpretationResult, timeTaken);
    }

    private void moveToResultActivity(final byte[] captureByteArray, final byte[] windowByteArray, final ImageProcessor.InterpretationResult interpretationResult, final long timeTaken) {
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
                    Intent i = new Intent(ImageQualityActivity.this, ImageResultActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                    i.putExtra("captured", captureByteArray);
                    i.putExtra("window", windowByteArray);
                    i.putExtra("topLine", interpretationResult.topLine);
                    i.putExtra("middleLine", interpretationResult.middleLine);
                    i.putExtra("bottomLine", interpretationResult.bottomLine);
                    i.putExtra("topLineName", interpretationResult.topLineName);
                    i.putExtra("middleLineName", interpretationResult.middleLineName);
                    i.putExtra("bottomLineName", interpretationResult.bottomLineName);
                    i.putExtra("timeTaken", timeTaken);
                    startActivity(i);
                }
            }
        });
    }
}
