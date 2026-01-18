package edu.mines.mmsbotupdater;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class JarManager {
    public static File findCurrentJar(String namePattern) {
        File dir = new File(".");
        File[] files = dir.listFiles((d, name) -> name.startsWith(namePattern) && name.endsWith(".jar"));

        if (files != null && files.length > 0) {
            File newest = files[0];
            for (File f : files) {
                if (f.lastModified() > newest.lastModified()) newest = f;
            }
            return newest;
        }
        return null;
    }

    public static String getJarVersion(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) return manifest.getMainAttributes().getValue("Implementation-Version");
        } catch (IOException e) {
            System.err.println("Error reading JAR manifest: " + e.getMessage());
        }
        return null;
    }
}