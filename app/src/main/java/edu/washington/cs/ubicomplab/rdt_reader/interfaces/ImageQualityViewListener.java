package edu.washington.cs.ubicomplab.rdt_reader.interfaces;

import edu.washington.cs.ubicomplab.rdt_reader.models.RdtCaptureResult;
import edu.washington.cs.ubicomplab.rdt_reader.models.RdtInterpretationResult;
import edu.washington.cs.ubicomplab.rdt_reader.views.ImageQualityView;

public interface ImageQualityViewListener {
    void onRDTCameraReady();
    ImageQualityView.RDTDetectedResult onRDTDetected(
            RdtCaptureResult captureResult,
            RdtInterpretationResult rdtInterpretationResult,
            long timeTaken
    );
}
