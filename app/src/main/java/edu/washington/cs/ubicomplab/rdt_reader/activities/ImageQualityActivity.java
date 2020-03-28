package edu.washington.cs.ubicomplab.rdt_reader.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import edu.washington.cs.ubicomplab.rdt_reader.interfaces.ImageQualityViewListener;
import edu.washington.cs.ubicomplab.rdt_reader.views.ImageQualityView;
import edu.washington.cs.ubicomplab.rdt_reader.R;
import edu.washington.cs.ubicomplab.rdt_reader.core.RDTCaptureResult;
import edu.washington.cs.ubicomplab.rdt_reader.core.RDTInterpretationResult;
import edu.washington.cs.ubicomplab.rdt_reader.utils.ImageUtil;

import static edu.washington.cs.ubicomplab.rdt_reader.core.Constants.DEFAULT_RDT_NAME;

public class ImageQualityActivity extends Activity implements ImageQualityViewListener {
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

    @Override
    public void onRDTCameraReady() {
    }

    @Override
    public ImageQualityView.RDTDetectedResult onRDTDetected(
            final RDTCaptureResult rdtCaptureResult,
            final RDTInterpretationResult rdtInterpretationResult,
            final long timeTaken) {
        Log.i("ImageQualityActivity", "Detected!");
        if (!rdtCaptureResult.allChecksPassed || rdtInterpretationResult == null) {
            return ImageQualityView.RDTDetectedResult.CONTINUE;
        }
        Log.i("ImageQualityActivity", "Detected and Passed!");
        final ImageQualityActivity self = this;

        final byte[] captureByteArray = ImageUtil.matToRotatedByteArray(rdtCaptureResult.resultMat);
        final byte[] windowByteArray = ImageUtil.matToRotatedByteArray(rdtInterpretationResult.resultMat);
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
                    i.putExtra("topLine", rdtInterpretationResult.topLine);
                    i.putExtra("middleLine", rdtInterpretationResult.middleLine);
                    i.putExtra("bottomLine", rdtInterpretationResult.bottomLine);
                    i.putExtra("topLineName", rdtInterpretationResult.topLineName);
                    i.putExtra("middleLineName", rdtInterpretationResult.middleLineName);
                    i.putExtra("bottomLineName", rdtInterpretationResult.bottomLineName);
                    i.putExtra("timeTaken", timeTaken);
                    startActivity(i);
                }
            }
        });
        return ImageQualityView.RDTDetectedResult.STOP;
    }
}
