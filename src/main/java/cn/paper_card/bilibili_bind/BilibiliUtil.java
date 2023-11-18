package cn.paper_card.bilibili_bind;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class BilibiliUtil {

    record Reply(
            long uid,
            String name,
            int level,
            boolean isVip,
            String message,
            long time
    ) {
    }

    record VideoInfo(
            long aid,
            String title,
            long ownerId,
            String ownerName
    ) {
    }

    private final Gson gson = new Gson();

    private @NotNull Reply parseReplyJson(@NotNull JsonObject jsonObject) {
        final long uid = jsonObject.get("mid").getAsLong();
        final long time = jsonObject.get("ctime").getAsLong() * 1000L;

        final JsonObject member = jsonObject.get("member").getAsJsonObject();


        final String name = member.get("uname").getAsString();
        final int level = member.get("level_info").getAsJsonObject().get("current_level").getAsInt();
        final boolean isVip = member.get("vip").getAsJsonObject().get("vipStatus").getAsInt() > 0;

        final String message = jsonObject.get("content").getAsJsonObject().get("message").getAsString();

        return new Reply(uid, name, level, isVip, message, time);
    }

    private static void close(@NotNull InputStream inputStream, @NotNull InputStreamReader inputStreamReader, @NotNull BufferedReader reader) throws IOException {
        IOException exception = null;
        try {
            reader.close();
        } catch (IOException e) {
            exception = e;
        }

        try {
            inputStreamReader.close();
        } catch (IOException e) {
            exception = e;
        }

        try {
            inputStream.close();
        } catch (IOException e) {
            exception = e;
        }

        if (exception != null) throw exception;
    }

    private static @NotNull String readContent(@NotNull HttpURLConnection connection) throws IOException {
        final InputStream inputStream = connection.getInputStream();
        final InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        String line;

        final StringBuilder builder = new StringBuilder();

        try {
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
                builder.append('\n');
            }
        } catch (IOException e) {
            try {
                close(inputStream, inputStreamReader, bufferedReader);
            } catch (IOException ignored) {
            }
            throw e;
        }

        close(inputStream, inputStreamReader, bufferedReader);

        return builder.toString();
    }

    // 根据bvid请求aid
    @NotNull VideoInfo requestVideoByBvid(@NotNull String bvid) throws Exception {
        final String link = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;

        final URL url;

        try {
            url = new URL(link);
        } catch (MalformedURLException e) {
            throw new Exception(e);
        }

        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("host", "api.bilibili.com");
        connection.setRequestProperty("user-agent", "Postcat");

        final String content;
        try {
            content = readContent(connection);
        } catch (IOException e) {
            connection.disconnect();
            throw e;
        }

//        System.out.println("DEBUG: " + content);
        final JsonObject jsonObject = this.gson.fromJson(content, JsonObject.class);
        final int code = jsonObject.get("code").getAsInt();
        final String msg = jsonObject.get("message").getAsString();
        if (code != 0) throw new Exception(msg);

        final JsonObject data = jsonObject.get("data").getAsJsonObject();
        final long aid = data.get("aid").getAsLong();
        final String title = data.get("title").getAsString();

        final JsonObject owner = data.get("owner").getAsJsonObject();
        final long ownerId = owner.get("mid").getAsLong();
        final String ownerName = owner.get("name").getAsString();

        return new VideoInfo(aid, title, ownerId, ownerName);
    }

    @NotNull List<Reply> requestLatestReplies(long aid) throws Exception {
//        https://api.bilibili.com/x/v2/reply?jsonp=jsonp&pn=1&ps=8&type=1&oid=620947279&sort=0

        final String link = "https://api.bilibili.com/x/v2/reply?jsonp=jsonp&pn=1&ps=8&type=1&sort=0&oid=" + aid;

        final URL url;

        try {
            url = new URL(link);
        } catch (MalformedURLException e) {
            throw new Exception(e);
        }

        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestProperty("host", "api.bilibili.com");
        connection.setRequestProperty("user-agent", "Postcat");

        final String content;
        try {
            content = readContent(connection);
        } catch (IOException e) {
            connection.disconnect();
            throw e;
        }
        connection.disconnect();

        // 解析JSON
        final ArrayList<Reply> list = new ArrayList<>();

        final JsonObject jsonObject = this.gson.fromJson(content, JsonObject.class);
        final JsonArray repliesJson = jsonObject.get("data").getAsJsonObject().get("replies").getAsJsonArray();
        for (JsonElement jsonElement : repliesJson) {
            final JsonObject replyJson = jsonElement.getAsJsonObject();
            final Reply reply = this.parseReplyJson(replyJson);
            list.add(reply);
        }
        return list;
    }
}
