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
import android.widget.Toast;

import net.simno.klingar.R;
import net.simno.klingar.playback.SleepTimer;
import net.simno.klingar.ui.widget.CircleSeekBar;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by topher on 04/03/2018.
 */
public class SleepTimerDialog extends DialogFragment {
    private CircleSeekBar circleSeekBar;
    private TextView selectedTime;
    private TextView stopTime;

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

        stopTime = sleepTimerView.findViewById(R.id.sleep_timer_stop_time);

        circleSeekBar.setOnSeekBarChangeListener(new CircleSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onChanged(CircleSeekBar seekbar, int curValue) {
                String hours = String.format(Locale.getDefault(),"%1$02d",
                        (int) (curValue / circleSeekBar.getMaxProcess()));
                String minutes = String.format(Locale.getDefault(),"%1$02d",
                        curValue % circleSeekBar.getMaxProcess());
                selectedTime.setText(getString(R.string.sleep_timer_dialog_selected_time,
                        hours, minutes));

                if (curValue == 0) {
                    stopTime.setText(R.string.sleep_timer_dialog_stop_time_unset);
                } else {
                    stopTime.setText(getString(R.string.sleep_timer_dialog_stop_time,
                            stopTime(curValue)));
                }

                setTextViewColors(curValue);
            }
        });

        circleSeekBar.setMaxProcess(60);
        circleSeekBar.setCurProcess(SleepTimer.getRemaining());

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity(),
                R.style.BaseDialogTheme);

        dialogBuilder = dialogBuilder.setView(sleepTimerView)
                .setNeutralButton(R.string.sleep_timer_dialog_cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Just close the dialog and do nothing further
                            }
                        });

        if (SleepTimer.isActive()) {
            dialogBuilder = dialogBuilder.setPositiveButton(R.string.sleep_timer_dialog_update,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SleepTimer.cancelSleepTimer();
                            SleepTimer.scheduleSleepTimer(circleSeekBar.getCurProcess());

                            Toast.makeText(getActivity(),
                                    getString(R.string.sleep_timer_dialog_toast_set,
                                            stopTime(circleSeekBar.getCurProcess())),
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
            dialogBuilder = dialogBuilder.setNegativeButton(R.string.sleep_timer_dialog_delete,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SleepTimer.cancelSleepTimer();
                            Toast.makeText(getActivity(),
                                    R.string.sleep_timer_dialog_toast_delete,
                                    Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
            );
        } else {
            dialogBuilder = dialogBuilder.setPositiveButton(R.string.sleep_timer_dialog_new,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SleepTimer.cancelSleepTimer();
                            SleepTimer.scheduleSleepTimer(circleSeekBar.getCurProcess());

                            Toast.makeText(getActivity(),
                                    getString(R.string.sleep_timer_dialog_toast_set,
                                            stopTime(circleSeekBar.getCurProcess())),
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
        }

        return dialogBuilder.create();
    }

    private void setTextViewColors(int value) {
        if (!SleepTimer.isActive() && value == 0) {
            int color = ContextCompat.getColor(getActivity(), R.color.primary);
            selectedTime.setTextColor(color);
            stopTime.setTextColor(color);
            return;
        } else if (!SleepTimer.isActive()) {
            selectedTime.setTextColor(ContextCompat.getColor(getActivity(), R.color.primary_light));
            stopTime.setTextColor(ContextCompat.getColor(getActivity(), R.color.primary_light));
            return;
        }

        // An active timer text has the same color has the circle pointer
        selectedTime.setTextColor(ContextCompat.getColor(getActivity(),
                R.color.circleseekbar_pointer));

        stopTime.setTextColor(ContextCompat.getColor(getActivity(), R.color.primary_light));
    }

    private String stopTime(int delay) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, delay);
        return DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(cal.getTime());
    }
}
