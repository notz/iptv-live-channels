package at.pansy.iptv.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.accessibility.CaptioningManager;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import at.pansy.iptv.R;
import at.pansy.iptv.domain.Channel;
import at.pansy.iptv.domain.PlaybackInfo;
import at.pansy.iptv.player.TvInputPlayer;
import at.pansy.iptv.util.SyncUtil;
import at.pansy.iptv.util.TvContractUtil;

/**
 * Created by notz.
 */
public class TvInputService extends android.media.tv.TvInputService {

    private static final String TAG = "TvInputService";

    private HandlerThread handlerThread;
    private Handler dbHandler;

    private List<TvInputSession> sessions;
    private CaptioningManager captioningManager;

    private final BroadcastReceiver parentalControlsBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (sessions != null) {
                for (TvInputSession session : sessions) {
                    session.checkContentBlockNeeded();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread(getClass().getSimpleName());
        handlerThread.start();
        dbHandler = new Handler(handlerThread.getLooper());
        captioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);

        setTheme(android.R.style.Theme_Holo_Light_NoActionBar);

        sessions = new ArrayList<>();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TvInputManager.ACTION_BLOCKED_RATINGS_CHANGED);
        intentFilter.addAction(TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED);
        registerReceiver(parentalControlsBroadcastReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(parentalControlsBroadcastReceiver);
        handlerThread.quit();
        handlerThread = null;
        dbHandler = null;
    }

    @Override
    public final Session onCreateSession(String inputId) {
        TvInputSession session = new TvInputSession(this, inputId);
        session.setOverlayViewEnabled(true);
        sessions.add(session);
        return session;
    }

    class TvInputSession extends android.media.tv.TvInputService.Session implements Handler.Callback {

        private static final int MSG_PLAY_PROGRAM = 1000;

        private final Context context;
        private final TvInputManager tvInputManager;
        protected TvInputPlayer player;
        private Surface surface;
        private float volume;
        private boolean captionEnabled;
        private PlaybackInfo currentPlaybackInfo;
        private TvContentRating lastBlockedRating;
        private TvContentRating currentContentRating;
        private String celectedSubtitleTrackId;
        private SubtitleLayout subtitleLayout;
        private boolean epgSyncRequested;
        private final Set<TvContentRating> unblockedRatingSet = new HashSet<>();
        private final Handler handler;

