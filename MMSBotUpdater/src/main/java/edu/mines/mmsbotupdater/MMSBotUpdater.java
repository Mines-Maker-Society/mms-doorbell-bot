package edu.mines.mmsbotupdater;

import java.io.File;

public class MMSBotUpdater {
    private static final String JAR_NAME_PATTERN = "mms-doorbell-bot";

    public static void main(String[] args) {
        File jarToRun = null;

        try {
            System.out.println("Updater starting...");

            File currentJar = JarManager.findCurrentJar(JAR_NAME_PATTERN);
            String currentVersion = null;

            if (currentJar != null) {
                System.out.println("Found JAR: " + currentJar.getName());
                currentVersion = JarManager.getJarVersion(currentJar);

                if (currentVersion != null) System.out.println("Current version: " + currentVersion);
                else System.out.println("Warning: Could not read version from JAR manifest");
            } else {
                System.out.println("No current JAR found, will download latest release");
            }

            GitHubRelease release = GitHubClient.getLatestRelease();
            if (release == null) {
                System.err.println("Could not fetch latest release information");
                if (currentJar != null) {
                    System.out.println("Falling back to current JAR");
                    jarToRun = currentJar;
                } else {
                    System.err.println("No JAR available to run. Exiting.");
                    System.exit(1);
                }
            } else {
                System.out.println("Latest version: " + release.getVersion());

                if (currentJar == null || currentVersion == null || VersionComparator.isNewer(release.getVersion(), currentVersion)) {
                    if (currentJar == null) System.out.println("Downloading initial version " + release.getVersion());
                    else System.out.println("Update available, Downloading version " + release.getVersion());

                    String jarFileName = JAR_NAME_PATTERN + "-" + release.getVersion() + ".jar";
                    File newJar = new File(currentJar != null ? currentJar.getParent() : ".", jarFileName);

                    if (!Downloader.downloadFile(release.getDownloadUrl(), newJar)) {
                        System.err.println("Download failed");
                        if (currentJar != null) {
                            System.out.println("Falling back to current JAR");
                            jarToRun = currentJar;
                        } else {
                            System.err.println("No JAR available to run. Exiting.");
                            System.exit(1);
                        }
                    } else {
                        System.out.println("Download complete: " + newJar.getName());
                        jarToRun = newJar;

                        if (currentJar != null && !currentJar.equals(newJar)) {
                            System.out.println("Cleaning up old JAR...");
                            if (currentJar.delete()) System.out.println("Old JAR deleted");
                        }

                        System.out.println("Update complete!");
                    }
                } else {
                    System.out.println("Already running the latest version");
                    jarToRun = currentJar;
                }
            }

        } catch (Exception e) {
            System.err.println("Error during update process: " + e.getMessage());
            e.printStackTrace();

            File fallbackJar = JarManager.findCurrentJar(JAR_NAME_PATTERN);
            if (fallbackJar != null) {
                System.out.println("Using available JAR as fallback...");
                jarToRun = fallbackJar;
            } else {
                System.err.println("No JAR available to run. Exiting.");
                System.exit(1);
            }
        }

        System.out.println("JAR_TO_RUN=" + jarToRun.getAbsolutePath());
    }
}