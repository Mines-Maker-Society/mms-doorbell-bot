package edu.mines.mmsbotupdater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Downloader {

    private static final int MAX_RETRIES = 20;
    private static final int INITIAL_RETRY_DELAY_MS = 2000;
    private static final int MAX_RETRY_DELAY_MS = 15000;

    /**
     * Downloads a file from a URL, has multiple tries
     */
    public static boolean downloadFile(String urlStr, File destination) {
        int attempt = 0;
        int retryDelay = INITIAL_RETRY_DELAY_MS;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                if (attempt > 1) System.out.println("Download retry attempt " + attempt + " of " + MAX_RETRIES + "...");

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/octet-stream");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    System.err.println("Download failed with status: " + responseCode);
                    throw new IOException("HTTP error code: " + responseCode);
                }

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(destination)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    long fileSize = conn.getContentLengthLong();

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;

                        if (fileSize > 0) {
                            int progress = (int) ((totalBytes * 100) / fileSize);
                            System.out.print("\rDownloading: " + progress + "%");
                        }
                    }
                    System.out.println();
                }

                System.out.println("Download successful");
                return true;

            } catch (IOException e) {
                System.err.println("Error downloading file (attempt " + attempt + "): " + e.getMessage());

                if (destination.exists()) destination.delete();

                if (attempt >= MAX_RETRIES) {
                    System.err.println("Max download retries reached. Giving up.");
                    return false;
                }

                System.out.println("Waiting " + (retryDelay / 1000) + " seconds before retry...");
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }

                retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY_MS);
            }
        }

        return false;
    }
}
