package org.triplea.services.maps.indexing.tasks;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.triplea.http.client.github.MapRepoListing;
import org.triplea.utils.YamlReader;

@Slf4j
@Builder
public class MapNameReader implements Function<MapRepoListing, Optional<String>> {
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

  /* Function to download content as a string, returns null if the download fails or returns a non-200 status. */
  @Setter(value = AccessLevel.PACKAGE, onMethod_ = @VisibleForTesting)
  @Builder.Default
  private Function<URI, String> downloadFunction =
      uri -> {
        try {
          HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
          HttpResponse<String> response =
              HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
          return response.statusCode() == 200 ? response.body() : null;
        } catch (IOException e) {
          return null;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
      };

  public static URI computeMapYamlLocation(MapRepoListing mapRepoListing) {
    return URI.create(
        mapRepoListing.getUri().toString()
            + "/blob/"
            + mapRepoListing.getDefaultBranch()
            + "/map.yml?raw=true");
  }

  /// Determines the expected location of a map.yml file, downloads it, reads and returns the
  /// 'map_name' attribute. Returns null if the file could not be found or otherwise could not be
  /// read.
  @Override
  public Optional<String> apply(MapRepoListing mapRepoListing) {
    final URI mapYmlUri = computeMapYamlLocation(mapRepoListing);

    final String mapYamlContents = downloadFunction.apply(mapYmlUri);
    if (mapYamlContents == null) {
      log.warn("Could not index, missing map.yml. Expected URI: {}", mapYmlUri);
      return Optional.empty();
    }

    // parse and return the 'map_name' attribute from the YML file we just downloaded
    try {
      final Map<String, Object> mapYamlData = YamlReader.readMap(mapYamlContents);
      return Optional.of((String) mapYamlData.get("map_name"));
    } catch (final ClassCastException
        | YamlReader.InvalidYamlFormatException
        | NullPointerException e) {
      log.error("Invalid map.yml data found at URI: {}, error: {}", mapYmlUri, e.getMessage());
      return Optional.empty();
    }
  }
}
