package cn.paper_card.bilibili_bind;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.database.api.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;

class BindCodeServiceImpl implements BilibiliBindApi.BindCodeService {
    BindCodeServiceImpl(@NotNull DatabaseApi.MySqlConnection mySqlConnection, long maxAliveTime) {
        this.mySqlConnection = mySqlConnection;
        this.maxAliveTime = maxAliveTime;
    }

    BindCodeServiceImpl(@NotNull DatabaseApi.MySqlConnection mySqlConnection) {
        this(mySqlConnection, 5 * 60 * 1000L);
    }

    private BindCodeTable table = null;
    private Connection connection = null;

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private final long maxAliveTime;

    private @NotNull BindCodeTable getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRawConnection();

        if (this.connection != null && this.connection != newCon) return this.table;

        this.connection = newCon;
        if (this.table != null) this.table.close();
        this.table = new BindCodeTable(this.connection);
        return this.table;
    }


    int close() throws SQLException {
        synchronized (this.mySqlConnection) {

            final BindCodeTable t = this.table;

            if (t == null) {
                this.connection = null;
                return -1;
            }

            final int clear;

            try {
                clear = this.cleanOutdated();
                this.connection = null;
                this.table = null;
            } catch (SQLException e) {
                this.connection = null;
                this.table = null;
                try {
                    t.close();
                } catch (SQLException ignored) {
                }

                throw e;
            }

            t.close();

            return clear;
        }
    }


    private int randomCode() {
        final int min = 1;
        final int max = 999999;
        return new Random().nextInt(max - min + 1) + min;
    }


    @Override
    public int createCode(@NotNull UUID uuid, @NotNull String name) throws SQLException {
        final int code = this.randomCode();
        final long time = System.currentTimeMillis();
        synchronized (this.mySqlConnection) {
            try {
                final BindCodeTable t = this.getTable();

                // 数据库保证：防止验证码重复

                final BilibiliBindApi.BindCodeInfo info = new BilibiliBindApi.BindCodeInfo(code, uuid, name, time);
                final int updated = t.updateByUuid(info);
                this.mySqlConnection.setLastUseTime();

                if (updated == 0) {
                    final int inserted = t.insert(info);
                    this.mySqlConnection.setLastUseTime();

                    if (inserted != 1) throw new RuntimeException("插入了%d条数据！".formatted(inserted));
                    return code;
                }

                if (updated == 1) return code;

                throw new RuntimeException("根据一个UUID更新了多条数据！");
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    // 取出一个绑定验证码
    @Override
    public @Nullable BilibiliBindApi.BindCodeInfo takeByCode(int code) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final BindCodeTable t = this.getTable();
                final BilibiliBindApi.BindCodeInfo info = t.queryByCode(code);
                this.mySqlConnection.setLastUseTime();

                if (info == null) return null;

                final int deleted = t.deleteByCode(code);

                if (deleted != 1) throw new RuntimeException("删除了%d条数据！".formatted(deleted));

                // 判断验证码是否过期
                final long delta = System.currentTimeMillis() - info.time();
                if (delta > this.maxAliveTime) return null;
                return info;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @Nullable BilibiliBindApi.BindCodeInfo takeByUuid(@NotNull UUID uuid) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final BindCodeTable t = this.getTable();
                final BilibiliBindApi.BindCodeInfo info = t.queryByUuid(uuid);
                this.mySqlConnection.setLastUseTime();

                if (info == null) return null;

                // 删除
                final int deleted = t.deleteByCode(info.code());

                if (deleted != 1) throw new RuntimeException("删除了%d条数据！".formatted(deleted));

                // 检查是否过期
                // 判断验证码是否过期
                final long delta = System.currentTimeMillis() - info.time();
                if (delta > this.maxAliveTime) return null;

                return info;

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public int cleanOutdated() throws SQLException {
        final long begin = System.currentTimeMillis() - maxAliveTime;
        synchronized (this.mySqlConnection) {
            try {
                final BindCodeTable t = this.getTable();
                final int deleted = t.deleteTimeBefore(begin);
                this.mySqlConnection.setLastUseTime();

                return deleted;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }


    static class BindCodeTable {

        private PreparedStatement statementInsert = null;

        private PreparedStatement statementUpdateByUuid = null;

        private PreparedStatement statementQueryByCode = null;

        private PreparedStatement statementDeleteByCode = null;


        private PreparedStatement statementDeleteTimeBefore = null;

        private PreparedStatement statementQueryByUuid = null;

        private final static String NAME = "bili_bind_code";

        private final @NotNull Connection connection;


        BindCodeTable(@NotNull Connection connection) throws SQLException {
            this.connection = connection;
            this.createTable(connection);
        }

        private void createTable(@NotNull Connection connection) throws SQLException {
            final String sql2 = """
                    CREATE TABLE IF NOT EXISTS %s (
                        code    INT UNIQUE NOT NULL,
                        uid1    BIGINT NOT NULL,
                        uid2    BIGINT NOT NULL,
                        name    VARCHAR(64) NOT NULL,
                        time    BIGINT NOT NULL,
                        PRIMARY KEY(uid1, uid2)
                    )""".formatted(NAME);
            Util.executeSQL(connection, sql2);
        }

        void close() throws SQLException {
            Util.closeAllStatements(this.getClass(), this);
        }

        private @NotNull PreparedStatement getStatementInsert() throws SQLException {
            if (this.statementInsert == null) {
                this.statementInsert = this.connection.prepareStatement("""
                        INSERT INTO %s (code, uid1, uid2, name, time) VALUES (?, ?, ?, ?, ?)
                        """.formatted(NAME));
            }
            return this.statementInsert;
        }


        private @NotNull PreparedStatement getStatementUpdateByUuid() throws SQLException {
            if (this.statementUpdateByUuid == null) {
                this.statementUpdateByUuid = this.connection.prepareStatement("""
                        UPDATE %s SET code=?, name=?, time=? WHERE uid1=? AND uid2=? LIMIT 1
                        """.formatted(NAME));
            }

            return this.statementUpdateByUuid;
        }

        private @NotNull PreparedStatement getStatementQueryByCode() throws SQLException {
            if (this.statementQueryByCode == null) {
                this.statementQueryByCode = this.connection.prepareStatement("SELECT code, uid1, uid2, name, time FROM %s WHERE code=? LIMIT 1".formatted(NAME));
            }
            return this.statementQueryByCode;
        }

        private @NotNull PreparedStatement getStatementDeleteByCode() throws SQLException {
            if (this.statementDeleteByCode == null) {
                this.statementDeleteByCode = this.connection.prepareStatement("DELETE FROM %s WHERE code=? LIMIT 1".formatted(NAME));
            }
            return this.statementDeleteByCode;
        }

        private @NotNull PreparedStatement getStatementDeleteTimeBefore() throws SQLException {
            if (this.statementDeleteTimeBefore == null) {
                this.statementDeleteTimeBefore = this.connection.prepareStatement("DELETE FROM %s WHERE time<?".formatted(NAME));
            }
            return this.statementDeleteTimeBefore;
        }

        private @NotNull PreparedStatement getStatementQueryByUuid() throws SQLException {
            if (this.statementQueryByUuid == null) {
                this.statementQueryByUuid = this.connection.prepareStatement
                        ("SELECT code, uid1, uid2, name, time FROM %s WHERE uid1=? AND uid2=? LIMIT 1".formatted(NAME));
            }
            return this.statementQueryByUuid;
        }

        int insert(@NotNull BilibiliBindApi.BindCodeInfo info) throws SQLException {
            final PreparedStatement ps = this.getStatementInsert();
            ps.setInt(1, info.code());
            ps.setLong(2, info.uuid().getMostSignificantBits());
            ps.setLong(3, info.uuid().getLeastSignificantBits());
            ps.setString(4, info.name());
            ps.setLong(5, info.time());
            return ps.executeUpdate();
        }

        int updateByUuid(@NotNull BilibiliBindApi.BindCodeInfo info) throws SQLException {
            final PreparedStatement ps = this.getStatementUpdateByUuid();
            // UPDATE %s SET code=?, name=?, time=? WHERE uid1=? AND uid2=?
            ps.setInt(1, info.code());
            ps.setString(2, info.name());
            ps.setLong(3, info.time());
            ps.setLong(4, info.uuid().getMostSignificantBits());
            ps.setLong(5, info.uuid().getLeastSignificantBits());
            return ps.executeUpdate();
        }

        private @NotNull BilibiliBindApi.BindCodeInfo parseRow(@NotNull ResultSet resultSet) throws SQLException {
            final int code = resultSet.getInt(1);
            final long uid1 = resultSet.getLong(2);
            final long uid2 = resultSet.getLong(3);
            final String name = resultSet.getString(4);
            final long time = resultSet.getLong(5);
            return new BilibiliBindApi.BindCodeInfo(code, new UUID(uid1, uid2), name, time);
        }

        private @Nullable BilibiliBindApi.BindCodeInfo parseOne(@NotNull ResultSet resultSet) throws SQLException {
            final BilibiliBindApi.BindCodeInfo info;
            try {
                // "SELECT code, uid1, uid2, name, time FROM %s WHERE code=?"
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

        @Nullable BilibiliBindApi.BindCodeInfo queryByCode(int code) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryByCode();
            ps.setInt(1, code);
            final ResultSet resultSet = ps.executeQuery();
            return this.parseOne(resultSet);
        }

        @Nullable BilibiliBindApi.BindCodeInfo queryByUuid(@NotNull UUID uuid) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryByUuid();
            ps.setLong(1, uuid.getMostSignificantBits());
            ps.setLong(2, uuid.getLeastSignificantBits());
            final ResultSet resultSet = ps.executeQuery();
            return this.parseOne(resultSet);
        }

        int deleteByCode(int code) throws SQLException {
            final PreparedStatement ps = this.getStatementDeleteByCode();
            ps.setInt(1, code);
            return ps.executeUpdate();
        }

        int deleteTimeBefore(long time) throws SQLException {
            final PreparedStatement ps = this.getStatementDeleteTimeBefore();
            ps.setLong(1, time);
            return ps.executeUpdate();
        }


    }
}
