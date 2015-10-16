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
import android.media.tv.TvContract;
import android.text.TextUtils;

import java.util.Objects;

/**
 * A convenience class to create and insert program information into the database.
 */
public final class Channel implements Comparable<Channel> {
    private static final long INVALID_LONG_VALUE = -1;

    private long channelId;
    private String displayName;
    private String displayNumber;
    private String internalProviderData;

    private Channel() {
        channelId = INVALID_LONG_VALUE;
    }

    public long getChannelId() {
        return channelId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayNumber() {
        return displayNumber;
    }

    public String getInternalProviderData() {
        return internalProviderData;
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId, displayName, displayNumber, internalProviderData);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Channel)) {
            return false;
        }
        Channel channel = (Channel) other;
        return channelId == channel.channelId
                && Objects.equals(displayName, channel.displayName)
                && Objects.equals(displayNumber, channel.displayNumber)
                && Objects.equals(internalProviderData, channel.internalProviderData);
    }

    @Override
    public int compareTo(Channel other) {
        return Long.compare(channelId, other.channelId);
    }

    @Override
    public String toString() {
        return "Channel{"
                + "channelId=" + channelId
                + ", displayName=" + displayName
                + ", displayNumber=" + displayNumber
                + ", internalProviderData=" + internalProviderData
                + "}";
    }

    public void copyFrom(Channel other) {
        if (this == other) {
            return;
        }

        channelId = other.channelId;
        displayName = other.displayName;
        displayNumber = other.displayNumber;
        internalProviderData = other.internalProviderData;
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        if (!TextUtils.isEmpty(displayName)) {
            values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, displayName);
        } else {
            values.putNull(TvContract.Channels.COLUMN_DISPLAY_NAME);
        }
        if (!TextUtils.isEmpty(displayNumber)) {
            values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, displayNumber);
        } else {
            values.putNull(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
        }
        if (!TextUtils.isEmpty(internalProviderData)) {
            values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, internalProviderData);
        } else {
            values.putNull(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA);
        }
        return values;
    }

    public static Channel fromCursor(Cursor cursor) {
        Builder builder = new Builder();
        int index = cursor.getColumnIndex(TvContract.Channels._ID);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setChannelId(cursor.getLong(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setDisplayName(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setDisplayNumber(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setInternalProviderData(cursor.getString(index));
        }
        return builder.build();
    }

    public static final class Builder {
        private final Channel channel;

        public Builder() {
            channel = new Channel();
        }

        public Builder(Channel other) {
            channel = new Channel();
            channel.copyFrom(other);
        }

        public Builder setChannelId(long channelId) {
            channel.channelId = channelId;
            return this;
        }

        public Builder setDisplayName(String displayName) {
            channel.displayName = displayName;
            return this;
        }

        public Builder setDisplayNumber(String displayNumber) {
            channel.displayNumber = displayNumber;
            return this;
        }

        public Builder setInternalProviderData(String data) {
            channel.internalProviderData = data;
            return this;
        }

        public Channel build() {
            return channel;
        }
    }
}
