package net.simno.klingar.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.simno.klingar.R;
import net.simno.klingar.playback.SleepTimer;
import net.simno.klingar.ui.widget.CircleSeekBar;

import java.util.Locale;

import timber.log.Timber;

/**
 * Created by topher on 04/03/2018.
 */
public class SleepTimerDialog extends DialogFragment {
    private CircleSeekBar circleSeekBar;
    private TextView selectedTime;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        RelativeLayout sleepTimerView;

        try {
            LayoutInflater layoutInflater = (LayoutInflater) getActivity()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            sleepTimerView = (RelativeLayout) layoutInflater
                    .inflate(R.layout.sleep_timer, null);
        } catch (NullPointerException e) {
            e.printStackTrace();
            return new AlertDialog.Builder(getActivity(), R.style.BaseDialogTheme).create();
        }

        circleSeekBar = (CircleSeekBar) sleepTimerView
                .findViewById(R.id.sleep_timer_circle_seekbar);

        selectedTime = (TextView) sleepTimerView
                .findViewById(R.id.sleep_timer_selected_time);

        circleSeekBar.setOnSeekBarChangeListener(new CircleSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onChanged(CircleSeekBar seekbar, int curValue) {
                String hours = String.format(Locale.getDefault(),"%1$02d",
                        (int) (curValue / circleSeekBar.getMaxProcess()));
                String minutes = String.format(Locale.getDefault(),"%1$02d",
                        curValue % circleSeekBar.getMaxProcess());
                selectedTime.setText(getString(R.string.sleep_timer_dialog_selected_time,
                        hours, minutes));
                setTextColor(curValue);

            }
        });

        circleSeekBar.setMaxProcess(60);
        circleSeekBar.setCurProcess(SleepTimer.getRemaining());

        return new AlertDialog.Builder(getActivity(), R.style.BaseDialogTheme)
                .setView(sleepTimerView)
                .setPositiveButton(R.string.sleep_timer_dialog_new, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Timber.e("sleep_dialog OK click");
                        SleepTimer.cancelSleepTimer();
                        SleepTimer.scheduleSleepTimer(circleSeekBar.getCurProcess());
                    }
                })
                .setNegativeButton(R.string.sleep_timer_dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Timber.e("sleep_dialog CANCEL click");
                        SleepTimer.cancelSleepTimer();
                    }
                })
                .create();
    }

    private void setTextColor(int value) {
        if (!SleepTimer.isActive() && value == 0) {
            selectedTime.setTextColor(ContextCompat.getColor(getActivity(), R.color.primary));
            return;
        } else if (!SleepTimer.isActive()) {
            selectedTime.setTextColor(ContextCompat.getColor(getActivity(), R.color.primary_light));
            return;
        }

        // An active timer text has the same color has the circle pointer
        selectedTime.setTextColor(ContextCompat.getColor(getActivity(),
                R.color.circleseekbar_pointer));
    }
}
