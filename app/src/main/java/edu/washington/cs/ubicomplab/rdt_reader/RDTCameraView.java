package edu.washington.cs.ubicomplab.rdt_reader;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;

import org.opencv.android.JavaCameraView;

/**
 * Created by cjpark on 3/5/18.
 */

public class RDTCameraView extends JavaCameraView {
    public RDTCameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public RDTCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Camera getCamera() {
        return this.mCamera;
    }
}
