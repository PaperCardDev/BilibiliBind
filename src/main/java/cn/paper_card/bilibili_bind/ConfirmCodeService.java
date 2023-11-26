package cn.paper_card.bilibili_bind;

import cn.paper_card.database.DatabaseApi;
import cn.paper_card.database.DatabaseConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;

class ConfirmCodeService {

    record Info(
            int code,
            UUID uuid,
            String name,
            long uid,
            String biliName,
            long time
    ) {
    }

    static class Table {
        private static final String NAME = "bili_confirm_code";

        private final Connection connection;

        private PreparedStatement statementInsert = null;

        private PreparedStatement statementQueryByUuid = null;

        private PreparedStatement statementQueryByCode = null;

        private PreparedStatement statementDeleteByCode = null;

        Table(@NotNull Connection connection) throws SQLException {
            this.connection = connection;
            this.create();
        }

        private void create() throws SQLException {
            DatabaseConnection.createTable(this.connection, """
                    CREATE TABLE IF NOT EXISTS %s (
                        code INT NOT NULL UNIQUE,
                        uid1 BIGINT NOT NULL,
                        uid2 BIGINT NOT NULL,
                        name VARCHAR(64) NOT NULL,
                        uid BIGINT NOT NULL,
                        bname VARCHAR(64) NOT NULL,
                        time BIGINT NOT NULL,
                        PRIMARY KEY(uid1, uid2)
                    )""".formatted(NAME));
        }

        void close() throws SQLException {
            DatabaseConnection.closeAllStatements(this.getClass(), this);
        }


        private @NotNull PreparedStatement getStatementInsert() throws SQLException {
            if (this.statementInsert == null) {
                this.statementInsert = this.connection.prepareStatement
                        ("INSERT INTO %s (code, uid1, uid2, name, uid, bname, time) VALUES (?, ?, ?, ?, ?, ?, ?)".formatted(NAME));
            }
            return this.statementInsert;
        }

        private @NotNull PreparedStatement getStatementQueryByUuid() throws SQLException {
            if (this.statementQueryByUuid == null) {
                this.statementQueryByUuid = this.connection.prepareStatement
                        ("SELECT code,uid1,uid2,name,uid,bname,time FROM %s WHERE uid1=? AND uid2=? LIMIT 1 OFFSET 0".formatted(NAME));
            }
            return this.statementQueryByUuid;
        }

        private @NotNull PreparedStatement getStatementQueryByCode() throws SQLException {
            if (this.statementQueryByCode == null) {
                this.statementQueryByCode = this.connection.prepareStatement
                        ("SELECT code,uid1,uid2,name,uid,bname,time FROM %s WHERE code=? LIMIT 1 OFFSET 0".formatted(NAME));
            }
            return this.statementQueryByCode;
        }

        private @NotNull PreparedStatement getStatementDeleteByCode() throws SQLException {
            if (this.statementDeleteByCode == null) {
                this.statementDeleteByCode = this.connection.prepareStatement
                        ("DELETE FROM %s WHERE code=? LIMIT 1".formatted(NAME));
            }
            return this.statementDeleteByCode;
        }

        int insert(@NotNull Info info) throws SQLException {
            final PreparedStatement ps = this.getStatementInsert();
            ps.setInt(1, info.code());
            ps.setLong(2, info.uuid().getMostSignificantBits());
            ps.setLong(3, info.uuid().getLeastSignificantBits());
            ps.setString(4, info.name());

            ps.setLong(5, info.uid());
            ps.setString(6, info.biliName());
            ps.setLong(7, info.time());

            return ps.executeUpdate();
        }

        private @NotNull Info parseRow(@NotNull ResultSet resultSet) throws SQLException {
            //                ("SELECT code,uid1,uid2,name,uid,bname,time FROM %s WHERE uid1=? AND uid2=? LIMIT 1 OFFSET 0");
            final int code = resultSet.getInt(1);
            final long uid1 = resultSet.getLong(2);
            final long uid2 = resultSet.getLong(3);
            final String name = resultSet.getString(4);

            final long uid = resultSet.getLong(5);
            final String bname = resultSet.getString(6);

            final long time = resultSet.getLong(7);

            return new Info(code, new UUID(uid1, uid2), name, uid, bname, time);
        }

        private @Nullable Info parseOne(@NotNull ResultSet resultSet) throws SQLException {


            final Info info;
            try {
                if (resultSet.next()) info = this.parseRow(resultSet);
                else info = null;

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

        @Nullable Info queryByUuid(@NotNull UUID uuid) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryByUuid();

            ps.setLong(1, uuid.getMostSignificantBits());
            ps.setLong(2, uuid.getLeastSignificantBits());

            final ResultSet resultSet = ps.executeQuery();

            return this.parseOne(resultSet);
        }

        @Nullable Info queryByCode(int code) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryByCode();
            ps.setInt(1, code);
            final ResultSet resultSet = ps.executeQuery();
            return this.parseOne(resultSet);
        }

        int deleteByCode(int code) throws SQLException {
            final PreparedStatement ps = this.getStatementDeleteByCode();
            ps.setInt(1, code);
            return ps.executeUpdate();
        }
    }

    private Table table = null;
    private Connection connection = null;

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    ConfirmCodeService(@NotNull DatabaseApi.MySqlConnection connection) {
        this.mySqlConnection = connection;
    }

    private @NotNull Table getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRowConnection();

        if (this.connection != null && this.connection == newCon) return this.table;

        if (this.table != null) this.table.close();
        this.connection = newCon;
        this.table = new Table(newCon);
        return this.table;
    }

    private int randomCode() {
        final int min = 1;
        final int max = 999999;
        return new Random().nextInt(max - min + 1) + min;
    }

    void close() throws SQLException {
        synchronized (this.mySqlConnection) {
            final Table t = this.table;
            if (t == null) {
                this.connection = null;
                return;
            }

            this.table = null;
            this.connection = null;
            t.close();
        }
    }


    // 生成验证码，如果已经存在，则返回旧的，并调整创建时间
    int getCode(@NotNull UUID uuid, @NotNull String name, long uid, @NotNull String biliName) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();

                final Info info = t.queryByUuid(uuid);
                this.mySqlConnection.setLastUseTime();

                if (info != null) return info.code();

                // 插入
                final int code = this.randomCode();

                final int inserted = t.insert(new Info(code, uuid, name, uid, biliName, System.currentTimeMillis()));
                this.mySqlConnection.setLastUseTime();

                if (inserted != 1) throw new RuntimeException("插入了%d条数据！".formatted(inserted));

                return code;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Nullable Info takeCode(int code) throws SQLException {
        synchronized (this.mySqlConnection) {

            try {
                final Table t = this.getTable();

                final Info info = t.queryByCode(code);
                this.mySqlConnection.setLastUseTime();

                if (info == null) return null;

                final int deleted = t.deleteByCode(info.code());
                this.mySqlConnection.setLastUseTime();

                if (deleted != 1) throw new RuntimeException("删除了%d条数据！".formatted(deleted));

                return info;

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Nullable Info queryByPlayer(@NotNull UUID uuid) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();

                final Info info = t.queryByUuid(uuid);
                this.mySqlConnection.setLastUseTime();

                return info;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }

    }
}
