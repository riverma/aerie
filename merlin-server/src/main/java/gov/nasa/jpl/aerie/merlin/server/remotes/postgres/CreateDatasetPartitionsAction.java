package gov.nasa.jpl.aerie.merlin.server.remotes.postgres;

import org.intellij.lang.annotations.Language;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/*package-local*/ final class CreateDatasetPartitionsAction implements AutoCloseable {
  private static final @Language("SQL") String sql = """
    select from allocate_dataset_partitions(?);
  """;

  private final PreparedStatement statement;

  public CreateDatasetPartitionsAction(final Connection connection) throws SQLException {
    this.statement = connection.prepareStatement(sql);
  }

  public void apply(final long datasetId) throws SQLException {
    this.statement.setLong(1, datasetId);
    this.statement.executeUpdate();
  }

  private static String generateSql(final long datasetId) {
    final @Language("SQL") String sql =
        """
        create table profile_segment_%d (
           like profile_segment including defaults including constraints
        );
        alter table profile_segment
          attach partition profile_segment_%d for values in (%d);
        """.formatted(datasetId, datasetId, datasetId, datasetId);
    return sql;
  }

  @Override
  public void close() throws SQLException {
    // Nothing to clean up. The method is an intentionally-blank override.
  }
}
