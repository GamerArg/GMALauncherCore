/*
 * This file is part of Technic Launcher Core.
 * Copyright (C) 2013 Syndicate, LLC
 *
 * Technic Launcher Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Technic Launcher Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License,
 * as well as a copy of the GNU Lesser General Public License,
 * along with Technic Launcher Core.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.technicpack.launchercore.mirror.download;

import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.technicpack.launchercore.exception.DownloadException;
import net.technicpack.launchercore.exception.PermissionDeniedException;

import net.technicpack.launchercore.util.DownloadListener;
import net.technicpack.launchercore.util.Utils;
import net.technicpack.launchercore.util.verifiers.IFileVerifier;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FileUtils;

public class Download implements Runnable {
    private static final long TIMEOUT = 30000;

    private URL url;
    private long size = -1;
    private long downloaded = 0;
    private String outPath;
    private String name;
    private DownloadListener listener;
    private Result result = Result.FAILURE;
    private File outFile = null;
    private Exception exception = null;
    private static final int DOWNLOAD_RETRIES = 3;

    public static HttpURLConnection openHttpUrlConnection(URL url) throws MalformedURLException, IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoInput(true);
        conn.setDoOutput(false);
        System.setProperty("http.agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.162 Safari/535.19");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.162 Safari/535.19");
        HttpURLConnection.setFollowRedirects(true);
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    public static String eTag(String url){
        String md5 = "";
        try {
            md5 = eTag(new URL(url));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return md5;
    }
    public static String eTag(URL url) {
        String md5 = "";

        try {
            HttpURLConnection conn = openHttpUrlConnection(url);

            String eTag = conn.getHeaderField("ETag");
            if (eTag != null) {
                eTag = eTag.replaceAll("^\"|\"$", "");
                if (eTag.length() == 32) {
                    md5 = eTag;
                } else {
                    // Search for the .md5 asssociated file if the header is missing
                    HttpURLConnection conn_md5 = openHttpUrlConnection(new URL(url.toString() + ".md5"));
                    InputStream in = conn_md5.getInputStream();
                    try {
                        eTag = IOUtils.toString(in).trim().replaceAll("\n ", "");
                        if (eTag.length() == 32) {
                            md5 = eTag;
                        }
                    } finally {
                        IOUtils.closeQuietly(in);
                    }
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return md5;
    }

    public static Download fileFromUrl(String url, String name, String output, File cache, IFileVerifier verifier, DownloadListener listener) throws IOException {
        return fileFromUrl(new URL(url), name, output, cache, verifier, listener);
    }
    public static Download fileFromUrl(URL url, String name, String output, File cache, IFileVerifier verifier, DownloadListener listener) throws IOException {
        int tries = DOWNLOAD_RETRIES;
        File outputFile = null;
        Download download = null;
        String url_string = url.toString();
        while (tries > 0) {
            System.out.println("Starting download of " + url_string + ", with " + tries + " tries remaining");
            tries--;
            download = new Download(url, name, output);
            download.setListener(listener);
            download.run();
            if (download.getResult() != Download.Result.SUCCESS) {
                if (download.getOutFile() != null) {
                    download.getOutFile().delete();
                }
                System.err.println("Download of " + url_string + " Failed!");
                if (listener != null) {
                    listener.stateChanged("Download failed, retries remaining: " + tries, 0F);
                }
            } else {
                if (download.getOutFile().exists() && (verifier == null || verifier.isFileValid(download.getOutFile()))) {
                    outputFile = download.getOutFile();
                    break;
                }
            }
        }
        if (outputFile == null) {
            throw new DownloadException("Failed to download " + url, download.getException());
        }
        if (cache != null) {
            FileUtils.copyFile(outputFile, cache);
        }
        return download;
    }

    public Download(URL url, String name, String outPath) throws MalformedURLException {
        this.url = url;
        this.outPath = outPath;
        this.name = name;
    }

    public float getProgress() {
        return ((float) downloaded / size) * 100;
    }

    public Exception getException() {
        return exception;
    }

    @Override
    @SuppressWarnings("unused")
    public void run() {
        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        try {
            HttpURLConnection conn = Utils.openHttpConnection(url);
            int response = conn.getResponseCode();
            int responseFamily = response / 100;

            if (responseFamily == 3) {
                throw new DownloadException("The server issued a redirect response which Technic failed to follow.");
            } else if (responseFamily != 2) {
                throw new DownloadException("The server issued a " + response + " response code.");
            }

            InputStream in = getConnectionInputStream(conn);

            size = conn.getContentLength();
            outFile = new File(outPath);
            outFile.delete();

            rbc = Channels.newChannel(in);
            fos = new FileOutputStream(outFile);

            stateChanged();

            Thread progress = new MonitorThread(Thread.currentThread(), rbc);
            progress.start();

            fos.getChannel().transferFrom(rbc, 0, size > 0 ? size : Integer.MAX_VALUE);
            in.close();
            rbc.close();
            progress.interrupt();
            if (size > 0) {
                if (size == outFile.length()) {
                    result = Result.SUCCESS;
                }
            } else {
                result = Result.SUCCESS;
            }
        } catch (PermissionDeniedException e) {
            exception = e;
            result = Result.PERMISSION_DENIED;
        } catch (DownloadException e) {
            exception = e;
            result = Result.FAILURE;
        } catch (Exception e) {
            exception = e;
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(fos);
            IOUtils.closeQuietly(rbc);
        }
    }

    protected InputStream getConnectionInputStream(final URLConnection urlconnection) throws DownloadException {
        final AtomicReference<InputStream> is = new AtomicReference<InputStream>();

        for (int j = 0; (j < 3) && (is.get() == null); j++) {
            StreamThread stream = new StreamThread(urlconnection, is);
            stream.start();
            int iterationCount = 0;
            while ((is.get() == null) && (iterationCount++ < 5)) {
                try {
                    stream.join(1000L);
                } catch (InterruptedException ignore) {
                }
            }

            if (stream.permDenied.get()) {
                throw new PermissionDeniedException("Permission denied!");
            }

            if (is.get() != null) {
                break;
            }
            try {
                stream.interrupt();
                stream.join();
            } catch (InterruptedException ignore) {
            }
        }

        if (is.get() == null) {
            throw new DownloadException("Unable to download file from " + urlconnection.getURL());
        }
        return new BufferedInputStream(is.get());
    }

    private void stateChanged() {
        if (listener != null)
            listener.stateChanged(name, getProgress());
    }

    public void setListener(DownloadListener listener) {
        this.listener = listener;
    }

    public Result getResult() {
        return result;
    }

    public File getOutFile() {
        return outFile;
    }

    private static class StreamThread extends Thread {
        private final URLConnection urlconnection;
        private final AtomicReference<InputStream> is;
        public final AtomicBoolean permDenied = new AtomicBoolean(false);

        public StreamThread(URLConnection urlconnection, AtomicReference<InputStream> is) {
            this.urlconnection = urlconnection;
            this.is = is;
        }

        @Override
        public void run() {
            try {
                is.set(urlconnection.getInputStream());
            } catch (SocketException e) {
                if (e.getMessage().equalsIgnoreCase("Permission denied: connect")) {
                    permDenied.set(true);
                }
            } catch (IOException ignore) {
            }
        }
    }

    private class MonitorThread extends Thread {
        private final ReadableByteChannel rbc;
        private final Thread downloadThread;
        private long last = System.currentTimeMillis();

        public MonitorThread(Thread downloadThread, ReadableByteChannel rbc) {
            super("Download Monitor Thread");
            this.setDaemon(true);
            this.rbc = rbc;
            this.downloadThread = downloadThread;
        }

        @Override
        public void run() {
            while (!this.isInterrupted()) {
                long diff = outFile.length() - downloaded;
                downloaded = outFile.length();
                if (diff == 0) {
                    if ((System.currentTimeMillis() - last) > TIMEOUT) {
                        if (listener != null) {
                            listener.stateChanged("Download Failed", getProgress());
                        }
                        try {
                            rbc.close();
                            downloadThread.interrupt();
                        } catch (Exception ignore) {
                            //We catch all exceptions here, because ReadableByteChannel is AWESOME
                            //and was throwing NPE's sometimes when we tried to close it after
                            //the connection broke.
                        }
                        return;
                    }
                } else {
                    last = System.currentTimeMillis();
                }

                stateChanged();
                try {
                    sleep(50);
                } catch (InterruptedException ignore) {
                    return;
                }
            }
        }
    }

    public enum Result {
        SUCCESS, FAILURE, PERMISSION_DENIED,
    }
}
