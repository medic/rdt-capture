package edu.washington.cs.ubicomplab.rdt_reader.util;

import android.app.ProgressDialog;
import android.content.Context;
import android.support.annotation.StringRes;
import android.widget.ProgressBar;

/**
 * Created by Vincent Karuri on 11/10/2019
 */
public class Utils {
    private static ProgressDialog progressDialog;

    public static void showProgressDialog(@StringRes int title, @StringRes int message, Context context) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(context);
            progressDialog.setCancelable(false);
            progressDialog.setTitle(context.getString(title));
            progressDialog.setMessage(context.getString(message));
            progressDialog.show();
        }
    }

    public static void hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        progressDialog = null;
    }
}
