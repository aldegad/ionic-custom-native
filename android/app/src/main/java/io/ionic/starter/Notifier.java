package io.ionic.starter;

/*
This is a wrapper for the MediaPlayer.
It allows the app to play a few sounds.
The sounds are .wav files placed in /res/raw
 */
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;


public class Notifier {

    private static final String TAG = "APP_Notifier";

    private static MediaPlayer mp = null;

    private static Handler soundHandler = new Handler();
    private static Runnable soundRunnable;

    /* Play a sound. Release the mp resource when it has finished playing */
    public static void playSound(Context context, String soundName, int delay) {
        Log.d(TAG, "Playing " + soundName);

        try {
            if (soundName.equals("shipsbell")) {
                mp = MediaPlayer.create(context, R.raw.shipsbell);
            } else if (soundName.equals("plop")) {
                mp = MediaPlayer.create(context, R.raw.plop);
            } else if (soundName.equals("boxing3")) {
                mp = MediaPlayer.create(context, R.raw.boxing3);
            } else if (soundName.equals("plunk")) {
                mp = MediaPlayer.create(context, R.raw.plunk);
            } else if (soundName.equals("bowlbell")) {
                mp = MediaPlayer.create(context, R.raw.bowlbell);
            } else if (soundName.equals("ovending")) {
                mp = MediaPlayer.create(context, R.raw.ovending);
            } else {
                mp = MediaPlayer.create(context, R.raw.dingding);
            }

            /* prepare to release MediaPlayer when the sound is finished */
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                    Log.i(TAG, "Sound finished");
                }
            });

            soundHandler.removeCallbacks(soundRunnable);

            if (delay == 0) {
                if (mp.isPlaying()) {
                    mp.stop();
                }
                //mp.reset();
                /* Now play the sound */
                mp.start();
            } else {
                soundRunnable = new Runnable() {
                    @Override
                    public void run() {
                        // This suggestion : https://stackoverflow.com/questions/8264481/mediaplayer-plays-twice
                        if (mp.isPlaying()) {
                            mp.stop();
                        }
                        //mp.reset();
                        /* Now play the sound */
                        mp.start();
                    }
                };

                soundHandler.postDelayed(soundRunnable, delay);
            }
        } catch (Exception e) {
            Log.e(TAG, "playSound error " + e.getMessage());
        }
    }

}
