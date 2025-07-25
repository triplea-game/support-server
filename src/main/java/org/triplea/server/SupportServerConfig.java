package org.triplea.server;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;
import lombok.Getter;
import lombok.Setter;
import org.triplea.http.client.github.GithubClient;

/**
 * This configuration class represents the configuration values in the server YML configuration. An
 * instance of this class is created by DropWizard on launch and then is passed to the application
 * class. Values can be injected into the application by using environment variables in the server
 * YML configuration file.
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

  public GithubClient createGithubApiClient() {
    return GithubClient.build(githubApiToken, githubMapsOrgName);
  }
}
