package com.levanilla.taczstartuphelper;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Properties;

public final class ExportCopyCache {
    private static final Path CACHE_DIR = FMLPaths.CONFIGDIR.get().resolve("tacz_startup_helper").resolve("export-copy");

    private ExportCopyCache() {}

    public static boolean canSkip(Class<?> resourceClass, String srcPath, Path root, String path) {
        Path target = root.resolve(path);
        if (!Files.isDirectory(target) || !Files.isRegularFile(target.resolve("gunpack.meta.json"))) {
            return false;
        }

        Signature signature = signature(resourceClass, srcPath);
        if (signature == null) {
            return false;
        }

        Properties props = load(marker(path));
        return srcPath.equals(props.getProperty("srcPath"))
            && signature.location.equals(props.getProperty("sourceLocation"))
            && Long.toString(signature.size).equals(props.getProperty("sourceSize"))
            && Long.toString(signature.modified).equals(props.getProperty("sourceModified"));
    }

    public static void markCopied(Class<?> resourceClass, String srcPath, String path) {
        Signature signature = signature(resourceClass, srcPath);
        if (signature == null) {
            return;
        }

        Properties props = new Properties();
        props.setProperty("srcPath", srcPath);
        props.setProperty("sourceLocation", signature.location);
        props.setProperty("sourceSize", Long.toString(signature.size));
        props.setProperty("sourceModified", Long.toString(signature.modified));
        try {
            Files.createDirectories(CACHE_DIR);
            try (OutputStream out = Files.newOutputStream(marker(path))) {
                props.store(out, "TacZ Startup Helper export copy cache");
            }
        } catch (IOException e) {
            TaczStartupHelper.LOGGER.debug("{} failed to write export marker for {}: {}", TaczStartupHelper.PREFIX, path, e.toString());
        }
    }

    public static boolean isExportComplete(Path root, String path) {
        Path target = root.resolve(path);
        return Files.isDirectory(target) && Files.isRegularFile(target.resolve("gunpack.meta.json"));
    }

    private static Properties load(Path marker) {
        Properties props = new Properties();
        if (!Files.isRegularFile(marker)) {
            return props;
        }
        try (InputStream in = Files.newInputStream(marker)) {
            props.load(in);
        } catch (IOException e) {
            TaczStartupHelper.LOGGER.debug("{} failed to read export marker {}: {}", TaczStartupHelper.PREFIX, marker, e.toString());
        }
        return props;
    }

    private static Path marker(String path) {
        String safe = path.replace('\\', '_').replace('/', '_').replace(':', '_');
        return CACHE_DIR.resolve(safe + ".properties");
    }

    private static Signature signature(Class<?> resourceClass, String srcPath) {
        Path archivePath = archivePath(resourceClass, srcPath);
        if (archivePath != null) {
            try {
                Path normalized = archivePath.toAbsolutePath().normalize();
                return new Signature(normalized.toString(), Files.size(normalized), Files.getLastModifiedTime(normalized).toMillis());
            } catch (Exception e) {
                TaczStartupHelper.LOGGER.debug("{} failed to stat export archive {}: {}", TaczStartupHelper.PREFIX, archivePath, e.toString());
            }
        }

        URL resourceUrl = resourceClass.getResource(srcPath);
        if (resourceUrl != null) {
            return new Signature(resourceUrl.toString(), -1L, -1L);
        }

        CodeSource codeSource = resourceClass.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation() != null) {
            return new Signature(codeSource.getLocation().toString() + "!" + srcPath, -1L, -1L);
        }
        return null;
    }

    private static Path archivePath(Class<?> resourceClass, String srcPath) {
        URL resourceUrl = resourceClass.getResource(srcPath);
        Path fromResource = archivePath(resourceUrl);
        if (fromResource != null) {
            return fromResource;
        }
        CodeSource codeSource = resourceClass.getProtectionDomain().getCodeSource();
        return codeSource != null ? archivePath(codeSource.getLocation()) : null;
    }

    private static Path archivePath(URL url) {
        if (url == null) {
            return null;
        }
        try {
            String raw = URLDecoder.decode(url.toString(), StandardCharsets.UTF_8);
            int bang = raw.indexOf('!');
            if (bang >= 0) {
                raw = raw.substring(0, bang);
            }
            boolean stripped = true;
            while (stripped) {
                stripped = false;
                if (raw.startsWith("jar:")) {
                    raw = raw.substring(4);
                    stripped = true;
                }
                if (raw.startsWith("union:")) {
                    raw = raw.substring(6);
                    stripped = true;
                }
                if (raw.startsWith("file:")) {
                    raw = raw.substring(5);
                    stripped = true;
                }
            }

            String lower = raw.toLowerCase(java.util.Locale.ROOT);
            int end = -1;
            for (String ext : new String[]{".jar", ".zip"}) {
                int idx = lower.lastIndexOf(ext);
                if (idx >= 0) {
                    end = idx + ext.length();
                    break;
                }
            }
            if (end >= 0) {
                raw = raw.substring(0, end);
            }
            if (raw.startsWith("/") && raw.length() > 2 && raw.charAt(2) == ':') {
                raw = raw.substring(1);
            }
            Path path = Path.of(raw);
            return Files.exists(path) ? path : null;
        } catch (Exception e) {
            TaczStartupHelper.LOGGER.debug("{} failed to resolve archive path from {}: {}", TaczStartupHelper.PREFIX, url, e.toString());
            return null;
        }
    }

    private record Signature(String location, long size, long modified) {}
}
