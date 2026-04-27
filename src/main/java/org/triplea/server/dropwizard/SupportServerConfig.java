package org.triplea.server.dropwizard;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;
import lombok.Getter;
import lombok.Setter;
import org.triplea.http.client.github.GithubClient;

/**
 * Dropwizard configuration bound from configuration.yml. This class is the framework adapter layer
 * and will be replaced when migrating away from Dropwizard.
 */
public class SupportServerConfig extends Configuration {
  @JsonProperty @Getter private final DataSourceFactory database = new DataSourceFactory();

  /** Webservice token, should be an API token for the TripleA builder bot account. */
  @Getter(onMethod_ = {@JsonProperty})
  @Setter(onMethod_ = {@JsonProperty})
  private String githubApiToken;

  @Getter(onMethod_ = {@JsonProperty})
  @Setter(onMethod_ = {@JsonProperty})
  private String githubMapsOrgName;

  @Getter(onMethod_ = {@JsonProperty})
  @Setter(onMethod_ = {@JsonProperty})
  private String errorReportingRepo;

  @Getter(onMethod_ = {@JsonProperty})
  @Setter(onMethod_ = {@JsonProperty})
  private String tripleaOrgName;

  @Getter(onMethod_ = {@JsonProperty})
  @Setter(onMethod_ = {@JsonProperty})
  private boolean mapIndexingEnabled;

  @Getter(onMethod_ = {@JsonProperty})
  @Setter(onMethod_ = {@JsonProperty})
  private int mapIndexingPeriodMinutes;

  @Getter(onMethod_ = {@JsonProperty})
  @Setter(onMethod_ = {@JsonProperty})
  private int indexingTaskDelaySeconds;

  @Getter(onMethod_ = {@JsonProperty})
  @Setter(onMethod_ = {@JsonProperty})
  private boolean logSqlStatements;

  public GithubClient githubClientMaps() {
    return GithubClient.build(githubApiToken, githubMapsOrgName);
  }

  public GithubClient githubClientErrorReporting() {
    return GithubClient.build(githubApiToken, tripleaOrgName);
  }
}
