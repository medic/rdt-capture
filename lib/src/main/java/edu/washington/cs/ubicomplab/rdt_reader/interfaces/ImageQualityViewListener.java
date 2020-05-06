package edu.washington.cs.ubicomplab.rdt_reader.interfaces;

import edu.washington.cs.ubicomplab.rdt_reader.core.RDTCaptureResult;
import edu.washington.cs.ubicomplab.rdt_reader.core.RDTInterpretationResult;
import edu.washington.cs.ubicomplab.rdt_reader.views.ImageQualityView;

/**
 * Interface for determining whether the app should advance to showing the test results
 */
public interface ImageQualityViewListener {
    /**
     * Method that should be implemented for when the camera is ready to process images
     */
    void onRDTCameraReady();

    /**
     *
     * @param captureResult
     * @param rdtInterpretationResult
     * @param timeTaken
     * @return
     */
    ImageQualityView.RDTDetectedResult onRDTDetected(
            RDTCaptureResult captureResult,
            RDTInterpretationResult rdtInterpretationResult,
            long timeTaken
    );
}
