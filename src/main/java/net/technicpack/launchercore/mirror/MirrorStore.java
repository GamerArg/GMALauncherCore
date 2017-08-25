package net.technicpack.launchercore.mirror;

import net.technicpack.launchercore.exception.DownloadException;
import net.technicpack.launchercore.install.user.UserModel;
import net.technicpack.launchercore.mirror.download.Download;
import net.technicpack.launchercore.mirror.secure.SecureToken;
import net.technicpack.launchercore.mirror.secure.rest.ISecureMirror;
import net.technicpack.launchercore.util.DownloadListener;
import net.technicpack.launchercore.util.verifiers.IFileVerifier;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

/**
 * This file is part of Technic Launcher Core.
 * Copyright (C) 2013 Syndicate, LLC
 * <p>
 * Technic Launcher Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * Technic Launcher Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License,
 * as well as a copy of the GNU Lesser General Public License,
 * along with Technic Launcher Core.  If not, see <http://www.gnu.org/licenses/>.
 */

public class MirrorStore {
    Map<String, SecureToken> secureMirrors = new HashMap<String, SecureToken>();
    private UserModel userModel;

    public MirrorStore(UserModel userModel) {
        this.userModel = userModel;
    }

    public void addSecureMirror(String host, ISecureMirror mirror) {
        SecureToken token = new SecureToken(this.userModel, mirror);

        secureMirrors.put(host.toLowerCase(), token);
    }

    public URL getFullUrl(String url) throws DownloadException {
        URL urlObject = null;

        try {
            urlObject = new URL(url);
        } catch (MalformedURLException ex) {
            throw new DownloadException("Invalid URL: " + url, ex);
        }

        String host = urlObject.getHost().toLowerCase();

        if (secureMirrors.containsKey(host)) {
            SecureToken token = secureMirrors.get(host);

            String downloadKey = token.queryForSecureToken();

            if (downloadKey != null)
                return addDownloadKey(urlObject, token.getDownloadHost(), downloadKey, userModel.getClientToken());
        }

        return urlObject;
    }

    public String getETag(String address) throws DownloadException{
        return Download.eTag(getFullUrl(address));
    }

    public Download downloadFile(String url, String name, String output, File cache, IFileVerifier verifier, DownloadListener listener) throws IOException {
        return Download.fileFromUrl(getFullUrl(url), name, output, cache, verifier, listener);
    }

    public Download downloadFile(String url, String name, String output, File cache) throws IOException {
        return downloadFile(url, name, output, cache, null, null);
    }

    public Download downloadFile(String url, String name, String output) throws IOException {
        return downloadFile(url, name, output, null);
    }

    private URL addDownloadKey(URL url, String downloadHost, String downloadKey, String clientId) {
        if (downloadHost != null && !downloadHost.isEmpty()) {
            try {
                url = new URI(url.getProtocol(), url.getUserInfo(), downloadHost, url.getPort(), url.getPath(), url.getQuery(), null).toURL();
            } catch (URISyntaxException ex) {
                //Ignore, just keep old url
            } catch (MalformedURLException ex) {
                //Ignore, just keep old url
            }
        }

        String textUrl = url.toString();

        if (url.getQuery() == null || url.getQuery().isEmpty()) {
            textUrl += "?";
        } else {
            textUrl += "&";
        }

        textUrl += "t=" + downloadKey + "&c=" + clientId;

        try {
            return new URL(textUrl);
        } catch (MalformedURLException ex) {
            throw new Error("Code error: managed to take valid url " + url.toString() + " and turn it into invalid URL " + textUrl);
        }
    }
}
