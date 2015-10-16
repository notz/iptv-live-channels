package at.pansy.iptv.domain;

import android.media.tv.TvContentRating;

/**
 * Created by notz.
 */
public class PlaybackInfo {

    public static final int VIDEO_TYPE_HTTP_PROGRESSIVE = 0;
    public static final int VIDEO_TYPE_HLS = 1;
    public static final int VIDEO_TYPE_MPEG_DASH = 2;
    public static final int VIDEO_TYPE_OTHER = 3;

    public final long startTimeMs;
    public final long endTimeMs;
    public final String videoUrl;
    public final int videoType;
    public final TvContentRating[] contentRatings;

    public PlaybackInfo(long startTimeMs, long endTimeMs, String videoUrl, int videoType,
                        TvContentRating[] contentRatings) {
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.contentRatings = contentRatings;
        this.videoUrl = videoUrl;
        this.videoType = videoType;
    }
}
