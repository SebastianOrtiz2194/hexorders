package com.oms.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration Test — Flyway migrations.
 *
 * <p>Runs Flyway directly against a PostgreSQL container, without a Spring context, so a test
 * can stop the schema at an older version, insert rows in the old shape, and check what a
 * later migration does with them.</p>
 */
@Testcontainers
class FlywayMigrationIT {

    /** PostgreSQL SQLSTATE for {@code not_null_violation}. */
    private static final String NOT_NULL_VIOLATION = "23502";

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    @DisplayName("V3 backfills a missing updated_at with created_at, then rejects new NULLs")
    void v3_backfillsMissingUpdatedAtAndMakesItRequired() throws SQLException {
        // Given: a schema at V2, where updated_at is still nullable, holding an order without it
        migrateTo("2");
        UUID orderId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 30);
        try (Connection connection = postgres.createConnection("");
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO orders (id, customer_name, status, total_amount, created_at) "
                             + "VALUES (?, 'Jane Doe', 'DELIVERED', 10.00, ?)")) {
            insert.setObject(1, orderId);
            insert.setObject(2, createdAt);
            insert.executeUpdate();
        }

        // When
        migrateTo("latest");

        // Then: the order was last updated when it was created
        try (Connection connection = postgres.createConnection("");
             PreparedStatement select = connection.prepareStatement(
                     "SELECT updated_at FROM orders WHERE id = ?")) {
            select.setObject(1, orderId);
            try (ResultSet row = select.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject("updated_at", LocalDateTime.class)).isEqualTo(createdAt);
            }
        }

        // And: a new order can no longer leave updated_at empty
        try (Connection connection = postgres.createConnection("");
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO orders (id, customer_name, status, total_amount, created_at, updated_at) "
                             + "VALUES (?, 'John Smith', 'PENDING', 10.00, now(), NULL)")) {
            insert.setObject(1, UUID.randomUUID());
            assertThatThrownBy(insert::executeUpdate)
                    .isInstanceOfSatisfying(SQLException.class,
                            e -> assertThat(e.getSQLState()).isEqualTo(NOT_NULL_VIOLATION));
        }
    }

    private static void migrateTo(String targetVersion) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(targetVersion)
                .load()
                .migrate();
    }
}
