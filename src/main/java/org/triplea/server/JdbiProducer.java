package org.triplea.server;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

/// CDI producer that exposes a singleton [Jdbi] instance backed by the Quarkus datasource.
@ApplicationScoped
public class JdbiProducer {

  @Inject AgroalDataSource dataSource;

  @Produces
  @ApplicationScoped
  public Jdbi jdbi() {
    return Jdbi.create(dataSource).installPlugin(new SqlObjectPlugin());
  }
}
