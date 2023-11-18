package cn.paper_card.bilibili_bind;

import cn.paper_card.database.DatabaseApi;
import cn.paper_card.database.DatabaseConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

class BindCodeApiImpl implements BilibiliBindApi.BindCodeApi {
    BindCodeApiImpl(@NotNull BilibiliBind plugin) {
        this.mySqlConnection = plugin.getDatabaseApi().getRemoteMySqlDb().getConnectionUnimportant();
    }

    private BindCodeTable table = null;
    private Connection connection = null;

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private int playerCount = -1;

    private static final long MAX_ALIVE_TIME = 5 * 60 * 1000L;

    private @NotNull BindCodeTable getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRowConnection();
        if (this.connection == null) {
            this.connection = newCon;
            if (this.table != null) this.table.close();
            this.table = new BindCodeTable(this.connection);
            this.playerCount = this.table.queryCount();
            return this.table;
        } else if (this.connection == newCon) {
            return this.table;
        } else {
            this.connection = newCon;
            if (this.table != null) this.table.close();
            this.table = new BindCodeTable(this.connection);
            this.playerCount = this.table.queryCount();
            return this.table;
        }
    }

    private int randomCode() {
        final int min = 1;
        final int max = 999999;
        return new Random().nextInt(max - min + 1) + min;
    }


    @Override
    public int createCode(@NotNull UUID uuid, @NotNull String name) throws Exception {
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

                    this.playerCount += inserted;
                    if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));
                    return code;
                }
                if (updated == 1) return code;
                throw new Exception("根据一个UUID更新了多条数据！");
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    // 取出一个绑定验证码
    @Override
    public @Nullable BilibiliBindApi.BindCodeInfo takeByCode(int code) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final BindCodeTable t = this.getTable();
                final List<BilibiliBindApi.BindCodeInfo> list = t.queryByCode(code);
                this.mySqlConnection.setLastUseTime();

                final int size = list.size();

                if (size == 1) {
                    final int deleted = t.deleteByCode(code);
                    this.mySqlConnection.setLastUseTime();

                    this.playerCount -= deleted;

                    if (deleted != 1) throw new Exception("删除了%d条数据！".formatted(deleted));


                    final BilibiliBindApi.BindCodeInfo info = list.get(0);

                    // 判断验证码是否过期
                    final long delta = System.currentTimeMillis() - info.time();
                    if (delta > MAX_ALIVE_TIME) return null;
                    return info;
                }

                if (size == 0) return null;

                throw new Exception("根据一个验证码查询到%d条数据！".formatted(size));

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public @Nullable BilibiliBindApi.BindCodeInfo takeByUuid(@NotNull UUID uuid) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final BindCodeTable t = this.getTable();
                final List<BilibiliBindApi.BindCodeInfo> list = t.queryByUuid(uuid);
                this.mySqlConnection.setLastUseTime();

                final int size = list.size();
                if (size == 0) return null;
                if (size == 1) {
                    final BilibiliBindApi.BindCodeInfo info = list.get(0);

                    // 删除
                    final int deleted = t.deleteByCode(info.code());
                    this.mySqlConnection.setLastUseTime();

                    if (deleted != 1) throw new Exception("删除了%d条数据！".formatted(deleted));

                    // 检查是否过期
                    // 判断验证码是否过期
                    final long delta = System.currentTimeMillis() - info.time();
                    if (delta > MAX_ALIVE_TIME) return null;

                    return info;
                }

                throw new Exception("根据一个UUID查询到了%d条数据！".formatted(size));

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public int cleanOutdated() throws SQLException {
        final long begin = System.currentTimeMillis() - MAX_ALIVE_TIME;
        synchronized (this.mySqlConnection) {
            try {
                final BindCodeTable t = this.getTable();
                final int deleted = t.deleteTimeBefore(begin);
                this.playerCount -= deleted;
                return deleted;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public int getCodeCount() {
        synchronized (this.mySqlConnection) {
            return this.playerCount;
        }
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


    static class BindCodeTable {

        private PreparedStatement statementInsert = null;

        private PreparedStatement statementUpdateByUuid = null;

        private PreparedStatement statementQueryByCode = null;

        private PreparedStatement statementDeleteByCode = null;

        private PreparedStatement statementQueryPlayerCount = null;

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
            DatabaseConnection.createTable(connection, sql2);
        }

        void close() throws SQLException {
            DatabaseConnection.closeAllStatements(this.getClass(), this);
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
                        UPDATE %s SET code=?, name=?, time=? WHERE uid1=? AND uid2=?
                        """.formatted(NAME));
            }

            return this.statementUpdateByUuid;
        }

        private @NotNull PreparedStatement getStatementQueryByCode() throws SQLException {
            if (this.statementQueryByCode == null) {
                this.statementQueryByCode = this.connection.prepareStatement("SELECT code, uid1, uid2, name, time FROM %s WHERE code=?".formatted(NAME));
            }
            return this.statementQueryByCode;
        }

        private @NotNull PreparedStatement getStatementQueryPlayerCount() throws SQLException {
            if (this.statementQueryPlayerCount == null) {
                this.statementQueryPlayerCount = this.connection.prepareStatement("SELECT count(*) FROM %s".formatted(NAME));
            }
            return this.statementQueryPlayerCount;
        }

        private @NotNull PreparedStatement getStatementDeleteByCode() throws SQLException {
            if (this.statementDeleteByCode == null) {
                this.statementDeleteByCode = this.connection.prepareStatement("DELETE FROM %s WHERE code=?".formatted(NAME));
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
                        ("SELECT code, uid1, uid2, name, time FROM %s WHERE uid1=? AND uid2=?".formatted(NAME));
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

        private @NotNull List<BilibiliBindApi.BindCodeInfo> parse(@NotNull ResultSet resultSet) throws SQLException {
            final LinkedList<BilibiliBindApi.BindCodeInfo> list = new LinkedList<>();
            try {
                // "SELECT code, uid1, uid2, name, time FROM %s WHERE code=?"
                while (resultSet.next()) {
                    final int code = resultSet.getInt(1);
                    final long uid1 = resultSet.getLong(2);
                    final long uid2 = resultSet.getLong(3);
                    final String name = resultSet.getString(4);
                    final long time = resultSet.getLong(5);
                    list.add(new BilibiliBindApi.BindCodeInfo(code, new UUID(uid1, uid2), name, time));
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

        @NotNull List<BilibiliBindApi.BindCodeInfo> queryByCode(int code) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryByCode();
            ps.setInt(1, code);
            final ResultSet resultSet = ps.executeQuery();
            return this.parse(resultSet);
        }

        int queryCount() throws SQLException {
            final ResultSet resultSet = this.getStatementQueryPlayerCount().executeQuery();

            final int c;

            try {
                if (resultSet.next()) {
                    c = resultSet.getInt(1);
                } else throw new SQLException("不应该没有数据！");

                if (resultSet.next()) throw new SQLException("不应该还有数据！");
            } catch (SQLException e) {
                try {
                    resultSet.close();
                } catch (SQLException ignored) {
                }

                throw e;
            }

            resultSet.close();
            return c;
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

        @NotNull List<BilibiliBindApi.BindCodeInfo> queryByUuid(@NotNull UUID uuid) throws SQLException {
            final PreparedStatement ps = this.getStatementQueryByUuid();
            ps.setLong(1, uuid.getMostSignificantBits());
            ps.setLong(2, uuid.getLeastSignificantBits());
            final ResultSet resultSet = ps.executeQuery();
            return this.parse(resultSet);
        }
    }
}
