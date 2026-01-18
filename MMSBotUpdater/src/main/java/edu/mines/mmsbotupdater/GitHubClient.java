package edu.mines.mmsbotupdater;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GitHubClient {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/Mines-Maker-Society/mms-doorbell-bot/releases/latest";

    public static GitHubRelease getLatestRelease() {
        try {
            URL url = new URL(GITHUB_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.err.println("GitHub API returned status: " + responseCode);
                return null;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            JsonObject releaseJson = JsonParser.parseString(response.toString()).getAsJsonObject();

            String version = releaseJson.get("tag_name").getAsString().replaceFirst("^v", "");

            String downloadUrl = null;
            for (var asset : releaseJson.getAsJsonArray("assets")) {
                JsonObject assetObj = asset.getAsJsonObject();
                String name = assetObj.get("name").getAsString();
                if (name.endsWith(".jar") && !name.contains("javadoc") && !name.contains("sources")) {
                    downloadUrl = assetObj.get("browser_download_url").getAsString();
                    break;
                }
            }

            if (downloadUrl == null) {
                System.err.println("Could not find JAR file in release assets");
                return null;
            }

            return new GitHubRelease(version, downloadUrl);

        } catch (IOException e) {
            System.err.println("Error fetching latest release: " + e.getMessage());
            return null;
        }
    }
}
