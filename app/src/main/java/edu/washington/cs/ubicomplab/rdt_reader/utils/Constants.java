/*
 * Copyright (C) 2019 University of Washington Ubicomp Lab
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of a BSD-style license that can be found in the LICENSE file.
 */

package edu.washington.cs.ubicomplab.rdt_reader.utils;

import android.os.Environment;

import org.opencv.core.Scalar;
import org.opencv.core.Size;

public final class Constants {
    public static final String TAG = "RDT-reader";
    public static final String DEFAULT_RDT_NAME = "carestart";
    public static final String CONFIG_FILE_NAME = "config.json";
    public static final int MY_PERMISSION_REQUEST_CODE = 100;

    public static double SHARPNESS_THRESHOLD = 0.8;
    public static double OVER_EXP_THRESHOLD = 255;
    public static double UNDER_EXP_THRESHOLD = 120;
    public static double OVER_EXP_WHITE_COUNT = 0.2;

    public static double GLARE_WHITE_COUNT = 0.001;

    public static double SIZE_THRESHOLD = 0.15;
    public static double POSITION_THRESHOLD = 0.15;
    public static int ANGLE_THRESHOLD = 10;

    public static int CAPTURE_COUNT = 3;

    public static Size CAMERA2_PREVIEW_SIZE = new Size(1280, 720);
    public static Size CAMERA2_IMAGE_SIZE = new Size(1280, 720);

    public static String LANGUAGE = "en";

    public static String RDT_IMAGE_DIR = Environment.getExternalStorageDirectory() + "/Pictures/" +"/RDTImageCaptures/";

    public static int GOOD_MATCH_COUNT = 7;

    public static int MOVE_CLOSER_COUNT = 5;

    public static double CROP_RATIO = 0.75;

    public static double ENHANCING_THRESHOLD = 10.0;

    public static double BLOOD_PERCENTAGE_THRESHOLD = 0.25;

    /**
     * Default RDT Parameters
     */
    public static final String DEFAULT_TOP_LINE_NAME = "Top Line Name";
    public static final String DEFAULT_MIDDLE_LINE_NAME = "Middle Line Name";
    public static final String DEFAULT_BOTTOM_LINE_NAME = "Bottom Line Name";

}
