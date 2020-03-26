package edu.washington.cs.ubicomplab.rdt_reader.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Size;
import org.opencv.features2d.BFMatcher;
import org.opencv.imgproc.Imgproc;
import org.opencv.xfeatures2d.SIFT;

import java.io.InputStream;

import edu.washington.cs.ubicomplab.rdt_reader.utils.Constants;

import static org.opencv.imgproc.Imgproc.cvtColor;

/**
 * Created by cjpark on 7/13/19.
 */

public class RDT {
    int refImageID;
    public double viewFinderScaleH, viewFinderScaleW;
    int intensityThreshold;
    int controlIntensityPeakThreshold;
    int testIntensityPeakThreshold;
    int lineSearchWidth;
    int topLinePosition, middleLinePosition, bottomLinePosition;
    int fiducialPositionMin, fiducialPositionMax;
    int fiducialMinH, fiducialMinW, fiducialMaxH, fiducialMaxW;
    int fiducialToResultWindowOffset;
    int resultWindowRectH;
    int resultWindowRectWPadding;
    int fiducialDistance;
    int fiducialCount;
    String topLineName, middleLineName, bottomLineName;

    Mat refImg;
    double refImgSharpness;
    Mat refDescriptor;
    MatOfKeyPoint refKeypoints;
    SIFT detector;
    BFMatcher matcher;
    String rdtName;

    public RDT(Context context, String rdtName) {
        try {
            // Read the JSON
            InputStream is = context.getAssets().open(Constants.CONFIG_FILE_NAME);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            JSONObject obj = new JSONObject(new String(buffer, "UTF-8")).getJSONObject(rdtName);

            // Pull data from tags
            refImageID = context.getResources().getIdentifier(obj.getString("REF_IMG"), "drawable", context.getPackageName());
            viewFinderScaleH = obj.getDouble("VIEW_FINDER_SCALE_H");
            viewFinderScaleW = obj.getDouble("VIEW_FINDER_SCALE_W");
            intensityThreshold = obj.getInt("INTENSITY_THRESHOLD");
            controlIntensityPeakThreshold = obj.getInt("CONTROL_INTENSITY_PEAK_THRESHOLD");
            testIntensityPeakThreshold = obj.getInt("TEST_INTENSITY_PEAK_THRESHOLD");
            lineSearchWidth = obj.getInt("LINE_SEARCH_WIDTH");
            topLinePosition = obj.getInt("TOP_LINE_POSITION");
            middleLinePosition = obj.getInt("MIDDLE_LINE_POSITION");
            bottomLinePosition = obj.getInt("BOTTOM_LINE_POSITION");
            fiducialPositionMin = obj.getInt("FIDUCIAL_POSITION_MIN");
            fiducialPositionMax = obj.getInt("FIDUCIAL_POSITION_MAX");
            fiducialMinH = obj.getInt("FIDUCIAL_MIN_HEIGHT");
            fiducialMinW = obj.getInt("FIDUCIAL_MIN_WIDTH");
            fiducialMaxW = obj.getInt("FIDUCIAL_MAX_WIDTH");
            fiducialToResultWindowOffset = obj.getInt("FIDUCIAL_TO_RESULT_WINDOW_OFFSET");
            resultWindowRectH = obj.getInt("RESULT_WINDOW_RECT_HEIGHT");
            resultWindowRectWPadding = obj.getInt("RESULT_WINDOW_RECT_WIDTH_PADDING");
            fiducialDistance = obj.getInt("FIDUCIAL_DISTANCE");
            fiducialCount = obj.getInt("FIDUCIAL_COUNT");
            topLineName = obj.getString("TOP_LINE_NAME");
            middleLineName = obj.getString("MIDDLE_LINE_NAME");
            bottomLineName = obj.getString("BOTTOM_LINE_NAME");

            // Load ref img
            refImg = new Mat();
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), refImageID);
            Utils.bitmapToMat(bitmap, refImg);
            cvtColor(refImg, refImg, Imgproc.COLOR_RGB2GRAY);

            // Store the reference's sharpness
            Imgproc.GaussianBlur(refImg, refImg, new Size(5, 5), 0, 0);

            // Load the reference image's features
            refDescriptor = new Mat();
            refKeypoints = new MatOfKeyPoint();
            detector = SIFT.create();
            matcher = BFMatcher.create(BFMatcher.BRUTEFORCE, false);
            detector.detectAndCompute(refImg, new Mat(), refKeypoints, refDescriptor);

            this.rdtName = rdtName;

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
