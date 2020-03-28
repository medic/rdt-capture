package edu.washington.cs.ubicomplab.rdt_reader.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.features2d.BFMatcher;
import org.opencv.imgproc.Imgproc;
import org.opencv.xfeatures2d.SIFT;

import java.io.InputStream;

import static edu.washington.cs.ubicomplab.rdt_reader.utils.ImageUtil.GAUSSIAN_BLUR_WINDOW;
import static org.opencv.imgproc.Imgproc.cvtColor;

/**
 * Created by cjpark on 7/13/19.
 */

public class RDT {
    int refImageID;
    public double viewFinderScaleH, viewFinderScaleW;
    int lineIntensity;
    int lineSearchWidth;
    int topLinePosition, middleLinePosition, bottomLinePosition;
    int fiducialPositionMin, fiducialPositionMax;
    int fiducialMinH, fiducialMinW, fiducialMaxW;
    int fiducialToResultWindowOffset;
    Rect resultWindowRect;
    int fiducialDistance;
    int fiducialCount;
    String topLineName, middleLineName, bottomLineName;

    public Mat refImg;
    double refImgSharpness;
    Mat refDescriptor;
    public MatOfKeyPoint refKeypoints;
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
            lineIntensity = obj.getInt("LINE_INTENSITY");
            lineSearchWidth = obj.getInt("LINE_SEARCH_WIDTH");
            topLinePosition = obj.getInt("TOP_LINE_POSITION");
            middleLinePosition = obj.getInt("MIDDLE_LINE_POSITION");
            bottomLinePosition = obj.getInt("BOTTOM_LINE_POSITION");

            JSONArray rectTL = obj.getJSONArray("RESULT_WINDOW_TOP_LEFT");
            JSONArray rectBR = obj.getJSONArray("RESULT_WINDOW_BOTTOM_RIGHT");
            resultWindowRect = new Rect(new Point(rectTL.getDouble(0), rectTL.getDouble(1)), new Point(rectBR.getDouble(0), rectBR.getDouble(1)));

            topLineName = obj.getString("TOP_LINE_NAME");
            middleLineName = obj.getString("MIDDLE_LINE_NAME");
            bottomLineName = obj.getString("BOTTOM_LINE_NAME");

            fiducialCount = obj.has("FIDUCIAL_COUNT") ? obj.getInt("FIDUCIAL_COUNT") : 0;
            fiducialDistance = fiducialCount > 0 ? obj.getInt("FIDUCIAL_DISTANCE") : 0;
            fiducialToResultWindowOffset = fiducialCount > 0 ? obj.getInt("FIDUCIAL_TO_RESULT_WINDOW_OFFSET") : 0;
            fiducialPositionMin = fiducialCount > 0 ? obj.getInt("FIDUCIAL_POSITION_MIN") : 0;
            fiducialPositionMax = fiducialCount > 0 ? obj.getInt("FIDUCIAL_POSITION_MAX") : 0;
            fiducialMinH = fiducialCount > 0 ? obj.getInt("FIDUCIAL_MIN_HEIGHT") : 0;
            fiducialMinW = fiducialCount > 0 ? obj.getInt("FIDUCIAL_MIN_WIDTH") : 0;
            fiducialMaxW = fiducialCount > 0 ? obj.getInt("FIDUCIAL_MAX_WIDTH") : 0;

            // Load ref img
            refImg = new Mat();
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), refImageID);
            Utils.bitmapToMat(bitmap, refImg);
            cvtColor(refImg, refImg, Imgproc.COLOR_RGB2GRAY);

            // Store the reference's sharpness
            Imgproc.GaussianBlur(refImg, refImg, new Size(GAUSSIAN_BLUR_WINDOW, GAUSSIAN_BLUR_WINDOW), 0, 0);

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
