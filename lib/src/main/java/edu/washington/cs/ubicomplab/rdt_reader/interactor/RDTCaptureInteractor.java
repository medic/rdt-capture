package edu.washington.cs.ubicomplab.rdt_reader.interactor;

import android.content.Context;

import edu.washington.cs.ubicomplab.rdt_reader.callback.OnImageSavedCallBack;
import edu.washington.cs.ubicomplab.rdt_reader.presenter.RDTCapturePresenter;
import edu.washington.cs.ubicomplab.rdt_reader.utils.ImageUtil;

/**
 * Created by Vincent Karuri on 23/05/2019
 */
public class RDTCaptureInteractor {

    RDTCapturePresenter presenter;

    public RDTCaptureInteractor(RDTCapturePresenter presenter) {
        this.presenter = presenter;
    }

    public void saveImage(Context context, byte[] imageByteArray, long timeTaken, boolean testResult, OnImageSavedCallBack onImageSavedCallBack) {
        ImageUtil.saveImage(context, imageByteArray, timeTaken, testResult, onImageSavedCallBack);
    }
}
