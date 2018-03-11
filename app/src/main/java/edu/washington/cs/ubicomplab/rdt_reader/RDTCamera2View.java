package edu.washington.cs.ubicomplab.rdt_reader;

import android.content.Context;
import android.util.AttributeSet;

import org.opencv.android.JavaCamera2View;

/**
 * Created by cjparkuw on 3/10/2018.
 */

public class RDTCamera2View extends JavaCamera2View {
    public RDTCamera2View(Context context, int cameraId) {
        super(context, cameraId);
    }

    public RDTCamera2View(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


}
