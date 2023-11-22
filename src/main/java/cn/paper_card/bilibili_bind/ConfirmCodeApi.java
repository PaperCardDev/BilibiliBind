package cn.paper_card.bilibili_bind;

import cn.paper_card.database.DatabaseApi;
import cn.paper_card.database.DatabaseConnection;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

class ConfirmCodeApi {

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

        private PreparedStatement statementUpdateByUuid = null;

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

        private @NotNull PreparedStatement getStatementUpdateByUuid() throws SQLException {
            if (this.statementUpdateByUuid == null) {
                this.statementUpdateByUuid = this.connection.prepareStatement
                        ("UPDATE %s SET code=?, name=?, uid=?, bname=?, time=? WHERE uid1=? AND uid2=?".formatted(NAME));
            }
            return this.statementUpdateByUuid;
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
                        ("DELETE FROM %s WHERE code=?".formatted(NAME));
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

        int update(@NotNull Info info) throws SQLException {

            final PreparedStatement ps = this.getStatementUpdateByUuid();

//            ("UPDATE %s SET code=?, name=?, uid=?, bname=?, time=? WHERE uid1=? AND uid2=?".formatted(NAME));

            ps.setInt(1, info.code());
            ps.setString(2, info.name());
            ps.setLong(3, info.uid());
            ps.setString(4, info.biliName());
            ps.setLong(5, info.time());

            ps.setLong(6, info.uuid().getMostSignificantBits());
            ps.setLong(7, info.uuid().getLeastSignificantBits());

            return ps.executeUpdate();
        }

        private @NotNull List<Info> parseAll(@NotNull ResultSet resultSet) throws SQLException {

            final LinkedList<Info> list = new LinkedList<>();

            try {
                while (resultSet.next()) {
//                ("SELECT code,uid1,uid2,name,uid,bname,time FROM %s WHERE uid1=? AND uid2=? LIMIT 1 OFFSET 0");
                    final int code = resultSet.getInt(1);
                    final long uid1 = resultSet.getLong(2);
                    final long uid2 = resultSet.getLong(3);
                    final String name = resultSet.getString(4);

                    final long uid = resultSet.getLong(5);
                    final String bname = resultSet.getString(6);

                    final long time = resultSet.getLong(7);

                    final Info info = new Info(code, new UUID(uid1, uid2), name, uid, bname, time);

                    list.add(info);
                }
            } catch (SQLException e) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {
                }
                throw e;
            }

            resultSet.close();

            return list;
        }

        @NotNull List<Info> queryByUuid(@NotNull UUID uuid) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryByUuid();

            ps.setLong(1, uuid.getMostSignificantBits());
            ps.setLong(2, uuid.getLeastSignificantBits());

            final ResultSet resultSet = ps.executeQuery();

            return this.parseAll(resultSet);
        }

        @NotNull List<Info> queryByCode(int code) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryByCode();
            ps.setInt(1, code);
            final ResultSet resultSet = ps.executeQuery();
            return this.parseAll(resultSet);
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

    ConfirmCodeApi(@NotNull BilibiliBind plugin) {
        this.mySqlConnection = plugin.getDatabaseApi().getRemoteMySqlDb().getConnectionUnimportant();
    }

    private @NotNull Table getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRowConnection();
        if (this.connection == null) {
            this.connection = newCon;
            if (this.table != null) this.table.close();
            this.table = new Table(this.connection);
            return this.table;
        } else if (this.connection == newCon) {
            return this.table;
        } else {
            this.connection = newCon;
            if (this.table != null) this.table.close();
            this.table = new Table(this.connection);
            return this.table;
        }
    }

    private int randomCode() {
        final int min = 1;
        final int max = 999999;
        return new Random().nextInt(max - min + 1) + min;
    }

    void close() {
        synchronized (this.mySqlConnection) {
            if (this.table != null) {
                try {
                    this.table.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                this.table = null;
            }
        }
    }


    // 生成验证码，如果已经存在，则返回旧的，并调整创建时间
    int getCode(@NotNull UUID uuid, @NotNull String name, long uid, @NotNull String biliName) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();

                final List<Info> list = t.queryByUuid(uuid);
                this.mySqlConnection.setLastUseTime();

                final int size = list.size();

                if (size == 0) {
                    // 插入
                    final int code = this.randomCode();

                    final int inserted = t.insert(new Info(code, uuid, name, uid, biliName, System.currentTimeMillis()));
                    this.mySqlConnection.setLastUseTime();

                    if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));

                    return code;
                }

                if (size == 1) {
                    // 更新
                    final Info info = list.get(0);
                    final Info newInfo = new Info(info.code(), info.uuid(), info.name(), info.uid(), info.biliName(), System.currentTimeMillis());

                    final int updated = t.update(newInfo);
                    this.mySqlConnection.setLastUseTime();

                    if (updated != 1) throw new Exception("更新了%d条数据！".formatted(updated));
                    return newInfo.code();
                }

                throw new Exception("查询到了%d条数据！".formatted(size));

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Nullable Info takeCode(int code) throws Exception {
        synchronized (this.mySqlConnection) {

            try {
                final Table t = this.getTable();

                final List<Info> list = t.queryByCode(code);
                this.mySqlConnection.setLastUseTime();

                final int size = list.size();

                if (size == 1) {
                    final Info info = list.get(0);

                    final int deleted = t.deleteByCode(info.code());
                    this.mySqlConnection.setLastUseTime();

                    if (deleted != 1) throw new Exception("删除了%d条数据！".formatted(deleted));

                    return info;
                }

                if (size == 0) return null;

                throw new Exception("查询到了%d条数据！".formatted(size));
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
