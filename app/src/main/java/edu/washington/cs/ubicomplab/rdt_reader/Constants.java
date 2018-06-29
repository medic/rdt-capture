package edu.washington.cs.ubicomplab.rdt_reader;

import android.os.Environment;

import org.opencv.core.Scalar;
import org.opencv.core.Size;

/**
 * Created by cjparkuw on 3/9/2018.
 */

public final class Constants {
    public static final String TAG = "RDT-reader";
    public static final int MY_PERMISSION_REQUEST_CODE = 100;
    public static final String[] DATE_FORMATS = {"yyyy/MM/dd","yyyy.MM.dd","yyyy-MM-dd", "yyyyMMdd"};

    public static final double BLUR_THRESHOLD = 0.0;
    public static final double OVER_EXP_THRESHOLD = 255;
    public static final double UNDER_EXP_THRESHOLD = 120;
    public static final double OVER_EXP_WHITE_COUNT = 50;

    public static final String OK = "<font color='#00EE00'>✔</font>";
    public static final String NOT_OK = "<font color='#EE0000'>✘</font>";

    public static final Scalar RDT_COLOR_HSV = new Scalar(30, 21, 204, 0.0);

    public static final double SIZE_THRESHOLD = 0.2;
    public static final double POSITION_THRESHOLD = 0.2;

    public static final int CALIBRATION_FRAME_COUNTER = 1;
    public static final int FEATURE_MATCHING_FRAME_COUNTER = 0;
    public static final int CAPTURE_COUNT = 3;

    public static final double VIEWPORT_SCALE = 0.50;

    public static final String LANGUAGE = "en";

    public static final String RDT_IMAGE_DIR = Environment.getExternalStorageDirectory() + "/Pictures/" +"/RDTImageCaptures/";
}
