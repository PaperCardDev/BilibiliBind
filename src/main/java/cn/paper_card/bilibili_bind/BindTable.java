package cn.paper_card.bilibili_bind;

import cn.paper_card.database.DatabaseConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import cn.paper_card.bilibili_bind.BilibiliBindApi.BindInfo;

class BindTable {
    private static final String NAME = "bili_bind";

    private final @NotNull Connection connection;


    private PreparedStatement statementInsert = null;

    private PreparedStatement statementQueryByUuid = null;

    private PreparedStatement statementQueryByUid = null;

    private PreparedStatement statementDeleteByUuid = null;

    BindTable(@NotNull Connection connection) throws SQLException {
        this.connection = connection;
        this.create();
    }

    private void create() throws SQLException {
        DatabaseConnection.createTable(this.connection, """
                CREATE TABLE IF NOT EXISTS %s (
                    uid1 BIGINT NOT NULL,
                    uid2 BIGINT NOT NULL,
                    uid BIGINT UNIQUE NOT NULL,
                    name VARCHAR(24) NOT NULL,
                    time BIGINT NOT NULL,
                    remark VARCHAR(128) NOT NULL,
                    PRIMARY KEY(uid1, uid2)
                )""".formatted(NAME));
    }

    void close() throws SQLException {
        DatabaseConnection.closeAllStatements(this.getClass(), this);
    }

    private @NotNull PreparedStatement getStatementInsert() throws SQLException {
        if (this.statementInsert == null) {
            this.statementInsert = this.connection.prepareStatement
                    ("INSERT INTO %s (uid1, uid2, uid, name, time, remark) VALUES (?, ?, ?, ?, ?, ?)".formatted(NAME));
        }
        return this.statementInsert;
    }

    private @NotNull PreparedStatement getStatementQueryByUuid() throws SQLException {
        if (this.statementQueryByUuid == null) {
            this.statementQueryByUuid = this.connection.prepareStatement
                    ("SELECT uid1,uid2,uid,name,time,remark FROM %s WHERE uid1=? AND uid2=? LIMIT 1 OFFSET 0".formatted(NAME));
        }
        return this.statementQueryByUuid;
    }

    private @NotNull PreparedStatement getStatementQueryByUid() throws SQLException {
        if (this.statementQueryByUid == null) {
            this.statementQueryByUid = this.connection.prepareStatement
                    ("SELECT uid1,uid2,uid,name,time,remark FROM %s WHERE uid=? LIMIT 1 OFFSET 0".formatted(NAME));
        }
        return this.statementQueryByUid;
    }

    private @NotNull PreparedStatement getStatementDeleteByUuid() throws SQLException {
        if (this.statementDeleteByUuid == null) {
            this.statementDeleteByUuid = this.connection.prepareStatement
                    ("DELETE FROM %s WHERE uid1=? AND uid2=? AND uid=? LIMIT 1".formatted(NAME));
        }
        return this.statementDeleteByUuid;
    }

    int insert(@NotNull BindInfo info) throws SQLException {
        final PreparedStatement ps = this.getStatementInsert();
        ps.setLong(1, info.uuid().getMostSignificantBits());
        ps.setLong(2, info.uuid().getLeastSignificantBits());
        ps.setLong(3, info.uid());
        ps.setString(4, info.name());
        ps.setLong(5, info.time());
        ps.setString(6, info.remark());
        return ps.executeUpdate();
    }

    int deleteByUuidAndUid(@NotNull UUID uuid, long uid) throws SQLException {
        final PreparedStatement ps = this.getStatementDeleteByUuid();
        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());
        ps.setLong(3, uid);
        return ps.executeUpdate();
    }

    private @NotNull BindInfo parseRow(@NotNull ResultSet resultSet) throws SQLException {
        // uid1,uid2,uid,name,time,remark
        final long uid1 = resultSet.getLong(1);
        final long uid2 = resultSet.getLong(2);
        final long uid = resultSet.getLong(3);
        final String name = resultSet.getString(4);
        final long time = resultSet.getLong(5);
        final String remark = resultSet.getString(6);
        return new BindInfo(new UUID(uid1, uid2), name, uid, remark, time);
    }

    private @Nullable BindInfo parseOne(@NotNull ResultSet resultSet) throws SQLException {

        final BindInfo info;
        try {
            if (resultSet.next()) {
                info = this.parseRow(resultSet);
            } else info = null;

            if (resultSet.next()) throw new SQLException("不应该还有数据！");
        } catch (SQLException e) {
            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }
            throw e;
        }

        resultSet.close();

        return info;
    }

    @Nullable BindInfo queryByUuid(@NotNull UUID uuid) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryByUuid();
        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());
        final ResultSet resultSet = ps.executeQuery();
        return this.parseOne(resultSet);
    }

    @Nullable BindInfo queryByUid(long uid) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryByUid();
        ps.setLong(1, uid);
        final ResultSet resultSet = ps.executeQuery();
        return this.parseOne(resultSet);
    }
}
