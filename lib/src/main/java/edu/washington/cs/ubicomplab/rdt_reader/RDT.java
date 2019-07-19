package edu.washington.cs.ubicomplab.rdt_reader;

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

import java.io.IOException;
import java.io.InputStream;

import static edu.washington.cs.ubicomplab.rdt_reader.Constants.ONA_RDT;
import static org.opencv.imgproc.Imgproc.cvtColor;

/**
 * Created by cjpark on 7/13/19.
 */

public class RDT {
    int refImageID;
    double viewFinderScaleH;
    double viewFinderScaleW;
    int intensityThreshold;
    int controlIntensityPeakThreshold;
    int testIntensityPeakThreshold;
    int lineSearchWidth;
    int controlLinePosition;
    int testALinePosition;
    int testBLinePosition;
    int fiducialPositionMin;
    int fiducialPositionMax;
    int fiducialMinH;
    int fiducialMinW;
    int fiducialMaxH;
    int fiducialMaxW;
    int fiducialToResultWindowOffset;
    int resultWindowRectH;
    int resultWindowRectWPadding;
    int fiducialDistance;
    int fiducialCount;

    Mat refImg;
    double refImgSharpness;
    Mat refDescriptor;
    MatOfKeyPoint refKeypoints;
    SIFT detector;
    BFMatcher matcher;

    public RDT(Context context, String rdtName) {
        try {
            InputStream is = context.getAssets().open(Constants.CONFIG_FILE_NAME);

            int size = is.available();

            byte[] buffer = new byte[size];

            is.read(buffer);

            is.close();

            //load config
            JSONObject obj = new JSONObject(new String(buffer, "UTF-8")).getJSONObject(rdtName);
            if (obj == null) {
                // default to ona rdt
                obj = new JSONObject(new String(buffer, "UTF-8")).getJSONObject(ONA_RDT);
            }

            refImageID = context.getResources().getIdentifier(obj.getString("REF_IMG"), "drawable", context.getPackageName());
            viewFinderScaleH = obj.getDouble("VIEW_FINDER_SCALE_H");
            viewFinderScaleW = obj.getDouble("VIEW_FINDER_SCALE_W");
            intensityThreshold = obj.getInt("INTENSITY_THRESHOLD");
            controlIntensityPeakThreshold = obj.getInt("CONTROL_INTENSITY_PEAK_THRESHOLD");
            testIntensityPeakThreshold = obj.getInt("TEST_INTENSITY_PEAK_THRESHOLD");
            lineSearchWidth = obj.getInt("LINE_SEARCH_WIDTH");
            controlLinePosition = obj.getInt("CONTROL_LINE_POSITION");
            testALinePosition = obj.getInt("TEST_A_LINE_POSITION");
            testBLinePosition = obj.getInt("TEST_B_LINE_POSITION");
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

            //load ref img
            refImg = new Mat();
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), refImageID);
            Utils.bitmapToMat(bitmap, refImg);
            cvtColor(refImg, refImg, Imgproc.COLOR_RGB2GRAY);

            //store ref sharpness
            Imgproc.GaussianBlur(refImg, refImg, new Size(5, 5), 0, 0);

            //load features
            refDescriptor = new Mat();
            refKeypoints = new MatOfKeyPoint();
            detector = SIFT.create();
            matcher = BFMatcher.create(BFMatcher.BRUTEFORCE, false);
            detector.detectAndCompute(refImg, new Mat(), refKeypoints, refDescriptor);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
