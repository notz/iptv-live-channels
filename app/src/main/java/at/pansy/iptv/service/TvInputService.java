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

import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.TimeRange;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleLayout;
import com.google.android.exoplayer.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import at.pansy.iptv.R;
import at.pansy.iptv.domain.Channel;
import at.pansy.iptv.domain.PlaybackInfo;
import at.pansy.iptv.player.DashRendererBuilder;
import at.pansy.iptv.player.HlsRendererBuilder;
import at.pansy.iptv.player.TvInputPlayer;
import at.pansy.iptv.util.RendererUtil;
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

    class TvInputSession extends Session implements Handler.Callback {

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
        private String selectedSubtitleTrackId;
        private SubtitleLayout subtitleLayout;
        private boolean epgSyncRequested;
        private final Set<TvContentRating> unblockedRatingSet = new HashSet<>();
        private final Handler handler;

        private final TvInputPlayer.Listener playerListener = new TvInputPlayer.Listener() {

            private boolean firstFrameDrawn;

            @Override
            public void onPrepared() {
                firstFrameDrawn = false;

                Log.d(TAG, "on prepared");


                List<TvTrackInfo> tracks = new ArrayList<>();
                String audioSelected = null;
                String videoSelected = null;
                String subtitleSelected = null;

                for (int i = 0; i < player.getTrackCount(TvInputPlayer.TYPE_AUDIO); i++) {
                    MediaFormat format = player.getTrackFormat(TvInputPlayer.TYPE_AUDIO, i);
                    Log.d(TAG, "audio track - " + format.channelCount + ", " + format.sampleRate);
                    tracks.add(new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO,
                            Integer.toString(i))
                            .setAudioChannelCount(format.channelCount)
                            .setAudioSampleRate(format.sampleRate)
                            .setLanguage(format.language)
                            .build());
                    if (player.getSelectedTrack(TvInputPlayer.TYPE_AUDIO) == i) {
                        audioSelected = Integer.toString(i);
                    }
                }

                for (int i = 0; i < player.getTrackCount(TvInputPlayer.TYPE_VIDEO); i++) {
                    MediaFormat format = player.getTrackFormat(TvInputPlayer.TYPE_VIDEO, i);
                    Log.d(TAG, "video track - " + format.bitrate + ", " + format.mimeType);
                    tracks.add(new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO,
                            Integer.toString(i))
                            .setVideoWidth(format.width)
                            .setVideoHeight(format.height)
                                    //.setVideoFrameRate(format.)
                            .build());
                    if (player.getSelectedTrack(TvInputPlayer.TYPE_VIDEO) == i) {
                        videoSelected = Integer.toString(i);
                    }
                }

                for (int i = 0; i < player.getTrackCount(TvInputPlayer.TYPE_TEXT); i++) {
                    tracks.add(new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE,
                            Integer.toString(i))
                            .build());
                    if (player.getSelectedTrack(TvInputPlayer.TYPE_TEXT) == i) {
                        subtitleSelected = Integer.toString(i);
                    }
                }

                notifyTracksChanged(tracks);
                notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, audioSelected);
                notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, videoSelected);
                notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, subtitleSelected);
            }

            @Override
            public void onStateChanged(boolean playWhenReady, int playbackState) {
                Log.d(TAG, "onStateChanged - " + playWhenReady + ", " + playbackState);
                if (playWhenReady && playbackState == ExoPlayer.STATE_BUFFERING) {
                    if (firstFrameDrawn) {
                        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
                    }
                } else if (playWhenReady && playbackState == ExoPlayer.STATE_READY) {
                    notifyVideoAvailable();
                }
            }

            @Override
            public void onError(Exception e) { }

            @Override
            public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {

            }

            @Override
            public void onDrawnToSurface(Surface surface) {
                Log.d(TAG, "onDrawnToSurface - " + surface);
                firstFrameDrawn = true;
                notifyVideoAvailable();
            }
        };

        TvInputPlayer.CaptionListener captionListener = new TvInputPlayer.CaptionListener() {
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
            subtitleLayout.setFractionalTextSize(SubtitleLayout.DEFAULT_TEXT_SIZE_FRACTION
                    * captioningManager.getFontScale());
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

            Log.d(TAG, "playProgram - " + info.videoUrl);

            String userAgent = Util.getUserAgent(context, "IptvLiveChannels");
            HashMap<String, String> httpHeaders = new HashMap<>();
            String url = RendererUtil.processUrlParameters(info.videoUrl, httpHeaders);

            TvInputPlayer.RendererBuilder rendererBuilder;
            if (info.videoType == PlaybackInfo.VIDEO_TYPE_HLS) {
                rendererBuilder = new HlsRendererBuilder(context, userAgent, url, httpHeaders);
            } else {
                rendererBuilder = new DashRendererBuilder(context, userAgent, info.videoUrl, null);
            }

            player = new TvInputPlayer(rendererBuilder);
            player.addListener(playerListener);
            player.setCaptionListener(captionListener);
            player.setInfoListener(new TvInputPlayer.InfoListener() {
                @Override
                public void onVideoFormatEnabled(Format format, int trigger, long mediaTimeMs) {
                    Log.d(TAG, "onVideoFormatEnabled - " + format.codecs);
                }

                @Override
                public void onAudioFormatEnabled(Format format, int trigger, long mediaTimeMs) {
                    Log.d(TAG, "onAudiFormatEnabled - " + format.codecs);
                }

                @Override
                public void onDroppedFrames(int count, long elapsed) {
                    Log.d(TAG, "onDroppedFrames - " + count);
                }

                @Override
                public void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate) {
                    Log.d(TAG, "onBandwidthSample - " + bitrateEstimate);
                }

                @Override
                public void onLoadStarted(int sourceId, long length, int type, int trigger, Format format, long mediaStartTimeMs, long mediaEndTimeMs) {
                }

                @Override
                public void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs) {
                }

                @Override
                public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {
                    Log.d(TAG, "onDecoderInitialized - " + decoderName);
                }

                @Override
                public void onAvailableRangeChanged(TimeRange availableRange) {
                    Log.d(TAG, "onAvailableRangeChanged");

                }
            });

            long nowMs = System.currentTimeMillis();
            int seekPosMs = (int) (nowMs - info.startTimeMs);
            if (seekPosMs > 0) {
                player.seekTo(seekPosMs);
            }

            player.prepare();
            player.setSurface(surface);
            player.setVolume(volume);
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
                    if (selectedSubtitleTrackId != null) {
                        player.setSelectedTrack(TvTrackInfo.TYPE_SUBTITLE,
                                Integer.parseInt(selectedSubtitleTrackId));
                    }
                } else {
                    player.setSelectedTrack(TvTrackInfo.TYPE_SUBTITLE, -1);
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
                    selectedSubtitleTrackId = trackId;
                    if (trackId == null) {
                        subtitleLayout.setVisibility(View.INVISIBLE);
                    }
                }
                if (trackId != null) {
                    player.setSelectedTrack(type, Integer.parseInt(trackId));
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
                player.setCaptionListener(null);
                player.removeListener(playerListener);
                player.setSurface(null);
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
                            url, PlaybackInfo.VIDEO_TYPE_HLS, new TvContentRating[] {});
                    programs.add(playbackInfo);
                }

                handler.removeMessages(MSG_PLAY_PROGRAM);
                handler.obtainMessage(MSG_PLAY_PROGRAM, programs.get(0)).sendToTarget();
            }
        }
    }
}

