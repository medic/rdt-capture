package edu.washington.cs.ubicomplab.rdt_reader.interfaces;

import edu.washington.cs.ubicomplab.rdt_reader.models.RDTCaptureResult;
import edu.washington.cs.ubicomplab.rdt_reader.models.RDTInterpretationResult;
import edu.washington.cs.ubicomplab.rdt_reader.views.ImageQualityView;

public interface ImageQualityViewListener {
    void onRDTCameraReady();
    ImageQualityView.RDTDetectedResult onRDTDetected(
            RDTCaptureResult captureResult,
            RDTInterpretationResult rdtInterpretationResult,
            long timeTaken
    );
}
