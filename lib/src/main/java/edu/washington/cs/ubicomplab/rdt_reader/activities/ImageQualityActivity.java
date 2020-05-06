package edu.washington.cs.ubicomplab.rdt_reader.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import edu.washington.cs.ubicomplab.rdt_reader.R;
import edu.washington.cs.ubicomplab.rdt_reader.core.RDTCaptureResult;
import edu.washington.cs.ubicomplab.rdt_reader.core.RDTInterpretationResult;
import edu.washington.cs.ubicomplab.rdt_reader.interfaces.ImageQualityViewListener;
import edu.washington.cs.ubicomplab.rdt_reader.utils.ImageUtil;
import edu.washington.cs.ubicomplab.rdt_reader.views.ImageQualityView;

import static edu.washington.cs.ubicomplab.rdt_reader.core.Constants.DEFAULT_RDT_NAME;

/**
 * The {@link android.app.Activity} for showing a real-time camera feed during image capture and
 * providing real-time feedback to the user
 * Note: In this example app, this activity is launched as an {@link Intent} from {@link MainActivity}
 * with the target RDT's name passed in the bundle to support multiple RDT designs simultaneously
 */
public class ImageQualityActivity extends Activity implements ImageQualityViewListener {
    protected ImageQualityView mImageQualityView;

    /**
     * {@link android.app.Activity} onCreate()
     * @param savedInstanceState: the bundle object in case this is launched from an intent
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Bundle b = getIntent().getExtras();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_quality);

        // Prepare ImageQualityView
        mImageQualityView = findViewById(R.id.imageQualityView);
        mImageQualityView.setImageQualityViewListener(this);

        // Extract the target RDT's name
        if (b != null && b.containsKey("rdt_name")) {
            String rdtName = b.getString("rdt_name");
            mImageQualityView.setRDTName(rdtName);
        } else {
            mImageQualityView.setRDTName(DEFAULT_RDT_NAME);
        }
    }

    /**
     * {@link android.app.Activity} onResume()
     */
    @Override
    public void onResume() {
        super.onResume();
        mImageQualityView.onResume();
    }

    /**
     * {@link android.app.Activity} onPause()
     */
    @Override
    public void onPause() {
        super.onPause();
        mImageQualityView.onPause();
    }

    /**
     * {@link android.app.Activity} onBackPressed()
     * Launches the MainActivity as an Intent if that's how this Activity
     * was created in the first place
     */
    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    /**
     * {@link ImageQualityViewListener} onRDTCameraReady()
     */
    @Override
    public void onRDTCameraReady() {
    }

    /**
     * {@link ImageQualityViewListener} onRDTDetected()
     * Launches the {@link ImageResultActivity} if the candidate video frame is high quality
     * @param rdtCaptureResult: the current {@link RDTCaptureResult}
     * @param rdtInterpretationResult: the current {@link RDTInterpretationResult}
     * @param timeTaken: the time it took for the RDT to be detected
     * @return whether the app should continue letting the user capture an image
     */
    @Override
    public ImageQualityView.RDTDetectedResult onRDTDetected(
            final RDTCaptureResult rdtCaptureResult,
            final RDTInterpretationResult rdtInterpretationResult,
            final long timeTaken) {
        // The RDT was detected, but the candidate video frame
        // did not pass all of the quality checks
        if (!rdtCaptureResult.allChecksPassed || rdtInterpretationResult == null)
            return ImageQualityView.RDTDetectedResult.CONTINUE;

        useCapturedImage(rdtCaptureResult, rdtInterpretationResult, timeTaken);

        return ImageQualityView.RDTDetectedResult.STOP;
    }

    public void useCapturedImage(RDTCaptureResult rdtCaptureResult, RDTInterpretationResult rdtInterpretationResult, long timeTaken) {
        final byte[] captureByteArray = ImageUtil.matToByteArray(rdtCaptureResult.resultMat);
        final byte[] windowByteArray = ImageUtil.matToByteArray(rdtInterpretationResult.resultMat);
        moveToResultActivity(captureByteArray, windowByteArray, rdtInterpretationResult, timeTaken);
    }

    private void moveToResultActivity(final byte[] captureByteArray, final byte[] windowByteArray, final RDTInterpretationResult rdtInterpretationResult, final long timeTaken) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(ImageQualityActivity.this, ImageResultActivity.class);
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
                i.putExtra("hasTooMuchBlood", rdtInterpretationResult.hasTooMuchBlood);
                startActivity(i);
            }
        });
    }

    public ImageQualityView getmImageQualityView() {
        return mImageQualityView;
    }
}
