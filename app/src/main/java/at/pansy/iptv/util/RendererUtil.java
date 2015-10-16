package at.pansy.iptv.util;

import android.content.Context;
;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by notz.
 */
public class RendererUtil {

    public static String processUrlParameters(String url, HashMap<String, String> httpHeaders) {
        String[] parameters = url.split("\\|");
        for (int i = 1; i < parameters.length; i++) {
            String[] pair = parameters[i].split("=", 2);
            if (pair.length == 2) {
                httpHeaders.put(pair[0], pair[1]);
            }
        }

        return parameters[0];
    }

    public static DefaultUriDataSource createDefaultUriDataSource(Context context, String userAgent,
                                                        HashMap<String, String> httpHeaders) {

        DefaultHttpDataSource httpDataSource = new DefaultHttpDataSource(userAgent, null, null,
                DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, false);

        for (Map.Entry<String, String> header : httpHeaders.entrySet()) {
            httpDataSource.setRequestProperty(header.getKey(), header.getValue());
        }

        return new DefaultUriDataSource(context, null, httpDataSource);
    }
}
