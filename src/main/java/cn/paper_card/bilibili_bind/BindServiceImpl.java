package cn.paper_card.bilibili_bind;

import cn.paper_card.database.api.DatabaseApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import cn.paper_card.bilibili_bind.BilibiliBindApi.BindInfo;
import cn.paper_card.bilibili_bind.BilibiliBindApi.AlreadyBindException;
import cn.paper_card.bilibili_bind.BilibiliBindApi.UidHasBeenBindException;

class BindServiceImpl implements BilibiliBindApi.BindService {

    record Cached(
            BindInfo info,
            long time
    ) {

    }

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private BindTable table = null;
    private Connection connection = null;

    private final @NotNull ConcurrentHashMap<UUID, Cached> cacheByUuid;
    private final @NotNull ConcurrentHashMap<Long, Cached> cacheByUid;

    BindServiceImpl(DatabaseApi.@NotNull MySqlConnection mySqlConnection) {
        this.mySqlConnection = mySqlConnection;
        this.cacheByUuid = new ConcurrentHashMap<>();
        this.cacheByUid = new ConcurrentHashMap<>();
    }

    private @NotNull BindTable getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRawConnection();

        if (this.connection != null && this.connection == newCon) return this.table;

        // 清除缓存
        this.clearCache();

        if (this.table != null) this.table.close();
        this.connection = newCon;
        this.table = new BindTable(newCon);
        return this.table;
    }

    void close() throws SQLException {
        // 清除缓存
        this.clearCache();

        synchronized (this.mySqlConnection) {
            final BindTable t = this.table;
            this.table = null;
            this.connection = null;

            if (t != null) t.close();
        }
    }

    void clearCache() {
        this.cacheByUuid.clear();
        this.cacheByUid.clear();
    }

    @Override
    public void addBind(@NotNull BindInfo info) throws AlreadyBindException, UidHasBeenBindException, SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final BindTable t = this.getTable();

                // 检查是否已经绑定
                {
                    final BindInfo info1 = t.queryByUuid(info.uuid());
                    if (info1 != null) throw new AlreadyBindException(info1);
                }

                // 检查UID是否已经被绑定
                {
                    final BindInfo info1 = t.queryByUid(info.uid());
                    if (info1 != null) throw new UidHasBeenBindException(info1);
                }

                final int inserted = t.insert(info);
                this.mySqlConnection.setLastUseTime();

                // 清除旧的缓存
                this.cacheByUuid.remove(info.uuid());
                this.cacheByUid.remove(info.uid());

                if (inserted != 1) throw new RuntimeException("插入了%d条数据".formatted(inserted));
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
    public boolean removeBind(@NotNull UUID uuid, long uid) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final BindTable t = this.getTable();

                final int deleted = t.deleteByUuidAndUid(uuid, uid);
                this.mySqlConnection.setLastUseTime();

                if (deleted == 1) {
                    // 清除缓存
                    this.cacheByUuid.remove(uuid);
                    this.cacheByUid.remove(uid);

                    return true;
                }

                if (deleted == 0) return false;

                throw new RuntimeException("删除了%d条数据！".formatted(deleted));

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
    public @Nullable BindInfo queryByUuid(@NotNull UUID uuid) throws SQLException {

        // 从缓存
        final Cached cached = this.cacheByUuid.get(uuid);
        if (cached != null) return cached.info();

        synchronized (this.mySqlConnection) {
            try {
                final BindTable t = this.getTable();
                final BindInfo info = t.queryByUuid(uuid);
                this.mySqlConnection.setLastUseTime();

                // 放入缓存
                this.cacheByUuid.put(uuid, new Cached(info, System.currentTimeMillis()));

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
    public @Nullable BindInfo queryByUid(long uid) throws SQLException {

        // 从缓存
        final Cached cached = this.cacheByUid.get(uid);
        if (cached != null) return cached.info();

        synchronized (this.mySqlConnection) {
            try {
                final BindTable t = this.getTable();
                final BindInfo info = t.queryByUid(uid);
                this.mySqlConnection.setLastUseTime();

                // 放入缓存
                this.cacheByUid.put(uid, new Cached(info, System.currentTimeMillis()));

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
}
