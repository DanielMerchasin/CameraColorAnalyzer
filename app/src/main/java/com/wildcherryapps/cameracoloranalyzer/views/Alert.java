package com.wildcherryapps.cameracoloranalyzer.views;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public final class Alert {

    /**
     * Convenience method for displaying a simple alert dialog
     */
    public static void display(Context context, String title, String message) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();
    }

}
