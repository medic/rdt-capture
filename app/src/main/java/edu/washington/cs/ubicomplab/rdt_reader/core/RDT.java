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

import static edu.washington.cs.ubicomplab.rdt_reader.core.Constants.SHARPNESS_GAUSSIAN_BLUR_WINDOW;
import static org.opencv.imgproc.Imgproc.cvtColor;

/**
 * Object for holding all of the RDT-specific variables, including those provided in config.json
 */
public class RDT {
    // Template image variables
    public int refImageID;
    public String rdtName;

    // UI variables
    public double viewFinderScaleH, viewFinderScaleW;

    // Result window variables
    public int topLinePosition, middleLinePosition, bottomLinePosition;
    public String topLineName, middleLineName, bottomLineName;
    public int lineIntensity;
    public int lineSearchWidth;

    // Fiducial variables
    public int fiducialPositionMin, fiducialPositionMax;
    public int fiducialMinH, fiducialMinW, fiducialMaxW;
    public int fiducialToResultWindowOffset;
    public Rect resultWindowRect;
    public int fiducialDistance;
    public int fiducialCount;

    // Feature matching variables
    public Mat refImg;
    public double refImgSharpness;
    public Mat refDescriptor;
    public MatOfKeyPoint refKeypoints;
    public SIFT detector;
    public BFMatcher matcher;

    public RDT(Context context, String rdtName) {
        try {
            // Read config.json
            InputStream is = context.getAssets().open(Constants.CONFIG_FILE_NAME);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            JSONObject obj = new JSONObject(new String(buffer, "UTF-8")).getJSONObject(rdtName);

            // Load the template image
            refImageID = context.getResources().getIdentifier(obj.getString("REF_IMG"),
                    "drawable", context.getPackageName());
            refImg = new Mat();
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), refImageID);
            Utils.bitmapToMat(bitmap, refImg);
            cvtColor(refImg, refImg, Imgproc.COLOR_RGB2GRAY);
            this.rdtName = rdtName;

            // Pull data related to UI
            viewFinderScaleH = obj.getDouble("VIEW_FINDER_SCALE_H");
            viewFinderScaleW = obj.getDouble("VIEW_FINDER_SCALE_W");
            JSONArray rectTL = obj.getJSONArray("RESULT_WINDOW_TOP_LEFT");
            JSONArray rectBR = obj.getJSONArray("RESULT_WINDOW_BOTTOM_RIGHT");
            resultWindowRect = new Rect(new Point(rectTL.getDouble(0), rectTL.getDouble(1)),
                    new Point(rectBR.getDouble(0), rectBR.getDouble(1)));

            // Pull data related to the result window
            topLinePosition = obj.getInt("TOP_LINE_POSITION");
            middleLinePosition = obj.getInt("MIDDLE_LINE_POSITION");
            bottomLinePosition = obj.getInt("BOTTOM_LINE_POSITION");
            topLineName = obj.getString("TOP_LINE_NAME");
            middleLineName = obj.getString("MIDDLE_LINE_NAME");
            bottomLineName = obj.getString("BOTTOM_LINE_NAME");
            lineIntensity = obj.getInt("LINE_INTENSITY");
            lineSearchWidth = obj.getInt("LINE_SEARCH_WIDTH");

            // Pull data related to fiducials
            boolean hasFiducial = fiducialCount > 0;
            fiducialCount = obj.has("FIDUCIAL_COUNT") ? obj.getInt("FIDUCIAL_COUNT") : 0;
            fiducialDistance = hasFiducial ? obj.getInt("FIDUCIAL_DISTANCE") : 0;
            fiducialToResultWindowOffset = hasFiducial ? obj.getInt("FIDUCIAL_TO_RESULT_WINDOW_OFFSET") : 0;
            fiducialPositionMin = hasFiducial ? obj.getInt("FIDUCIAL_POSITION_MIN") : 0;
            fiducialPositionMax = hasFiducial ? obj.getInt("FIDUCIAL_POSITION_MAX") : 0;
            fiducialMinH = hasFiducial ? obj.getInt("FIDUCIAL_MIN_HEIGHT") : 0;
            fiducialMinW = hasFiducial ? obj.getInt("FIDUCIAL_MIN_WIDTH") : 0;
            fiducialMaxW = hasFiducial ? obj.getInt("FIDUCIAL_MAX_WIDTH") : 0;

            // Store the reference's sharpness
            Size kernel = new Size(SHARPNESS_GAUSSIAN_BLUR_WINDOW,
                    SHARPNESS_GAUSSIAN_BLUR_WINDOW);
            Imgproc.GaussianBlur(refImg, refImg, kernel, 0, 0);

            // Load the reference image's features
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
