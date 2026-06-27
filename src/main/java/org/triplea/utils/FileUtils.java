package org.triplea.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/// A collection of useful methods related to files.
@UtilityClass
@Slf4j
public final class FileUtils {

  /// Utility to delete file specified by the given path. This method handles any needed logging if
  /// the delete fails.
  public static void delete(final Path pathToDelete) {
    try {
      Files.delete(pathToDelete);
    } catch (final IOException e) {
      log.error("Failed to delete file: {}, {}", pathToDelete.toAbsolutePath(), e.getMessage(), e);
    }
  }

  /// Creates a temp file, logs and returns an empty optional if there is a problem creating the
  /// temp file.
  public static Optional<Path> createTempFile() {
    try {
      return Optional.of(Files.createTempFile("triplea-temp-file", ".temp"));
    } catch (final IOException e) {
      log.error("Failed to create temp file: {}", e.getMessage(), e);
      return Optional.empty();
    }
  }
}
