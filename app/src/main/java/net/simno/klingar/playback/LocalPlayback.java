/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2017 Simon Norberg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.simno.klingar.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.session.PlaybackStateCompat.State;
import android.support.v4.util.Pair;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.DynamicConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import net.simno.klingar.R;
import net.simno.klingar.data.model.Track;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.reactivex.Flowable;
import okhttp3.Call;
import timber.log.Timber;

import static android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static com.google.android.exoplayer2.C.CONTENT_TYPE_MUSIC;
import static com.google.android.exoplayer2.C.TIME_UNSET;
import static com.google.android.exoplayer2.C.USAGE_MEDIA;

/**
 * A class that implements local media playback using
 * {@link com.google.android.exoplayer2.ExoPlayer}
 */
class LocalPlayback implements Playback, Player.EventListener,
    AudioManager.OnAudioFocusChangeListener {

  private static final float VOLUME_DUCK = 0.2f;
  private static final float VOLUME_NORMAL = 1.0f;
  private static final int AUDIO_NO_FOCUS_NO_DUCK = 0;
  private static final int AUDIO_NO_FOCUS_CAN_DUCK = 1;
  private static final int AUDIO_FOCUSED = 2;

  private final IntentFilter audioNoisyIntentFilter = new IntentFilter(ACTION_AUDIO_BECOMING_NOISY);
  private final Context context;
  private final WifiManager.WifiLock wifiLock;
  private final AudioManager audioManager;
  private final MusicController musicController;
  private final DefaultDataSourceFactory dataSourceFactory;
  private final DefaultExtractorsFactory extractorsFactory;
  private DynamicConcatenatingMediaSource dynamicMediaSource;
  private SimpleExoPlayer exoPlayer;
  private Callback callback;
  private int audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
  private boolean playOnFocusGain;
  private boolean audioNoisyReceiverRegistered;
  private Track currentTrack;
  private List<Track> currentQueue;
  // Whether to return STATE_NONE or STATE_STOPPED when exoPlayer is null;
  private boolean exoPlayerNullIsStopped;

  private final BroadcastReceiver audioNoisyReceiver = new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) {
      if (ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
        if (isPlaying()) {
          musicController.pause();
        }
      }
    }
  };

  LocalPlayback(Context context, MusicController musicController, AudioManager audioManager,
                WifiManager wifiManager, Call.Factory callFactory) {
    this.context = context;
    this.musicController = musicController;
    this.audioManager = audioManager;
    this.wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "klingar_lock");
    String agent = Util.getUserAgent(context, context.getResources().getString(R.string.app_name));
    this.dataSourceFactory = new DefaultDataSourceFactory(context, null,
        new OkHttpDataSourceFactory(callFactory, agent, null));
    this.extractorsFactory = new DefaultExtractorsFactory();
  }

  private static String getExoPlayerState(int state) {
    switch (state) {
      case Player.STATE_IDLE:
        return "STATE_IDLE";
      case Player.STATE_BUFFERING:
        return "STATE_BUFFERING";
      case Player.STATE_READY:
        return "STATE_READY";
      case Player.STATE_ENDED:
        return "STATE_ENDED";
      default:
        return "UNKNOWN";
    }
  }

  @Override public void start() {
  }

  @Override public void stop(boolean notifyListeners) {
    giveUpAudioFocus();
    unregisterAudioNoisyReceiver();
    releaseResources(true);
  }

  @Override @State public int getState() {
    if (exoPlayer == null) {
      return exoPlayerNullIsStopped ? PlaybackStateCompat.STATE_STOPPED
          : PlaybackStateCompat.STATE_NONE;
    }
    switch (exoPlayer.getPlaybackState()) {
      case Player.STATE_IDLE:
        return PlaybackStateCompat.STATE_PAUSED;
      case Player.STATE_BUFFERING:
        return PlaybackStateCompat.STATE_BUFFERING;
      case Player.STATE_READY:
        return exoPlayer.getPlayWhenReady() ? PlaybackStateCompat.STATE_PLAYING
            : PlaybackStateCompat.STATE_PAUSED;
      case Player.STATE_ENDED:
        return PlaybackStateCompat.STATE_PAUSED;
      default:
        return PlaybackStateCompat.STATE_NONE;
    }
  }

  @Override public boolean isConnected() {
    return true;
  }

  @Override public boolean isPlaying() {
    return playOnFocusGain || (exoPlayer != null && exoPlayer.getPlayWhenReady());
  }

  @Override public int getCurrentStreamPosition() {
    if (exoPlayer != null) {
      int pos = (int) exoPlayer.getCurrentPosition();
      Timber.d("current playback pos = " + pos);
      return pos;
    }

    return 0;
  }

  @Override public void updateLastKnownStreamPosition() {
    // Nothing to do. Position maintained by ExoPlayer.
  }

  /**
   * @param queue to play
   */
  @Override
  public void play(Pair<List<Track>, Integer> queue) {
    playOnFocusGain = true;
    setCurrentQueue(queue);
  }

  @Override public void play(Track track) {
    Timber.d("play %s", track);
    playOnFocusGain = true;
    tryToGetAudioFocus();
    registerAudioNoisyReceiver();
    boolean mediaHasChanged = !track.equals(currentTrack);
    if (mediaHasChanged) {
      currentTrack = track;
    }

    if (mediaHasChanged || exoPlayer == null) {
      releaseResources(false); // release everything except the player

      if (exoPlayer == null) {
        exoPlayer = ExoPlayerFactory.newSimpleInstance(context, new DefaultTrackSelector());
        exoPlayer.addListener(this);
      }
      AudioAttributes audioAttributes = new AudioAttributes.Builder()
          .setContentType(CONTENT_TYPE_MUSIC)
          .setUsage(USAGE_MEDIA)
          .build();
      exoPlayer.setAudioAttributes(audioAttributes);

      Uri uri = Uri.parse(track.source());
      ExtractorMediaSource source = new ExtractorMediaSource(uri, dataSourceFactory,
          extractorsFactory, null, null);
      source = new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
      exoPlayer.prepare(source);

      // If we are streaming from the internet, we want to hold a Wifi lock, which prevents the
      // Wifi radio from going to sleep while the song is playing.
      wifiLock.acquire();
    }

    configurePlayerState();
  }

  @Override public void pause() {
    Timber.d("pause");

    // Pause player and cancel the 'foreground service' state.
    if (exoPlayer != null) {
      exoPlayer.setPlayWhenReady(false);
    }
    // While paused, retain the player instance, but give up audio focus.
    releaseResources(false);
    unregisterAudioNoisyReceiver();
  }

  @Override
  public void resume() {
    playOnFocusGain = true;
    configurePlayerState();
  }

  @Override public void seekTo(int position) {
    Timber.d("seekTo %s", position);
    if (exoPlayer != null) {
      registerAudioNoisyReceiver();
      long duration = exoPlayer.getDuration();
      long seekPosition = duration == TIME_UNSET ? 0 : Math.min(Math.max(0, position), duration);
      exoPlayer.seekTo(seekPosition);
    }
  }

  @Override public Track getCurrentTrack() {
    return currentTrack;
  }

  @Override public void setCurrentTrack(Track track) {
    this.currentTrack = track;
  }

  @Override
  public void setCurrentQueue(Pair<List<Track>, Integer> queue) {
    // playOnFocusGain = false;
    tryToGetAudioFocus();
    registerAudioNoisyReceiver();

    List<Track> trackList = queue.first;
    Integer trackPos = queue.second;

    Timber.d("play %s", trackList.get(trackPos));

    boolean trackHasChanged = !trackList.get(trackPos).equals(currentTrack);
    if (trackHasChanged) {
      currentTrack = trackList.get(trackPos);
    }

    // boolean queueHasChanged = !trackList.equals(currentQueue);
    if (trackHasChanged || exoPlayer == null) {
      currentQueue = trackList;
      releaseResources(false); // release everything except the player

      if (exoPlayer == null) {
        exoPlayer = ExoPlayerFactory.newSimpleInstance(context, new DefaultTrackSelector());
        exoPlayer.addListener(this);
      }
      AudioAttributes audioAttributes = new AudioAttributes.Builder()
              .setContentType(CONTENT_TYPE_MUSIC)
              .setUsage(USAGE_MEDIA)
              .build();
      exoPlayer.setAudioAttributes(audioAttributes);

      dynamicMediaSource = new DynamicConcatenatingMediaSource();

      List<Uri> uris = new ArrayList<>();
      for (Track t : trackList.subList(trackPos, trackList.size())) {
        uris.add(Uri.parse(t.source()));
      }

      for (Uri uri : uris) {
        ExtractorMediaSource source = new ExtractorMediaSource.Factory(dataSourceFactory)
                .createMediaSource(uri);
        dynamicMediaSource.addMediaSource(source);
      }

      exoPlayer.prepare(dynamicMediaSource);

      // If we are streaming from the internet, we want to hold a Wifi lock, which prevents the
      // Wifi radio from going to sleep while the song is playing.
      wifiLock.acquire();
    }

    configurePlayerState();
  }

  @Override public void setCallback(Callback callback) {
    this.callback = callback;
  }

  @Override public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    Timber.d("onPlayerStateChanged %s playWhenReady %s", getExoPlayerState(playbackState),
        playWhenReady);

    switch (playbackState) {
      case Player.STATE_IDLE:
      case Player.STATE_BUFFERING:
      case Player.STATE_READY:
        if (callback != null) {
          callback.onPlaybackStatusChanged();
        }
        break;
      case Player.STATE_ENDED:
        if (callback != null) {
          callback.onCompletion();
        }
        break;
      default:
        break;
    }
  }

  @Override public void onRepeatModeChanged(int repeatMode) {
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

  }

  @Override public void onLoadingChanged(boolean isLoading) {
  }

  @Override public void onPlayerError(ExoPlaybackException error) {
    Timber.e(error, "Exception playing song");
    if (callback != null) {
      callback.onPlaybackStatusChanged();
    }
  }

  @Override public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
  }

  private void tryToGetAudioFocus() {
    Timber.d("tryToGetAudioFocus");
    int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN);
    android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
            .setContentType(CONTENT_TYPE_MUSIC)
            .setUsage(USAGE_MEDIA)
            .build();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      result = audioManager.requestAudioFocus(new AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
              .setOnAudioFocusChangeListener(this)
              .setAudioAttributes(audioAttributes)
              .build());
    }
    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      audioFocus = AUDIO_FOCUSED;
    } else {
      audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
    }
  }

  private void giveUpAudioFocus() {
    Timber.d("giveUpAudioFocus");
    if (audioManager.abandonAudioFocus(this) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
    }
  }

  private void configurePlayerState() {
    Timber.d("configurePlayerState audioFocus %s", audioFocus);

    if (audioFocus == AUDIO_NO_FOCUS_NO_DUCK) {
      // We don't have audio focus and can't duck, so we have to pause
      pause();
    } else {
      registerAudioNoisyReceiver();

      if (audioFocus == AUDIO_NO_FOCUS_CAN_DUCK) {
        // We're permitted to play, but only if we 'duck', ie: play softly
        exoPlayer.setVolume(VOLUME_DUCK);
      } else {
        exoPlayer.setVolume(VOLUME_NORMAL);
      }

      // If we were playing when we lost focus, we need to resume playing.
      if (playOnFocusGain) {
        exoPlayer.setPlayWhenReady(true);
        playOnFocusGain = false;
      }
    }
  }

  @Override public void onAudioFocusChange(int focusChange) {
    Timber.d("onAudioFocusChange focusChange %s", focusChange);

    switch (focusChange) {
      case AudioManager.AUDIOFOCUS_GAIN:
        audioFocus = AUDIO_FOCUSED;
        break;
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
        // Audio focus was lost, but it's possible to duck (i.e.: play quietly)
        audioFocus = AUDIO_NO_FOCUS_CAN_DUCK;
        break;
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
        // Lost audio focus, but will gain it back (shortly), so note whether
        // playback should resume
        audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
        playOnFocusGain = exoPlayer != null && exoPlayer.getPlayWhenReady();
        break;
      case AudioManager.AUDIOFOCUS_LOSS:
        // Lost audio focus, probably "permanently"
        audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
        break;
      default:
        break;
    }

    if (exoPlayer != null) {
      // Update the player state based on the change
      configurePlayerState();
    }
  }

  /**
   * Called when the timeline and/or manifest has been refreshed.
   * <p>
   * Note that if the timeline has changed then a position discontinuity may also have occurred.
   * For example, the current period index may have changed as a result of periods being added or
   * removed from the timeline. This will <em>not</em> be reported via a separate call to
   * {@link #onPositionDiscontinuity(int)}.
   *
   * @param timeline The latest timeline. Never null, but may be empty.
   * @param manifest The latest manifest. May be null.
   * @param reason   The TimeLineChangeReason responsible for this timeline change.
   */
  @Override
  public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

  }

  /**
   * Called when the value of getShuffleModeEnabled() changes.
   *
   * @param shuffleModeEnabled Whether shuffling of windows is enabled.
   */
  @Override
  public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

  }

  /**
   * Called when a position discontinuity occurs without a change to the timeline. A position
   * discontinuity occurs when the current window or period index changes (as a result of playback
   * transitioning from one period in the timeline to the next), or when the playback position
   * jumps within the period currently being played (as a result of a seek being performed, or
   * when the source introduces a discontinuity internally).
   * <p>
   * When a position discontinuity occurs as a result of a change to the timeline this method is
   * <em>not</em> called. {@link #onTimelineChanged(Timeline, Object, int)} is called in this
   * case.
   *
   * @param reason The DiscontinuityReason responsible for the discontinuity.
   */
  @Override
  public void onPositionDiscontinuity(int reason) {
    if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
      // Update current track
      currentTrack = currentQueue.get(exoPlayer.getCurrentWindowIndex());

      // Notify the others we have a new track
      callback.onCompletion();
      callback.onPlaybackStatusChanged();
    }
  }

  /**
   * Called when all pending seek requests have been processed by the player. This is guaranteed
   * to happen after any necessary changes to the player state were reported to
   * {@link #onPlayerStateChanged(boolean, int)}.
   */
  @Override
  public void onSeekProcessed() {

  }

  /**
   * Releases resources used by the service for playback, which is mostly just the WiFi lock for
   * local playback. If requested, the ExoPlayer instance is also released.
   *
   * @param releasePlayer Indicates whether the player should also be released
   */
  private void releaseResources(boolean releasePlayer) {
    Timber.d("releaseResources releasePlayer %s", releasePlayer);
    // stop and release the ExoPlayer, if it's available
    if (releasePlayer && exoPlayer != null) {
      exoPlayer.release();
      exoPlayer.removeListener(this);
      exoPlayer = null;
      exoPlayerNullIsStopped = true;
      playOnFocusGain = false;
    }

    /*
    for (int i = 0; i < dynamicMediaSource.getSize(); i++) {
      dynamicMediaSource.removeMediaSource(i);
    }*/

    if (wifiLock.isHeld()) {
      wifiLock.release();
    }
  }

  private void registerAudioNoisyReceiver() {
    if (!audioNoisyReceiverRegistered) {
      context.registerReceiver(audioNoisyReceiver, audioNoisyIntentFilter);
      audioNoisyReceiverRegistered = true;
    }
  }

  private void unregisterAudioNoisyReceiver() {
    if (audioNoisyReceiverRegistered) {
      context.unregisterReceiver(audioNoisyReceiver);
      audioNoisyReceiverRegistered = false;
    }
  }
}
