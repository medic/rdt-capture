package edu.washington.cs.ubicomplab.rdt_reader.activity;

import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import org.opencv.core.Mat;

import edu.washington.cs.ubicomplab.rdt_reader.ImageQualityActivity;
import edu.washington.cs.ubicomplab.rdt_reader.ImageUtil;
import edu.washington.cs.ubicomplab.rdt_reader.callback.OnImageSavedCallBack;
import edu.washington.cs.ubicomplab.rdt_reader.presenter.RDTCapturePresenter;

public class RDTCaptureActivity extends ImageQualityActivity implements ActivityCompat.OnRequestPermissionsResultCallback, OnImageSavedCallBack {

    private static final String TAG = RDTCaptureActivity.class.getName();
    private RDTCapturePresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presenter = new RDTCapturePresenter(this);
    }

    @Override
    protected void useCapturedImage(Mat result) {
        Log.i(TAG, "Processing captured image");
        byte[] byteArray = ImageUtil.matToRotatedByteArray(result);
        presenter.saveImage(getApplicationContext(), byteArray, System.currentTimeMillis(), this);
    }

    @Override
    public void onImageSaved(String imageLocation) {
        if (imageLocation != null) {
            // todo: do something
        } else {
            Log.e(TAG, "Could not save null image path");
        }
        finish();
    }
}
