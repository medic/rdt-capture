package edu.washington.cs.ubicomplab.rdt_reader.core;

import android.app.ListActivity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.features2d.BFMatcher;
import org.opencv.imgproc.Imgproc;
import org.opencv.xfeatures2d.SIFT;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

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
    public double topLinePosition, middleLinePosition, bottomLinePosition;
    public String topLineName, middleLineName, bottomLineName;
    public int lineIntensity;
    public int lineSearchWidth;
    public ArrayList<double[]> topLineHueRange, middleLineHueRange, bottomLineHueRange;

    // Fiducial variables
    public double distanctFromFiducialToResultWindow;
    public Rect resultWindowRect;
    public JSONArray fiducials;
    public ArrayList<Rect> fiducialRects;
    public boolean hasFiducial;
    // Feature matching variables
    public Mat refImg;
    public double refImgSharpness;
    public Mat refDescriptor;
    public MatOfKeyPoint refKeypoints;
    public SIFT detector;
    public BFMatcher matcher;
    //Glare check variables
    public boolean checkGlare;

    public boolean rotated = false;

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

            if(refImg.height() > refImg.width()) {
                Core.rotate(refImg, refImg, Core.ROTATE_90_COUNTERCLOCKWISE);
                rotated = true;
            }

            cvtColor(refImg, refImg, Imgproc.COLOR_RGB2GRAY);
            this.rdtName = rdtName;

            // Pull data related to UI
            viewFinderScaleH = obj.getDouble("VIEW_FINDER_SCALE");
            viewFinderScaleW = (viewFinderScaleH * (double)refImg.height()/(double)refImg.width())+Constants.VIEW_FINDER_SCALE_W_PADDING;
            //viewFinderScaleW = obj.getDouble("VIEW_FINDER_SCALE_W");
            JSONArray rectTL = obj.getJSONArray("RESULT_WINDOW_TOP_LEFT");
            JSONArray rectBR = obj.getJSONArray("RESULT_WINDOW_BOTTOM_RIGHT");
            resultWindowRect = rotated ? new Rect(new Point(rectTL.getDouble(1), rectTL.getDouble(0)),
                    new Point(rectBR.getDouble(1), rectBR.getDouble(0))) :
                    new Rect(new Point(rectTL.getDouble(0), rectTL.getDouble(1)),
                            new Point(rectBR.getDouble(0), rectBR.getDouble(1)));

            // Pull data related to the result window
            topLinePosition = rotated ? obj.getJSONArray("TOP_LINE_POSITION").getDouble(1) - resultWindowRect.x : obj.getJSONArray("TOP_LINE_POSITION").getDouble(0) - resultWindowRect.x;
            middleLinePosition = rotated ? obj.getJSONArray("MIDDLE_LINE_POSITION").getDouble(1) - resultWindowRect.x: obj.getJSONArray("MIDDLE_LINE_POSITION").getDouble(0) - resultWindowRect.x;
            bottomLinePosition = rotated ? obj.getJSONArray("BOTTOM_LINE_POSITION").getDouble(1) - resultWindowRect.x: obj.getJSONArray("BOTTOM_LINE_POSITION").getDouble(0) - resultWindowRect.x;
            topLineHueRange = obj.has("TOP_LINE_HUE_RANGE") ? new ArrayList<double[]>((Collection<? extends double[]>) obj.getJSONArray("TOP_LINE_HUE_RANGE")) : new ArrayList<double[]>();
            middleLineHueRange = obj.has("MIDDLE_LINE_HUE_RANGE") ? new ArrayList<double[]>((Collection<? extends double[]>) obj.getJSONArray("MIDDLE_LINE_HUE_RANGE")) : new ArrayList<double[]>();
            bottomLineHueRange = obj.has("BOTTOM_LINE_HUE_RANGE") ? new ArrayList<double[]>((Collection<? extends double[]>) obj.getJSONArray("BOTTOM_LINE_HUE_RANGE")) : new ArrayList<double[]>();
            topLineName = obj.getString("TOP_LINE_NAME");
            middleLineName = obj.getString("MIDDLE_LINE_NAME");
            bottomLineName = obj.getString("BOTTOM_LINE_NAME");
            lineIntensity = obj.getInt("LINE_INTENSITY");
            lineSearchWidth = obj.has("LINE_SEARCH_WIDTH") ? obj.getInt("LINE_SEARCH_WIDTH") :
                    Math.max((int)((middleLinePosition-topLinePosition)/2.0),(int)((bottomLinePosition-middleLinePosition)/2.0));

            checkGlare = obj.has("CHECK_GLARE") ? obj.getBoolean("CHECK_GLARE") : false;

            // Pull data related to fiducials
            fiducials = obj.has("FIDUCIALS") ? obj.getJSONArray("FIDUCIALS") : new JSONArray();
            hasFiducial = fiducials.length() > 0;
            distanctFromFiducialToResultWindow = 0;

            if (hasFiducial && fiducials.length() == 2) {
                JSONArray trueFiducial1 = fiducials.getJSONArray(0);
                Point trueFiducialTL1 = rotated
                        ? new Point(trueFiducial1.getJSONArray(0).getDouble(1), trueFiducial1.getJSONArray(0).getDouble(0))
                        : new Point(trueFiducial1.getJSONArray(0).getDouble(0), trueFiducial1.getJSONArray(0).getDouble(1));
                Point trueFiducialBR1 = rotated
                        ? new Point(trueFiducial1.getJSONArray(1).getDouble(1), trueFiducial1.getJSONArray(1).getDouble(0))
                        : new Point(trueFiducial1.getJSONArray(1).getDouble(0), trueFiducial1.getJSONArray(1).getDouble(1));

                JSONArray trueFiducial2 = fiducials.getJSONArray(1);
                Point trueFiducialTL2 = rotated
                        ? new Point(trueFiducial2.getJSONArray(0).getDouble(1), trueFiducial2.getJSONArray(0).getDouble(0))
                        : new Point(trueFiducial2.getJSONArray(0).getDouble(0), trueFiducial2.getJSONArray(0).getDouble(1));
                Point trueFiducialBR2 = rotated
                        ? new Point(trueFiducial2.getJSONArray(1).getDouble(1), trueFiducial2.getJSONArray(1).getDouble(0))
                        : new Point(trueFiducial2.getJSONArray(1).getDouble(0), trueFiducial2.getJSONArray(1).getDouble(1));

                fiducialRects = new ArrayList<>();

                fiducialRects.add(new Rect(trueFiducialTL1, trueFiducialBR1));
                fiducialRects.add(new Rect(trueFiducialTL2, trueFiducialBR2));

                distanctFromFiducialToResultWindow = resultWindowRect.x - (trueFiducialBR2.x + trueFiducialBR1.x)/2.0;
            }

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