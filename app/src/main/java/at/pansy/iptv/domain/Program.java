/*
 * Copyright 2015 Google Inc. All rights reserved.
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

package at.pansy.iptv.domain;

import android.content.ContentValues;
import android.database.Cursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.Objects;

import at.pansy.iptv.util.TvContractUtil;

/**
 * A convenience class to create and insert program information into the database.
 */
public final class Program implements Comparable<Program> {

    private static final long INVALID_LONG_VALUE = -1;
    private static final int INVALID_INT_VALUE = -1;

    private long programId;
    private long channelId;
    private String title;
    private String episodeTitle;
    private int seasonNumber;
    private int episodeNumber;
    private long startTimeUtcMillis;
    private long endTimeUtcMillis;
    private String description;
    private String longDescription;
    private int videoWidth;
    private int videoHeight;
    private String posterArtUri;
    private String thumbnailUri;
    private String[] canonicalGenres;
    private TvContentRating[] contentRatings;
    private String internalProviderData;

    private Program() {
        channelId = INVALID_LONG_VALUE;
        programId = INVALID_LONG_VALUE;
        seasonNumber = INVALID_INT_VALUE;
        episodeNumber = INVALID_INT_VALUE;
        startTimeUtcMillis = INVALID_LONG_VALUE;
        endTimeUtcMillis = INVALID_LONG_VALUE;
        videoWidth = INVALID_INT_VALUE;
        videoHeight = INVALID_INT_VALUE;
    }

    public long getProgramId() {
        return programId;
    }

    public long getChannelId() {
        return channelId;
    }

    public String getTitle() {
        return title;
    }

