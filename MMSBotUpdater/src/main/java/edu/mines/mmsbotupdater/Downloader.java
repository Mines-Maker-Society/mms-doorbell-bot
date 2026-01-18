package edu.mines.mmsbotupdater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Downloader {
    public static boolean downloadFile(String urlStr, File destination) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/octet-stream");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.err.println("Download failed with status: " + responseCode);
                return false;
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

            return true;

        } catch (IOException e) {
            System.err.println("Error downloading file: " + e.getMessage());
            if (destination.exists()) destination.delete();
            return false;
        }
    }
}
