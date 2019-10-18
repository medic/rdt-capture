package edu.washington.cs.ubicomplab.rdt_reader.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import edu.washington.cs.ubicomplab.rdt_reader.ImageProcessor;
import edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity;
import edu.washington.cs.ubicomplab.rdt_reader.ImageUtil;
import edu.washington.cs.ubicomplab.rdt_reader.callback.OnImageSavedCallBack;
import edu.washington.cs.ubicomplab.rdt_reader.presenter.RDTCapturePresenter;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.SAVED_IMAGE_FILE_PATH;
import static edu.washington.cs.ubicomplab.rdt_reader.Constants.SAVED_IMAGE_RESULT;

public class RDTCaptureActivity extends ImageQualityActivity implements ActivityCompat.OnRequestPermissionsResultCallback, OnImageSavedCallBack {

    private static final String TAG = RDTCaptureActivity.class.getName();
    private RDTCapturePresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presenter = new RDTCapturePresenter(this);
    }

    @Override
    public void useCapturedImage(ImageProcessor.CaptureResult captureResult, ImageProcessor.InterpretationResult interpretationResult, long timeTaken) {
        Log.i(TAG, "Processing captured image");
        final byte[] captureByteArray = ImageUtil.matToRotatedByteArray(captureResult.resultMat);
        boolean testResult = interpretTestResult(interpretationResult);
        presenter.saveImage(getApplicationContext(), captureByteArray, System.currentTimeMillis(), testResult, this);
    }

    protected boolean interpretTestResult(ImageProcessor.InterpretationResult interpretationResult) {
        return  interpretationResult.topLine && interpretationResult.middleLine
                || interpretationResult.topLine && interpretationResult.bottomLine;
    }

    @Override
    public void onImageSaved(String imageMetaData) {
        if (imageMetaData != null) {
            Map<String, String> keyVals = new HashMap();
            String[] vals = imageMetaData.split(",");
            keyVals.put(SAVED_IMAGE_FILE_PATH, vals[0]);
            keyVals.put(SAVED_IMAGE_RESULT, vals[1]);
            setResult(RESULT_OK, getResultIntent(keyVals));
        } else {
            Log.e(TAG, "Could not save null image path");
        }
        finish();
    }


    protected Intent getResultIntent(Map<String, String> keyVals) {
        Intent resultIntent = new Intent();
        for (Map.Entry<String, String> keyVal : keyVals.entrySet()) {
            resultIntent.putExtra(keyVal.getKey(), keyVal.getValue());
        }
        return resultIntent;
    }

    @Override
    public void onBackPressed() {
       finish();
    }
}