    public String getEpisodeTitle() {
        return episodeTitle;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public long getStartTimeUtcMillis() {
        return startTimeUtcMillis;
    }

    public long getEndTimeUtcMillis() {
        return endTimeUtcMillis;
    }

    public String getDescription() {
        return description;
    }

    public String getLongDescription() {
        return longDescription;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public String[] getCanonicalGenres() {
        return canonicalGenres;
    }

    public TvContentRating[] getContentRatings() {
        return contentRatings;
    }

    public String getPosterArtUri() {
        return posterArtUri;
    }

    public String getThumbnailUri() {
        return thumbnailUri;
    }

    public String getInternalProviderData() {
        return internalProviderData;
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId, startTimeUtcMillis, endTimeUtcMillis,
                title, episodeTitle, description, longDescription, videoWidth, videoHeight,
                posterArtUri, thumbnailUri, contentRatings, canonicalGenres, seasonNumber,
                episodeNumber);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Program)) {
            return false;
        }
        Program program = (Program) other;
        return channelId == program.channelId
                && startTimeUtcMillis == program.startTimeUtcMillis
                && endTimeUtcMillis == program.endTimeUtcMillis
                && Objects.equals(title, program.title)
                && Objects.equals(episodeTitle, program.episodeTitle)
                && Objects.equals(description, program.description)
                && Objects.equals(longDescription, program.longDescription)
                && videoWidth == program.videoWidth
                && videoHeight == program.videoHeight
                && Objects.equals(posterArtUri, program.posterArtUri)
                && Objects.equals(thumbnailUri, program.thumbnailUri)
                && Arrays.equals(contentRatings, program.contentRatings)
                && Arrays.equals(canonicalGenres, program.canonicalGenres)
                && seasonNumber == program.seasonNumber
                && episodeNumber == program.episodeNumber;
    }

    @Override
    public int compareTo(Program other) {
        return Long.compare(startTimeUtcMillis, other.startTimeUtcMillis);
    }

    @Override
    public String toString() {
        return "Program{"
                + "programId=" + programId
                + ", channelId=" + channelId
                + ", title=" + title
                + ", episodeTitle=" + episodeTitle
                + ", seasonNumber=" + seasonNumber
                + ", episodeNumber=" + episodeNumber
                + ", startTimeUtcSec=" + startTimeUtcMillis
                + ", endTimeUtcSec=" + endTimeUtcMillis
                + ", videoWidth=" + videoWidth
                + ", videoHeight=" + videoHeight
                + ", contentRatings=" + contentRatings
                + ", posterArtUri=" + posterArtUri
                + ", thumbnailUri=" + thumbnailUri
                + ", contentRatings=" + contentRatings
                + ", genres=" + canonicalGenres
                + "}";
    }

    public void copyFrom(Program other) {
        if (this == other) {
            return;
        }

        programId = other.programId;
        channelId = other.channelId;
        title = other.title;
        episodeTitle = other.episodeTitle;
        seasonNumber = other.seasonNumber;
        episodeNumber = other.episodeNumber;
        startTimeUtcMillis = other.startTimeUtcMillis;
        endTimeUtcMillis = other.endTimeUtcMillis;
        description = other.description;
        longDescription = other.longDescription;
        videoWidth = other.videoWidth;
        videoHeight = other.videoHeight;
        posterArtUri = other.posterArtUri;
        thumbnailUri = other.thumbnailUri;
        canonicalGenres = other.canonicalGenres;
        contentRatings = other.contentRatings;
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        if (channelId != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs.COLUMN_CHANNEL_ID, channelId);
        } else {
            values.putNull(TvContract.Programs.COLUMN_CHANNEL_ID);
        }
        if (!TextUtils.isEmpty(title)) {
            values.put(TvContract.Programs.COLUMN_TITLE, title);
        } else {
            values.putNull(TvContract.Programs.COLUMN_TITLE);
        }
        if (!TextUtils.isEmpty(episodeTitle)) {
            values.put(TvContract.Programs.COLUMN_EPISODE_TITLE, episodeTitle);
        } else {
            values.putNull(TvContract.Programs.COLUMN_EPISODE_TITLE);
        }
        if (seasonNumber != INVALID_INT_VALUE) {
            values.put(TvContract.Programs.COLUMN_SEASON_NUMBER, seasonNumber);
        } else {
            values.putNull(TvContract.Programs.COLUMN_SEASON_NUMBER);
        }
        if (episodeNumber != INVALID_INT_VALUE) {
            values.put(TvContract.Programs.COLUMN_EPISODE_NUMBER, episodeNumber);
        } else {
            values.putNull(TvContract.Programs.COLUMN_EPISODE_NUMBER);
        }
        if (!TextUtils.isEmpty(description)) {
            values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, description);
        } else {
            values.putNull(TvContract.Programs.COLUMN_SHORT_DESCRIPTION);
        }
        if (!TextUtils.isEmpty(posterArtUri)) {
            values.put(TvContract.Programs.COLUMN_POSTER_ART_URI, posterArtUri);
        } else {
            values.putNull(TvContract.Programs.COLUMN_POSTER_ART_URI);
        }
        if (!TextUtils.isEmpty(thumbnailUri)) {
            values.put(TvContract.Programs.COLUMN_THUMBNAIL_URI, thumbnailUri);
        } else {
            values.putNull(TvContract.Programs.COLUMN_THUMBNAIL_URI);
        }
        if (canonicalGenres != null && canonicalGenres.length > 0) {
            values.put(TvContract.Programs.COLUMN_CANONICAL_GENRE,
                    TvContract.Programs.Genres.encode(canonicalGenres));
        } else {
            values.putNull(TvContract.Programs.COLUMN_CANONICAL_GENRE);
        }
        if (contentRatings != null && contentRatings.length > 0) {
            values.put(TvContract.Programs.COLUMN_CONTENT_RATING,
                    TvContractUtil.contentRatingsToString(contentRatings));
        } else {
            values.putNull(TvContract.Programs.COLUMN_CONTENT_RATING);
        }
        if (startTimeUtcMillis != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, startTimeUtcMillis);
        } else {
            values.putNull(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS);
        }
        if (endTimeUtcMillis != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, endTimeUtcMillis);
        } else {
            values.putNull(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS);
        }
        if (videoWidth != INVALID_INT_VALUE) {
            values.put(TvContract.Programs.COLUMN_VIDEO_WIDTH, videoWidth);
        } else {
            values.putNull(TvContract.Programs.COLUMN_VIDEO_WIDTH);
        }
        if (videoHeight != INVALID_INT_VALUE) {
            values.put(TvContract.Programs.COLUMN_VIDEO_HEIGHT, videoHeight);
        } else {
            values.putNull(TvContract.Programs.COLUMN_VIDEO_HEIGHT);
        }
        if (!TextUtils.isEmpty(internalProviderData)) {
            values.put(TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA, internalProviderData);
        } else {
            values.putNull(TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA);
        }
        return values;
    }

    public static Program fromCursor(Cursor cursor) {
        Builder builder = new Builder();
        int index = cursor.getColumnIndex(TvContract.Programs._ID);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setProgramId(cursor.getLong(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_CHANNEL_ID);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setChannelId(cursor.getLong(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_TITLE);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setTitle(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_EPISODE_TITLE);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setEpisodeTitle(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_SEASON_NUMBER);
        if(index >= 0 && !cursor.isNull(index)) {
            builder.setSeasonNumber(cursor.getInt(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_EPISODE_NUMBER);
        if(index >= 0 && !cursor.isNull(index)) {
            builder.setEpisodeNumber(cursor.getInt(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_SHORT_DESCRIPTION);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setDescription(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_LONG_DESCRIPTION);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setLongDescription(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_POSTER_ART_URI);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setPosterArtUri(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_THUMBNAIL_URI);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setThumbnailUri(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_CANONICAL_GENRE);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setCanonicalGenres(TvContract.Programs.Genres.decode(cursor.getString(index)));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_CONTENT_RATING);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setContentRatings(TvContractUtil.stringToContentRatings(cursor.getString(
                    index)));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setStartTimeUtcMillis(cursor.getLong(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setEndTimeUtcMillis(cursor.getLong(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_VIDEO_WIDTH);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setVideoWidth((int) cursor.getLong(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_VIDEO_HEIGHT);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setVideoHeight((int) cursor.getLong(index));
        }
        index = cursor.getColumnIndex(TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setInternalProviderData(cursor.getString(index));
        }
        return builder.build();
    }

    public static final class Builder {
        private final Program mProgram;

        public Builder() {
            mProgram = new Program();
        }

        public Builder(Program other) {
            mProgram = new Program();
            mProgram.copyFrom(other);
        }

        public Builder setProgramId(long programId) {
            mProgram.programId = programId;
            return this;
        }

        public Builder setChannelId(long channelId) {
            mProgram.channelId = channelId;
            return this;
        }

        public Builder setTitle(String title) {
            mProgram.title = title;
            return this;
        }

        public Builder setEpisodeTitle(String episodeTitle) {
            mProgram.episodeTitle = episodeTitle;
            return this;
        }

        public Builder setSeasonNumber(int seasonNumber) {
            mProgram.seasonNumber = seasonNumber;
            return this;
        }

        public Builder setEpisodeNumber(int episodeNumber) {
            mProgram.episodeNumber = episodeNumber;
            return this;
        }

        public Builder setStartTimeUtcMillis(long startTimeUtcMillis) {
            mProgram.startTimeUtcMillis = startTimeUtcMillis;
            return this;
        }

        public Builder setEndTimeUtcMillis(long endTimeUtcMillis) {
            mProgram.endTimeUtcMillis = endTimeUtcMillis;
            return this;
        }

        public Builder setDescription(String description) {
            mProgram.description = description;
            return this;
        }

        public Builder setLongDescription(String longDescription) {
            mProgram.longDescription = longDescription;
            return this;
        }

        public Builder setVideoWidth(int width) {
            mProgram.videoWidth = width;
            return this;
        }

        public Builder setVideoHeight(int height) {
            mProgram.videoHeight = height;
            return this;
        }

        public Builder setContentRatings(TvContentRating[] contentRatings) {
            mProgram.contentRatings = contentRatings;
            return this;
        }

        public Builder setPosterArtUri(String posterArtUri) {
            mProgram.posterArtUri = posterArtUri;
            return this;
        }

        public Builder setThumbnailUri(String thumbnailUri) {
            mProgram.thumbnailUri = thumbnailUri;
            return this;
        }

        public Builder setCanonicalGenres(String[] genres) {
            mProgram.canonicalGenres = genres;
            return this;
        }

        public Builder setInternalProviderData(String data) {
            mProgram.internalProviderData = data;
            return this;
        }

        public Program build() {
            return mProgram;
        }
    }
}