        private final TvInputPlayer.Callback playerCallback = new TvInputPlayer.Callback() {

            private boolean firstFrameDrawn;

            @Override
            public void onPrepared() {
                firstFrameDrawn = false;
                List<TvTrackInfo> tracks = new ArrayList<>();
                Collections.addAll(tracks, player.getTracks(TvTrackInfo.TYPE_AUDIO));
                Collections.addAll(tracks, player.getTracks(TvTrackInfo.TYPE_VIDEO));
                Collections.addAll(tracks, player.getTracks(TvTrackInfo.TYPE_SUBTITLE));

                notifyTracksChanged(tracks);
                notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, player.getSelectedTrack(
                        TvTrackInfo.TYPE_AUDIO));
                notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, player.getSelectedTrack(
                        TvTrackInfo.TYPE_VIDEO));
                notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, player.getSelectedTrack(
                        TvTrackInfo.TYPE_SUBTITLE));
            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playWhenReady && playbackState == ExoPlayer.STATE_BUFFERING) {
                    if (firstFrameDrawn) {
                        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
                    }
                } else if (playWhenReady && playbackState == ExoPlayer.STATE_READY) {
                    notifyVideoAvailable();
                }
            }

            @Override
            public void onPlayWhenReadyCommitted() {
                // Do nothing.
            }

            @Override
            public void onPlayerError(ExoPlaybackException e) {
                // Do nothing.
            }

            @Override
            public void onDrawnToSurface(Surface surface) {
                firstFrameDrawn = true;
                notifyVideoAvailable();
            }

            @Override
            public void onCues(List<Cue> cues) {
                if (subtitleLayout != null) {
                    if (cues.isEmpty()) {
                        subtitleLayout.setVisibility(View.INVISIBLE);
                    } else {
                        subtitleLayout.setVisibility(View.VISIBLE);
                        subtitleLayout.setCues(cues);
                    }
                }
            }
        };

        private PlayCurrentProgramRunnable playCurrentProgramRunnable;
        private String inputId;

        protected TvInputSession(Context context, String inputId) {
            super(context);
            this.context = context;
            this.inputId = inputId;

            tvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            lastBlockedRating = null;
            captionEnabled = captioningManager.isEnabled();
            handler = new Handler(this);
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == MSG_PLAY_PROGRAM) {
                playProgram((PlaybackInfo) msg.obj);
                return true;
            }
            return false;
        }

        @Override
        public void onRelease() {
            if (dbHandler != null) {
                dbHandler.removeCallbacks(playCurrentProgramRunnable);
            }
            releasePlayer();
            sessions.remove(this);
        }

        @Override
        public View onCreateOverlayView() {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.overlay_view, null);
            subtitleLayout = (SubtitleLayout) view.findViewById(R.id.subtitles);

            // Configure the subtitle view.
            CaptionStyleCompat captionStyle;
            captionStyle = CaptionStyleCompat.createFromCaptionStyle(
                    captioningManager.getUserStyle());
            subtitleLayout.setStyle(captionStyle);
            subtitleLayout.setFractionalTextSize(captioningManager.getFontScale());
            return view;
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            if (player != null) {
                player.setSurface(surface);
            }
            this.surface = surface;
            return true;
        }

        @Override
        public void onSetStreamVolume(float volume) {
            if (player != null) {
                player.setVolume(volume);
            }
            this.volume = volume;
        }

        private boolean playProgram(PlaybackInfo info) {
            releasePlayer();

            currentPlaybackInfo = info;
            currentContentRating = (info.contentRatings == null || info.contentRatings.length == 0)
                    ? null : info.contentRatings[0];
            player = new TvInputPlayer();
            player.addCallback(playerCallback);
            player.prepare(TvInputService.this, Uri.parse(info.videoUrl), info.videoType);
            player.setSurface(surface);
            player.setVolume(volume);

            long nowMs = System.currentTimeMillis();
            int seekPosMs = (int) (nowMs - info.startTimeMs);
            if (seekPosMs > 0) {
                player.seekTo(seekPosMs);
            }
            player.setPlayWhenReady(true);

            checkContentBlockNeeded();
            dbHandler.postDelayed(playCurrentProgramRunnable, info.endTimeMs - nowMs + 1000);
            return true;
        }

        @Override
        public boolean onTune(Uri channelUri) {
            if (subtitleLayout != null) {
                subtitleLayout.setVisibility(View.INVISIBLE);
            }
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
            unblockedRatingSet.clear();

            dbHandler.removeCallbacks(playCurrentProgramRunnable);
            playCurrentProgramRunnable = new PlayCurrentProgramRunnable(channelUri);
            dbHandler.post(playCurrentProgramRunnable);
            return true;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            captionEnabled = enabled;
            if (player != null) {
                if (enabled) {
                    if (celectedSubtitleTrackId != null) {
                        player.selectTrack(TvTrackInfo.TYPE_SUBTITLE, celectedSubtitleTrackId);
                    }
                } else {
                    player.selectTrack(TvTrackInfo.TYPE_SUBTITLE, null);
                }
            }
        }

        @Override
        public boolean onSelectTrack(int type, String trackId) {
            if (player != null) {
                if (type == TvTrackInfo.TYPE_SUBTITLE) {
                    if (!captionEnabled && trackId != null) {
                        return false;
                    }
                    celectedSubtitleTrackId = trackId;
                    if (trackId == null) {
                        subtitleLayout.setVisibility(View.INVISIBLE);
                    }
                }
                if (player.selectTrack(type, trackId)) {
                    notifyTrackSelected(type, trackId);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onUnblockContent(TvContentRating rating) {
            if (rating != null) {
                unblockContent(rating);
            }
        }

        private void releasePlayer() {
            if (player != null) {
                player.removeCallback(playerCallback);
                player.setSurface(null);
                player.stop();
                player.release();
                player = null;
            }
        }

        private void checkContentBlockNeeded() {
            if (currentContentRating == null || !tvInputManager.isParentalControlsEnabled()
                    || !tvInputManager.isRatingBlocked(currentContentRating)
                    || unblockedRatingSet.contains(currentContentRating)) {
                // Content rating is changed so we don't need to block anymore.
                // Unblock content here explicitly to resume playback.
                unblockContent(null);
                return;
            }

            lastBlockedRating = currentContentRating;
            if (player != null) {
                // Children restricted content might be blocked by TV app as well,
                // but TIS should do its best not to show any single frame of blocked content.
                releasePlayer();
            }

            notifyContentBlocked(currentContentRating);
        }

        private void unblockContent(TvContentRating rating) {
            // TIS should unblock content only if unblock request is legitimate.
            if (rating == null || lastBlockedRating == null
                    || rating.equals(lastBlockedRating)) {
                lastBlockedRating = null;
                if (rating != null) {
                    unblockedRatingSet.add(rating);
                }
                if (player == null && currentPlaybackInfo != null) {
                    playProgram(currentPlaybackInfo);
                }
                notifyContentAllowed();
            }
        }

        private class PlayCurrentProgramRunnable implements Runnable {

            private static final int RETRY_DELAY_MS = 2000;
            private final Uri mChannelUri;

            public PlayCurrentProgramRunnable(Uri channelUri) {
                mChannelUri = channelUri;
            }

            @Override
            public void run() {
                long nowMs = System.currentTimeMillis();
                List<PlaybackInfo> programs = TvContractUtil.getProgramPlaybackInfo(
                        context.getContentResolver(), mChannelUri, nowMs, nowMs + 1, 1);
                if (programs.isEmpty()) {
                    Log.w(TAG, "Failed to get program info for " + mChannelUri + ". Retry in " +
                            RETRY_DELAY_MS + "ms.");
                    if (!epgSyncRequested) {
                        SyncUtil.requestSync(inputId, true);
                        epgSyncRequested = true;
                    }

                    String url = null;

                    Channel channel = TvContractUtil.getChannel(context.getContentResolver(), mChannelUri);
                    if (channel != null) {
                        url = channel.getInternalProviderData();
                    }
                    PlaybackInfo playbackInfo = new PlaybackInfo(nowMs, nowMs + 3600 * 1000l,
                            url, 1, new TvContentRating[] {});
                    programs.add(playbackInfo);
                }

                handler.removeMessages(MSG_PLAY_PROGRAM);
                handler.obtainMessage(MSG_PLAY_PROGRAM, programs.get(0)).sendToTarget();
            }
        }
    }
}

