package edu.washington.cs.ubicomplab.rdt_reader.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import org.opencv.core.Mat;

import edu.washington.cs.ubicomplab.rdt_reader.ImageProcessor;
import edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity;
import edu.washington.cs.ubicomplab.rdt_reader.ImageUtil;
import edu.washington.cs.ubicomplab.rdt_reader.callback.OnImageSavedCallBack;
import edu.washington.cs.ubicomplab.rdt_reader.presenter.RDTCapturePresenter;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.SAVED_IMAGE_FILE_PATH;

public class RDTCaptureActivity extends ImageQualityActivity implements ActivityCompat.OnRequestPermissionsResultCallback, OnImageSavedCallBack {

    private static final String TAG = RDTCaptureActivity.class.getName();
    private RDTCapturePresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presenter = new RDTCapturePresenter(this);
    }

    @Override
    protected void useCapturedImage(Mat result, ImageProcessor.InterpretationResult interpretationResult) {
        Log.i(TAG, "Processing captured image");
        byte[] byteArray = ImageUtil.matToRotatedByteArray(result);
        presenter.saveImage(getApplicationContext(), byteArray, System.currentTimeMillis(), this);
    }

    @Override
    public void onImageSaved(String imageLocation) {
        if (imageLocation != null) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(SAVED_IMAGE_FILE_PATH, imageLocation);
            setResult(RESULT_OK, resultIntent);
        } else {
            Log.e(TAG, "Could not save null image path");
        }
        finish();
    }
}
