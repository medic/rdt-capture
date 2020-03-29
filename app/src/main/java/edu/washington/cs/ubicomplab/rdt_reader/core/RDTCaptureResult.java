package edu.washington.cs.ubicomplab.rdt_reader.core;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;

/**
 * Object for holding all of the parameters that describe whether a candidate video framed
 * passed all of the quality checks
 */
public class RDTCaptureResult {
    public boolean allChecksPassed;
    public Mat resultMat;
    public MatOfPoint2f boundary;

    public ImageProcessor.ExposureResult exposureResult;
    public boolean isSharp;

    public boolean isCentered;
    public ImageProcessor.SizeResult sizeResult;
    public boolean isOriented;
    public double angle;

    public boolean isShadow;
    public boolean fiducial;
    public boolean flashEnabled;
    public boolean isGlared;

    public RDTCaptureResult(boolean allChecksPassed, Mat resultMat, boolean fiducial,
                            ImageProcessor.ExposureResult exposureResult, ImageProcessor.SizeResult sizeResult, boolean isCentered,
                            boolean isOriented, double angle, boolean isSharp, boolean isShadow, boolean isGlared, MatOfPoint2f boundary, boolean flashEnabled) {
        this.allChecksPassed = allChecksPassed;
        this.resultMat = resultMat;
        this.fiducial = fiducial;
        this.exposureResult = exposureResult;
        this.sizeResult = sizeResult;
        this.isCentered = isCentered;
        this.isOriented = isOriented;
        this.isSharp = isSharp;
        this.isShadow = isShadow;
        this.angle = angle;
        this.boundary = boundary;
        this.flashEnabled = flashEnabled;
        this.isGlared = isGlared;
    }
}