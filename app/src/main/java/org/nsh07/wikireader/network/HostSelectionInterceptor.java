
/*
  Code taken from https://gist.github.com/swankjesse/8571a8207a5815cca1fb
*/

package org.nsh07.wikireader.network;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;

/**
 * An interceptor that allows runtime changes to the URL hostname.
 */
public final class HostSelectionInterceptor implements Interceptor {
    private volatile String host;

    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    /** The Wikipedia language the next requests go to ("ja" for "ja.wikipedia.org"), or null. */
    public String getLang() {
        String h = host;
        if (h == null) return null;
        int dot = h.indexOf('.');
        return dot > 0 ? h.substring(0, dot) : null;
    }

    @NonNull
    @Override
    public okhttp3.Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String host = this.host;
        // Wiktionary and Wikimedia Commons are separate projects. Rewriting their host to the
        // selected Wikipedia language made dictionary/Commons requests hit the wrong wiki.
        String requestedHost = request.url().host();
        boolean otherWikimediaProject = requestedHost != null &&
                (requestedHost.endsWith(".wiktionary.org") ||
                 requestedHost.endsWith(".wikimedia.org") ||
                 requestedHost.endsWith(".wikidata.org"));
        if (host != null && !otherWikimediaProject) {
            HttpUrl newUrl = request.url().newBuilder()
                    .host(host)
                    .build();
            request = request.newBuilder()
                    .url(newUrl)
                    .build();
        }
        return chain.proceed(request);
    }
}