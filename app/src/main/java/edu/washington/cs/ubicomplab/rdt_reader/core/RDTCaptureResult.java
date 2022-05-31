package edu.washington.cs.ubicomplab.rdt_reader.core;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;

/**
 * Object for holding all of the parameters that describe whether a candidate video framed
 * passed all of the quality checks
 */
public class RDTCaptureResult {
    // High-level variables
    public boolean allChecksPassed;
    public Mat resultMat;
    public MatOfPoint2f boundary;
    public boolean flashEnabled;

    // Overall image quality variables
    public ImageProcessor.ExposureResult exposureResult;
    public boolean isSharp;

    // RDT image quality variables
    public boolean isCentered;
    public ImageProcessor.SizeResult sizeResult;
    public boolean isOriented;
    public double angle;

    // Result window variables
    public boolean isGlared;
    public boolean fiducial;

    public RDTCaptureResult(boolean allChecksPassed, Mat resultMat,
                            MatOfPoint2f boundary, boolean flashEnabled,
                            ImageProcessor.ExposureResult exposureResult, boolean isSharp,
                            boolean isCentered, ImageProcessor.SizeResult sizeResult,
                            boolean isOriented, double angle,
                            boolean isGlared, boolean fiducial) {
        this.allChecksPassed = allChecksPassed;
        this.resultMat = resultMat;
        this.boundary = boundary;
        this.flashEnabled = flashEnabled;

        this.exposureResult = exposureResult;
        this.isSharp = isSharp;

        this.isCentered = isCentered;
        this.sizeResult = sizeResult;
        this.isOriented = isOriented;
        this.angle = angle;

        this.fiducial = fiducial;
        this.isGlared = isGlared;
    }
}