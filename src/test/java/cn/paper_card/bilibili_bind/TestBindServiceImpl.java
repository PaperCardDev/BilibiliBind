package cn.paper_card.bilibili_bind;

import cn.paper_card.bilibili_bind.api.BindInfo;
import cn.paper_card.bilibili_bind.api.exception.AlreadyBoundException;
import cn.paper_card.bilibili_bind.api.exception.UidHasBeenBoundException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;

public class TestBindServiceImpl {

    private MyConnection connection;

    @Before
    public void setup() {
        connection = new MyConnection();
    }

    @After
    public void cleanup() {
        connection.close();
    }

    @Test
    public void test1() throws SQLException, UidHasBeenBoundException, AlreadyBoundException {

        final BindServiceImpl bindService = new BindServiceImpl(connection);

        final BindInfo testInfo = new BindInfo(
                UUID.randomUUID(),
                "Paper99",
                123456,
                "Test",
                System.currentTimeMillis()
        );

        // 先删除，确保没有重复
        bindService.removeBind(testInfo.uuid(), testInfo.uid());

        // 添加
        bindService.addBind(testInfo);

        // 按UUID查询
        final var info1 = bindService.queryByUuid(testInfo.uuid());
        Assert.assertEquals(testInfo, info1);

        // 按UID查询
        final var info2 = bindService.queryByUid(testInfo.uid());
        Assert.assertEquals(testInfo, info2);

        // 删除
        final boolean removed = bindService.removeBind(testInfo.uuid(), testInfo.uid());
        Assert.assertTrue(removed);

        bindService.close();
    }

    @Test
    public void test2() throws SQLException {
        final BindServiceImpl service = new BindServiceImpl(this.connection);
        service.close();
    }

    @Test
    public void testAlreadyBind() throws SQLException, UidHasBeenBoundException, AlreadyBoundException {
        final BindServiceImpl service = new BindServiceImpl(this.connection);

        final BindInfo test = new BindInfo(UUID.randomUUID(),
                "Paper99", new Random().nextLong(99999999), "Test", System.currentTimeMillis());

        // 先删除
        {
            final BindInfo tmp = service.queryByUuid(test.uuid());
            if (tmp != null) {
                final boolean removed = service.removeBind(tmp.uuid(), tmp.uid());
                Assert.assertTrue(removed);
            }

            final BindInfo tmp2 = service.queryByUid(test.uid());
            if (tmp2 != null) {
                final boolean removed = service.removeBind(tmp2.uuid(), tmp2.uid());
                Assert.assertTrue(removed);
            }
        }


        // 添加
        service.addBind(test);

        // 重复添加
        try {
            service.addBind(test);
            throw new RuntimeException("没有异常！");
        } catch (AlreadyBoundException e) {
            final BindInfo bindInfo = e.getBindInfo();
            Assert.assertEquals(test, bindInfo);
        }

        // 删除
        final boolean removed = service.removeBind(test.uuid(), test.uid());
        Assert.assertTrue(removed);

        service.close();
    }
}
