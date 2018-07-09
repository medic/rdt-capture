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

    public static double BLUR_THRESHOLD = 0.0;
    public static double OVER_EXP_THRESHOLD = 255;
    public static double UNDER_EXP_THRESHOLD = 120;
    public static double OVER_EXP_WHITE_COUNT = 100;

    public static String OK = "<font color='#00EE00'>✔</font>";
    public static String NOT_OK = "<font color='#EE0000'>✘</font>";

    public static Scalar RDT_COLOR_HSV = new Scalar(30, 21, 204, 0.0);

    public static double SIZE_THRESHOLD = 0.2;
    public static double POSITION_THRESHOLD = 0.1;

    public static int CALIBRATION_FRAME_COUNTER = 1;
    public static int FEATURE_MATCHING_FRAME_COUNTER = 0;
    public static int CAPTURE_COUNT = 3;

    public static Size PREVIEW_SIZE = new Size(960, 720);
    public static Size CAMERA2_PREVIEW_SIZE = new Size(1280, 720);
    public static Size CAMERA2_IMAGE_SIZE = new Size(960, 540);
    public static double VIEWPORT_SCALE = 0.50;

    public static String LANGUAGE = "en";

    public static String RDT_IMAGE_DIR = Environment.getExternalStorageDirectory() + "/Pictures/" +"/RDTImageCaptures/";

    public static int GOOD_MATCH_COUNT = 7;
}
