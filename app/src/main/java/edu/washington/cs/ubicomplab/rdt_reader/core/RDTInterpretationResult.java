package edu.washington.cs.ubicomplab.rdt_reader.core;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import static edu.washington.cs.ubicomplab.rdt_reader.core.Constants.DEFAULT_BOTTOM_LINE_NAME;
import static edu.washington.cs.ubicomplab.rdt_reader.core.Constants.DEFAULT_MIDDLE_LINE_NAME;
import static edu.washington.cs.ubicomplab.rdt_reader.core.Constants.DEFAULT_TOP_LINE_NAME;

/**
 * Interpretation result represents the image result of RDT image after processing which describes if
 * a particular line of a RDT is appeared (3 lines in this example)
 */

public class RDTInterpretationResult {
    public boolean topLine;
    public boolean middleLine;
    public boolean bottomLine;
    public String topLineName;
    public String middleLineName;
    public String bottomLineName;
    public Mat resultMat;
    public Bitmap resultBitmap;
    public boolean hasTooMuchBlood;
    public int numberOfLines;

    public RDTInterpretationResult() {
        topLine = false;
        middleLine = false;
        bottomLine = false;
        topLineName = DEFAULT_TOP_LINE_NAME;
        middleLineName = DEFAULT_MIDDLE_LINE_NAME;
        bottomLineName = DEFAULT_BOTTOM_LINE_NAME;
        resultMat = new Mat();
        resultBitmap = null;
        hasTooMuchBlood = false;
        numberOfLines = 2;
    }

    public RDTInterpretationResult(Mat resultMat, boolean topLine, boolean middleLine, boolean bottomLine,
                                   String topLineName, String middleLineName, String bottomLineName,
                                   boolean hasTooMuchBlood, int numberOfLines){
        this.resultMat = resultMat;
        this.topLine = topLine;
        this.middleLine = middleLine;
        this.bottomLine = bottomLine;
        this.topLineName = topLineName;
        this.middleLineName = middleLineName;
        this.bottomLineName = bottomLineName;
        this.hasTooMuchBlood = hasTooMuchBlood;
        this.numberOfLines = numberOfLines;

        // Convert the image to a Bitmap so it can be displayed with Android
        if (resultMat.cols() > 0 && resultMat.rows() > 0) {
            this.resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(),
                    Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(resultMat, resultBitmap);
        }
    }
}

