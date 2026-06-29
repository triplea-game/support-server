package org.triplea.services.maps.indexing.tasks;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.function.Function;
import org.triplea.http.client.github.MapRepoListing;

/// A function where if given a map repo listing will find the 'description.html' file in that repo
/// and returns its contents. If the contents are too long or the file is missing then will return a
/// 'description-missing' error message with details on how to fix it.
public class MapDescriptionReader implements Function<MapRepoListing, String> {
  private static final int DESCRIPTION_COLUMN_DATABASE_MAX_LENGTH = 3000;
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

  @Override
  public String apply(final MapRepoListing mapRepoListing) {
    final String description = downloadDescription(mapRepoListing).orElse(null);
    if (description == null) {
      return String.format(
          "No description available for: %s. "
              + "Contact the map author and request they add a 'description.html' file",
          mapRepoListing.getUri());
    } else if (description.length() > DESCRIPTION_COLUMN_DATABASE_MAX_LENGTH) {
      return String.format(
          "The description for this map is too long at %s characters. Max length is %s. "
              + "Contact the map author for: %s"
              + ", and request they reduce the length of the file 'description.html'",
          description.length(), DESCRIPTION_COLUMN_DATABASE_MAX_LENGTH, mapRepoListing.getUri());
    } else {
      return description;
    }
  }

  private Optional<String> downloadDescription(final MapRepoListing mapRepoListing) {
    final URI descriptionUri =
        URI.create(
            mapRepoListing.getUri().toString()
                + "/blob/"
                + mapRepoListing.getDefaultBranch()
                + "/description.html?raw=true");
    try {
      HttpRequest request = HttpRequest.newBuilder(descriptionUri).GET().build();
      HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200 ? Optional.of(response.body()) : Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }
}
