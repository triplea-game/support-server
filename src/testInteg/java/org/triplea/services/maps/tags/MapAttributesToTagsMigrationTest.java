package org.triplea.services.maps.tags;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// Runs the data migration `V2.00.09__map_attributes_to_tags.sql` with the Flyway API in a schema
/// of its own, next to the schema Quarkus migrated at startup: migrate to 2.00.08, insert
/// attributes, migrate to the latest version, check the tags.
///
/// The schema is dropped after the test, because DBUnit in the other tests would find each table
/// in two schemas.
@QuarkusTest
class MapAttributesToTagsMigrationTest {

  private static final String SCHEMA = "attributes_to_tags_test";

  @ConfigProperty(name = "quarkus.datasource.jdbc.url")
  String url;

  @ConfigProperty(name = "quarkus.datasource.username", defaultValue = "support_user")
  String user;

  @ConfigProperty(name = "quarkus.datasource.password", defaultValue = "support_user")
  String password;

  private Jdbi jdbi;

  @BeforeEach
  void dropSchema() {
    jdbi = Jdbi.create(url, user, password);
    jdbi.useHandle(handle -> handle.execute("drop schema if exists " + SCHEMA + " cascade"));
  }

  @AfterEach
  void dropSchemaAgain() {
    dropSchema();
  }

  @Test
  void attributeValuesBecomeTags() {
    migrateTo("2.00.08");
    // Difficulty (order 10) comes before Era (order 20); "Mixed" is in both, "easy" is already a
    // tag, "Ancient" is on no map, and map-a already has the tag "easy".
    inSchema(
        """
        insert into map_tag (name, display_order) values ('naval', 0), ('easy', 10);
        insert into map_attribute (id, name, display_order)
        values (1, 'Era', 20), (2, 'Difficulty', 10);
        insert into map_attribute_value (id, map_attribute_id, value, display_order) values
          (11, 1, 'Ancient', 10), (12, 1, 'Mixed', 0),
          (21, 2, 'easy', 0), (22, 2, 'hard', 10), (23, 2, 'Mixed', 20);
        insert into map_index (id, map_name, last_commit_date, repo_url, preview_image_url,
                               description, download_size_bytes, download_url, default_branch)
        values
          (1, 'map-a', '2000-01-01', 'http-a', 'http-a.png', '', 1, 'http-a.zip', 'master'),
          (2, 'map-b', '2000-01-01', 'http-b', 'http-b.png', '', 1, 'http-b.zip', 'master');
        insert into map_index_attribute (map_index_id, map_attribute_id, map_attribute_value_id)
        values (1, 2, 21), (1, 1, 12), (2, 2, 23);
        insert into map_index_tag (map_index_id, map_tag_id)
        select 1, id from map_tag where name = 'easy';
        """);

    migrateTo("latest");

    assertThat(
            query("select name || ' @' || display_order from map_tag order by display_order, id"))
        .containsExactly(
            "naval @0",
            "easy @10",
            "hard @20",
            "Difficulty: Mixed @30",
            "Era: Mixed @40",
            "Ancient @50");
    assertThat(
            query(
                """
                select m.map_name || ' -> ' || t.name
                from map_index_tag mit
                join map_index m on m.id = mit.map_index_id
                join map_tag t on t.id = mit.map_tag_id
                """))
        .containsExactlyInAnyOrder(
            "map-a -> easy", "map-a -> Era: Mixed", "map-b -> Difficulty: Mixed");
    // The previous release still uses the attribute tables after a rollback.
    assertThat(query("select count(*)::text from map_index_attribute")).containsExactly("3");
  }

  private void migrateTo(String version) {
    Flyway.configure()
        .dataSource(url, user, password)
        .schemas(SCHEMA)
        .locations("classpath:db/migration")
        .target(version)
        .load()
        .migrate();
  }

  private void inSchema(String sql) {
    jdbi.useHandle(handle -> handle.execute("set search_path to " + SCHEMA + ";\n" + sql));
  }

  private List<String> query(String sql) {
    return jdbi.withHandle(
        handle -> {
          handle.execute("set search_path to " + SCHEMA);
          return handle.createQuery(sql).mapTo(String.class).list();
        });
  }
}
