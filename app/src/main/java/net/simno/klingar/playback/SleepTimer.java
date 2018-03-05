package net.simno.klingar.playback;

import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Created by topher on 05/03/2018.
 */
public class SleepTimer {
    private static Date end;
    private static MusicController musicController;
    private static Timer sleepTimer;

    public static void scheduleSleepTimer(int delay) {
        // Cancel any pre-existing timers, there may only be one scheduled
        // timer at a time
        if (sleepTimer != null) {
            cancelSleepTimer();
        }

        Date now = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.add(Calendar.MINUTE, delay);
        end = c.getTime();

        long delayMS = TimeUnit.MILLISECONDS.convert(delay, TimeUnit.MINUTES);

        sleepTimer = new Timer();
        sleepTimer.schedule(new SleepTimerTask(), delayMS);
    }

    public static void setMusicController(MusicController mc) {
        musicController = mc;
    }

    public static boolean isActive() {
        return end != null;
    }

    public static void cancelSleepTimer() {
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
            end = null;
        }
    }

    public static int getRemaining() {
        if (end == null) {
            return 0;
        }

        long remainingMS = end.getTime() - new Date().getTime();
        // Add 1 to the exact time, as TimeUnit.convert alwys rounds down, this way
        // we only show remaining time 0 when there is no timer set.
        return 1 + (int) TimeUnit.MINUTES.convert(remainingMS, TimeUnit.MILLISECONDS);
    }

    protected static class SleepTimerTask extends TimerTask {
        private boolean waitTillEnd;

        protected SleepTimerTask() {
            super();
            this.waitTillEnd = waitTillEnd;
        }

        public void setWaitTillEnd(boolean wait) {
            waitTillEnd = wait;
        }

        public boolean getWaitTillEnd() {
            return waitTillEnd;
        }

        @Override
        public boolean cancel() {
            return super.cancel();
        }

        @Override
        public long scheduledExecutionTime() {
            return super.scheduledExecutionTime();
        }

        @Override
        public void run() {
            musicController.pause();
            end = null;
        }
    }
}