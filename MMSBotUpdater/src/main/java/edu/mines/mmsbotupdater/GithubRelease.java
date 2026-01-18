package edu.mines.mmsbotupdater;

public class GitHubRelease {
    private final String version;
    private final String downloadUrl;

    public GitHubRelease(String version, String downloadUrl) {
        this.version = version;
        this.downloadUrl = downloadUrl;
    }

    public String getVersion() {
        return version;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}
