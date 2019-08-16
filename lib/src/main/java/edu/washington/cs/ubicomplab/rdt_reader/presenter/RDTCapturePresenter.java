package edu.washington.cs.ubicomplab.rdt_reader.presenter;

import android.content.Context;

import edu.washington.cs.ubicomplab.rdt_reader.activity.RDTCaptureActivity;
import edu.washington.cs.ubicomplab.rdt_reader.callback.OnImageSavedCallBack;
import edu.washington.cs.ubicomplab.rdt_reader.interactor.RDTCaptureInteractor;

/**
 * Created by Vincent Karuri on 23/05/2019
 */
public class RDTCapturePresenter {

    private RDTCaptureActivity activity;
    private RDTCaptureInteractor interactor;

    public RDTCapturePresenter(RDTCaptureActivity activity) {
        this.activity = activity;
        this.interactor = new RDTCaptureInteractor(this);
    }

    public void saveImage(Context context, byte[] imageByteArray, long timeTaken, boolean testResult, OnImageSavedCallBack onImageSavedCallBack) {
        interactor.saveImage(context, imageByteArray, timeTaken, testResult, onImageSavedCallBack);
    }
}
