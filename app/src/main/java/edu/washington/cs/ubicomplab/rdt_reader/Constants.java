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

    public static double SIZE_THRESHOLD = 0.3;
    public static double POSITION_THRESHOLD = 0.2;

    public static int CALIBRATION_FRAME_COUNTER = 1;
    public static int FEATURE_MATCHING_FRAME_COUNTER = 0;
    public static int CAPTURE_COUNT = 3;

    public static Size PREVIEW_SIZE = new Size(960, 720);
    public static Size CAMERA2_PREVIEW_SIZE = new Size(1280, 720);
    public static Size CAMERA2_IMAGE_SIZE = new Size(960, 540);
    //public static double VIEWPORT_SCALE = 0.50;
    public static double VIEW_FINDER_SCALE_H = 0.52;
    public static double VIEW_FINDER_SCALE_W = 0.15;

    //Set for QuickVue
    public static int RESULT_WINDOW_X = 580;
    public static int RESULT_WINDOW_Y = 0;
    public static int RESULT_WINDOW_WIDTH = 130;
    public static int RESULT_WINDOW_HEIGHT = 50;

    //For SD Bioline Malaria
    //public static int RESULT_WINDOW_X = 177;
    //public static int RESULT_WINDOW_Y = 55;
    //public static int RESULT_WINDOW_WIDTH = 110;
    //public static int RESULT_WINDOW_HEIGHT = 35;


    public static String LANGUAGE = "en";

    public static String RDT_IMAGE_DIR = Environment.getExternalStorageDirectory() + "/Pictures/" +"/RDTImageCaptures/";

    public static int GOOD_MATCH_COUNT = 7;

    public static int MOVE_CLOSER_COUNT = 5;

    public static double CROP_RATIO = 0.6;

    public static float INTENSITY_THRESHOLD = 190;
    public static float CONTROL_INTENSITY_PEAK_THRESHOLD = 150;
    public static float TEST_INTENSITY_PEAK_THRESHOLD = 50;
    public static int LINE_SEARCH_WIDTH = 13;
    public static int CONTROL_LINE_POSITION = 45;
    public static int TEST_A_LINE_POSITION = 15;
    public static int TEST_B_LINE_POSITION = 75;
    public static Scalar CONTROL_LINE_COLOR_LOWER = new Scalar(160/2.0, 45/100.0*255.0, 5/100.0*255.0);
    public static Scalar CONTROL_LINE_COLOR_UPPER = new Scalar(260/2.0, 90/100.0*255.0, 15/100.0*255.0);
    public static int CONTROL_LINE_POSITION_MIN = 575;
    public static int CONTROL_LINE_POSITION_MAX = 700;
    public static int CONTROL_LINE_MIN_HEIGHT = 25;
    public static int CONTROL_LINE_MIN_WIDTH = 20;
    public static int CONTROL_LINE_MAX_WIDTH = 55;
}
