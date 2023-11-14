package cn.paper_card.bilibili_bind;

import cn.paper_card.database.DatabaseConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

class Table {
    private static final String NAME = "bili_bind";

    private final @NotNull Connection connection;


    private PreparedStatement statementInsert = null;
    private PreparedStatement statementUpdate = null;

    private PreparedStatement statementQueryUid = null;

    private PreparedStatement statementQueryUuid = null;

    Table(@NotNull Connection connection) throws SQLException {
        this.connection = connection;
        this.create();
    }

    private void create() throws SQLException {
        DatabaseConnection.createTable(this.connection, """
                CREATE TABLE IF NOT EXISTS %s (
                    uid1 BIGINT NOT NULL,
                    uid2 BIGINT NOT NULL,
                    uid BIGINT NOT NULL,
                    name VARCHAR(24) NOT NULL,
                    time BIGINT NOT NULL
                )""".formatted(NAME));
    }

    void close() throws SQLException {
        DatabaseConnection.closeAllStatements(this.getClass(), this);
    }

    private @NotNull PreparedStatement getStatementInsert() throws SQLException {
        if (this.statementInsert == null) {
            this.statementInsert = this.connection.prepareStatement
                    ("INSERT INTO %s (uid1, uid2, uid, name, time) VALUES (?, ?, ?, ?, ?)".formatted(NAME));
        }
        return this.statementInsert;
    }

    private @NotNull PreparedStatement getStatementUpdate() throws SQLException {
        if (this.statementUpdate == null) {
            this.statementUpdate = this.connection.prepareStatement
                    ("UPDATE %s SET uid=?,name=?,time=? WHERE uid1=? AND uid2=?".formatted(NAME));
        }
        return this.statementUpdate;
    }

    private @NotNull PreparedStatement getStatementQueryUid() throws SQLException {
        if (this.statementQueryUid == null) {
            this.statementQueryUid = this.connection.prepareStatement
                    ("SELECT uid FROM %s WHERE uid1=? AND uid2=? LIMIT 1 OFFSET 0".formatted(NAME));
        }
        return this.statementQueryUid;
    }

    private @NotNull PreparedStatement getStatementQueryUuid() throws SQLException {
        if (this.statementQueryUuid == null) {
            this.statementQueryUuid = this.connection.prepareStatement
                    ("SELECT uid1, uid2 FROM %s WHERE uid=? LIMIT 1 OFFSET 0".formatted(NAME));
        }
        return this.statementQueryUuid;
    }

    int insert(@NotNull UUID uuid, @NotNull String name, long uid, long time) throws SQLException {
        final PreparedStatement ps = this.getStatementInsert();
        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());
        ps.setLong(3, uid);
        ps.setString(4, name);
        ps.setLong(5, time);
        return ps.executeUpdate();
    }

    int updateByUuid(@NotNull UUID uuid, @NotNull String name, long uid, long time) throws SQLException {
        final PreparedStatement ps = this.getStatementUpdate();
        ps.setLong(1, uid);
        ps.setString(2, name);
        ps.setLong(3, time);
        ps.setLong(4, uuid.getMostSignificantBits());
        ps.setLong(5, uuid.getLeastSignificantBits());
        return ps.executeUpdate();
    }

    @Nullable UUID queryUuid(long uid) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryUuid();
        ps.setLong(1, uid);

        final ResultSet resultSet = ps.executeQuery();

        final UUID uuid;
        try {
            if (resultSet.next()) {
                final long uid1 = resultSet.getLong(1);
                final long uid2 = resultSet.getLong(2);
                uuid = new UUID(uid1, uid2);
            } else uuid = null;

            if (resultSet.next()) throw new SQLException("不应该还有数据！");
        } catch (SQLException e) {
            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }
            throw e;
        }


        resultSet.close();

        return uuid;
    }


    @Nullable Long queryUid(@NotNull UUID uuid) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryUid();

        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());

        final ResultSet resultSet = ps.executeQuery();

        Long uid;
        try {
            if (resultSet.next()) {
                uid = resultSet.getLong(1);
            } else uid = null;

            if (resultSet.next()) throw new SQLException("不应该还有数据！");
        } catch (SQLException e) {

            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }

            throw e;
        }

        resultSet.close();

        return uid;
    }
}
