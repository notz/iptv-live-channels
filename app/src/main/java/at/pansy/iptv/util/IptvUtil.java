package at.pansy.iptv.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import at.pansy.iptv.xmltv.XmlTvParser;

/**
 * Static helper methods for fetching the channel feed.
 */
public class IptvUtil {

    public static final int FORMAT_XMLTV = 0;
    public static final int FORMAT_M3U = 1;

    private static final String TAG = "IptvUtil";
    private static HashMap<String, XmlTvParser.TvListing> sampleTvListings = new HashMap<>();

    private static final int URLCONNECTION_CONNECTION_TIMEOUT_MS = 3000;  // 3 sec
    private static final int URLCONNECTION_READ_TIMEOUT_MS = 10000;  // 10 sec

    private IptvUtil() {
    }

    public static XmlTvParser.TvListing getTvListings(Context context, String url, int format) {

        if (sampleTvListings.containsKey(url)) {
            return sampleTvListings.get(url);
        }

        Uri catalogUri =
                Uri.parse(url).normalizeScheme();

        XmlTvParser.TvListing sampleTvListing = null;
        try {
            InputStream inputStream = getInputStream(context, catalogUri);
            if (url.endsWith(".gz")) {
                inputStream = new GZIPInputStream(inputStream);
            }
            if (format == FORMAT_M3U) {
                sampleTvListing = parse(inputStream);
            } else {
                sampleTvListing = XmlTvParser.parse(inputStream);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error in fetching " + catalogUri, e);
        }
        if (sampleTvListing != null) {
            sampleTvListings.put(url, sampleTvListing);
        }
        return sampleTvListing;
    }

    public static InputStream getInputStream(Context context, Uri uri) throws IOException {
        InputStream inputStream;
        if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())
                || ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())
                || ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            inputStream = context.getContentResolver().openInputStream(uri);
        } else {
            URLConnection urlConnection = new URL(uri.toString()).openConnection();
            urlConnection.setConnectTimeout(URLCONNECTION_CONNECTION_TIMEOUT_MS);
            urlConnection.setReadTimeout(URLCONNECTION_READ_TIMEOUT_MS);
            inputStream = urlConnection.getInputStream();
        }
        return new BufferedInputStream(inputStream);
    }

    private static XmlTvParser.TvListing parse(InputStream inputStream) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        List<XmlTvParser.XmlTvChannel> channels = new ArrayList<>();
        List<XmlTvParser.XmlTvProgram> programs = new ArrayList<>();
        Map<Integer, Integer> channelMap = new HashMap<>();

        while ((line = in.readLine()) != null) {
            if (line.startsWith("#EXTINF:")) {
                // #EXTINF:0051 tvg-id="blizz.de" group-title="DE Spartensender" tvg-logo="897815.png", [COLOR orangered]blizz TV HD[/COLOR]

                String id = null;
                String displayName = null;
                String displayNumber = null;
                int originalNetworkId = 0;
                XmlTvParser.XmlTvIcon icon = null;

                String[] parts = line.split(", ", 2);
                if (parts.length == 2) {
                    for (String part : parts[0].split(" ")) {
                        if (part.startsWith("#EXTINF:")) {
                            displayNumber = part.substring(8).replaceAll("^0+", "");
                            originalNetworkId = Integer.parseInt(displayNumber);
                        } else if (part.startsWith("tvg-id=")) {
                            int end = part.indexOf("\"", 8);
                            if (end > 8) {
                                id = part.substring(8, end);
                            }
                        } else if (part.startsWith("tvg-logo=")) {
                            int end = part.indexOf("\"", 10);
                            if (end > 10) {
                                icon = new XmlTvParser.XmlTvIcon("http://logo.iptv.ink/"
                                        + part.substring(10, end));
                            }
                        }
                    }
                    displayName = parts[1].replaceAll("\\[\\/?COLOR[^\\]]*\\]", "");
                }

                if (originalNetworkId != 0 && displayName != null) {
                    XmlTvParser.XmlTvChannel channel =
                            new XmlTvParser.XmlTvChannel(id, displayName, displayNumber, icon,
                                originalNetworkId, 0, 0, false);
                    if (channelMap.containsKey(originalNetworkId)) {
                        channels.set(channelMap.get(originalNetworkId), channel);
                    } else {
                        channelMap.put(originalNetworkId, channels.size());
                        channels.add(channel);
                    }
                }
            } else if (line.startsWith("http") && channels.size() > 0) {
                channels.get(channels.size()-1).url = line;
            }
        }
        return new XmlTvParser.TvListing(channels, programs);
    }
}
