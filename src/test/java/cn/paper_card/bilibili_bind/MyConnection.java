package cn.paper_card.bilibili_bind;

import cn.paper_card.database.api.DatabaseApi;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class MyConnection implements DatabaseApi.MySqlConnection {

    private long lastUse = 0;
    private int count = 0;

    private Connection connection = null;

    @Override
    public long getLastUseTime() {
        return this.lastUse;
    }

    @Override
    public void setLastUseTime() {
        this.lastUse = System.currentTimeMillis();
    }

    @Override
    public @NotNull Connection getRawConnection() throws SQLException {
        if (this.connection == null) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new SQLException(e);
            }
            //3、获取数据库的连接对象
            this.connection = DriverManager.getConnection("jdbc:mysql://localhost/test", "root", "qwer4321");
            ++this.count;
            return this.connection;
        }

        return this.connection;
    }

    @Override
    public int getConnectCount() {
        return this.count;
    }

    @Override
    public void testConnection() {
    }

    @Override
    public void close() {

    }

    @Override
    public void handleException(@NotNull SQLException e) {

    }
}
