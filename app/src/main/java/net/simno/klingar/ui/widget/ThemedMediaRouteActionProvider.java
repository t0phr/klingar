package net.simno.klingar.ui.widget;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.app.MediaRouteChooserDialog;
import android.support.v7.app.MediaRouteChooserDialogFragment;
import android.support.v7.app.MediaRouteControllerDialog;
import android.support.v7.app.MediaRouteControllerDialogFragment;
import android.support.v7.app.MediaRouteDialogFactory;

import net.simno.klingar.R;

/**
 * Created by topher on 05/03/2018.
 */

public class ThemedMediaRouteActionProvider extends MediaRouteActionProvider {

    public ThemedMediaRouteActionProvider(Context context) {
        super(context);
        setDialogFactory(new MediaRouteDialogFactoryThemeLight());
    }

    private static class MediaRouteDialogFactoryThemeLight extends MediaRouteDialogFactory {
        @NonNull
        @Override
        public MediaRouteChooserDialogFragment onCreateChooserDialogFragment() {
            return new MediaRouteChooserDialogFragmentThemeLight();
        }

        @NonNull
        @Override
        public MediaRouteControllerDialogFragment onCreateControllerDialogFragment() {
            return new MediaRouteControllerDialogFragmentThemeLight();
        }

    }

    public static class MediaRouteChooserDialogFragmentThemeLight extends MediaRouteChooserDialogFragment {
        @Override
        public MediaRouteChooserDialog onCreateChooserDialog(Context context, Bundle savedInstanceState) {
            return new MediaRouteChooserDialog(context, R.style.CastChooserTheme);
        }
    }

    public static class MediaRouteControllerDialogFragmentThemeLight extends MediaRouteControllerDialogFragment {

        @Override
        public MediaRouteControllerDialog onCreateControllerDialog(Context context, Bundle savedInstanceState) {
            return new ThemedMediaRouteControllerDialog(context, R.style.CastControllerTheme);
        }
    }

    public static class ThemedMediaRouteControllerDialog extends MediaRouteControllerDialog {
        public ThemedMediaRouteControllerDialog(Context context, int theme) {
            super(context, theme);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            findViewById(R.id.mr_dialog_area).setBackgroundResource(R.drawable.dialog_background);
        }
    }

}