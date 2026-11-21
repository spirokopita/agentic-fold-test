package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/** Styling only: sensor acquisition, analysis and graph rendering stay in phyphox. */
final class NervSkin {
    private static final int AMBER = Color.rgb(255,154,26);
    static void apply(Activity activity) {
        activity.getWindow().setStatusBarColor(Color.BLACK);
        activity.getWindow().setNavigationBarColor(Color.BLACK);
        activity.getWindow().getDecorView().setSystemUiVisibility(0);
        style(activity.getWindow().getDecorView());
        if (activity instanceof de.rwth_aachen.phyphox.ExperimentList.ExperimentListActivity)
            activity.setTitle("NERV / PHYSICAL EXPERIMENTS");
    }
    private static void style(View view) {
        if (view instanceof androidx.appcompat.widget.Toolbar) {
            androidx.appcompat.widget.Toolbar toolbar = (androidx.appcompat.widget.Toolbar)view;
            toolbar.setBackgroundColor(Color.BLACK);
            toolbar.setTitleTextColor(AMBER);
            toolbar.setSubtitleTextColor(AMBER);
        }
        if (view instanceof TextView) {
            TextView text = (TextView)view;
            text.setTypeface(Typeface.MONOSPACE, text.getTypeface() != null ? text.getTypeface().getStyle() : Typeface.NORMAL);
        }
        if (view instanceof Button) {
            Button button = (Button)view;
            button.setTextColor(AMBER);
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.BLACK);
            background.setStroke(Math.max(1,Math.round(2*view.getResources().getDisplayMetrics().density)),AMBER);
            button.setBackground(background);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) style(group.getChildAt(i));
        }
    }
}