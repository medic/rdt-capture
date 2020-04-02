/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader.core;

import android.os.Environment;

import org.opencv.core.Scalar;
import org.opencv.core.Size;

/**
 * A class for holding all of the general app configurations and algorithm-specific values
 */
public final class Constants {
    // Debugging variables
    public static final String TAG = "RDT-reader";
    public static String RDT_IMAGE_DIR = Environment.getExternalStorageDirectory() +
            "/Pictures/" +"/RDTImageCaptures/";

    // Default settings
    public static String LANGUAGE = "en";
    public static final String DEFAULT_RDT_NAME = "malaria-carestart";
    public static final String CONFIG_FILE_NAME = "config.json";
    public static final String DEFAULT_TOP_LINE_NAME = "Top Line Name";
    public static final String DEFAULT_MIDDLE_LINE_NAME = "Middle Line Name";
    public static final String DEFAULT_BOTTOM_LINE_NAME = "Bottom Line Name";
    public static final int MY_PERMISSION_REQUEST_CODE = 100;
    public static final String CONTROL_LINE_NAME = "control";

    // Default camera size
    public static Size CAMERA2_PREVIEW_SIZE = new Size(1280, 720);
    public static Size CAMERA2_IMAGE_SIZE = new Size(1280, 720);

    // Overall image quality thresholds
    public static final int SHARPNESS_GAUSSIAN_BLUR_WINDOW = 5;
    public static double SHARPNESS_THRESHOLD = 0.8;
    public static double UNDER_EXPOSURE_THRESHOLD = 120;
    public static double OVER_EXPOSURE_THRESHOLD = 255;
    public static double OVER_EXPOSURE_WHITE_COUNT = 0.2;

    // RDT image quality thresholds
    public static double POSITION_THRESHOLD = 0.15;
    public static double SIZE_THRESHOLD = 0.15;
    public static int ANGLE_THRESHOLD = 10;

    // Result window image quality thresholds
    public static int GLARE_WHITE_VALUE = 225;
    public static double GLARE_WHITE_RATIO = 0.01;
    public static Scalar BLOOD_COLOR_LOW_HUE_LOWER = new Scalar(0, 100, 100);
    public static Scalar BLOOD_COLOR_LOW_HUE_UPPER = new Scalar(10, 255, 255);
    public static Scalar BLOOD_COLOR_HIGH_HUE_LOWER = new Scalar(160, 100, 100);
    public static Scalar BLOOD_COLOR_HIGH_HUE_UPPER = new Scalar(179, 255, 255);
    public static double BLOOD_PERCENTAGE_THRESHOLD = 0.25;
    public static int FIDUCIAL_SEARCH_NUM_CLUSTERS = 5;
    public static int FIDUCIAL_THRESHOLD = 20;

    // SIFT feature template matching parameters
    public static double SIFT_RESIZE_FACTOR = 0.5;
    public static int GOOD_MATCH_COUNT = 7;
    public static int RANSAC = 5;

    // Interpretation parameters
    public static double RESULT_WINDOW_ENHANCE_THRESHOLD = 7.5;
    public static int CLAHE_CLIP_LIMIT = 10;
    public static int CLAHE_WIDTH = 5;

    // Miscellaneous UX variables
    public static int CAPTURE_COUNT = 3;
    public static int MOVE_CLOSER_COUNT = 5;
    public static double CROP_RATIO = 0.75;
}
