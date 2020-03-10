package com.ztbsuper.dingding;

import com.alibaba.fastjson.JSONObject;
import hudson.EnvVars;
import hudson.ProxyConfiguration;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Created by Marvin on 16/10/8.
 */
public class DingdingServiceImpl implements DingdingService {

    private Logger logger = LoggerFactory.getLogger(DingdingService.class);

    private String jenkinsURL;

    private boolean onStart;

    private boolean onSuccess;

    private boolean onFailed;

    private boolean onAbort;

    private TaskListener listener;

    private AbstractBuild build;

    private static final String apiUrl = "https://oapi.dingtalk.com/robot/send?access_token=";

    private String api;

    private EnvVars env;

    public DingdingServiceImpl(String jenkinsURL, String token, boolean onStart, boolean onSuccess, boolean onFailed,
        boolean onAbort, TaskListener listener, AbstractBuild build) {
        this.jenkinsURL = jenkinsURL;
        this.onStart = onStart;
        this.onSuccess = onSuccess;
        this.onFailed = onFailed;
        this.onAbort =  onAbort;
        this.listener = listener;
        this.build = build;
        this.api = apiUrl + token;
        try {
            this.env = build.getEnvironment(listener);
        } catch (Exception e) {
            this.env = new EnvVars();
            this.env.put("SCM_CHANGELOG", "new EnvVars");
        }
    }

    @Override
    public void start() {
        String pic = ""; // http://icon-park.com/imagefiles/loading7_gray.gif";
        String branch = env.expand("$branch") == null ? env.expand("$BRANCH") : env.expand("$branch");
        String user = env.expand("$BUILD_USER") == null ? "未知用户" : env.expand("$BUILD_USER"); // 依赖插件：build-user-vars-plugin
        String title = String.format("%s%s开始构建，分支：%s，打包人：%s", build.getProject().getDisplayName(), build.getDisplayName(),
            branch, user);
        String content = String.format("项目[%s%s]**开始构建**", build.getProject().getDisplayName(), build.getDisplayName());
        String link = getBuildUrl();
        if (onStart) {
            logger.info("send link msg from " + listener.toString());
            sendLinkMessage(link, content, title, pic);
        }

    }

    private String getBuildUrl() {
        if (jenkinsURL.endsWith("/")) {
            return jenkinsURL + build.getUrl();
        } else {
            return jenkinsURL + "/" + build.getUrl();
        }
    }

    @Override
    public void success() {
        String branch = env.expand("$branch") == null ? env.expand("$BRANCH") : env.expand("$branch");
        String user = env.expand("$BUILD_USER") == null ? "未知用户" : env.expand("$BUILD_USER"); // 依赖插件：build-user-vars-plugin
        String pic = "http://icons.iconarchive.com/icons/paomedia/small-n-flat/1024/sign-check-icon.png";
        String title = String.format("%s%s构建成功，分支：%s，打包人：%s", build.getProject().getDisplayName(), build.getDisplayName(),
            branch, user);
        String content = String.format("项目[%s%s]构建成功, summary:%s, duration:%s",
            build.getProject().getDisplayName(),
            build.getDisplayName(), build.getBuildStatusSummary().message, build.getDurationString());
        String changeContent = env.expand("$SCM_CHANGELOG") == null ? "NO CHANGE" : env.expand("$SCM_CHANGELOG").replaceAll("\\\\n", "\n");
        String commitContent = String.format("项目[%s%s]构建成功 \n\n分支：%s \n\nCOMMITS:\n\n%s", build.getProject().getDisplayName(),
            build.getDisplayName(), branch, changeContent);

        String link = getBuildUrl();
        logger.info(link);
        if (onSuccess) {
            logger.info("send link msg from " + listener.toString());
            sendLinkMessage(link, content, title, pic);
            sendMarkDownMessage(title, commitContent);
        }
    }

    @Override
    public void failed() {
        String branch = env.expand("$branch") == null ? env.expand("$BRANCH") : env.expand("$branch");
        String user = env.expand("$BUILD_USER") == null ? "未知用户" : env.expand("$BUILD_USER"); // 依赖插件：build-user-vars-plugin
        String pic = "http://www.iconsdb.com/icons/preview/soylent-red/x-mark-3-xxl.png";
        String title = String.format("%s%s构建失败，分支：%s，打包人：%s", build.getProject().getDisplayName(), build.getDisplayName(),
            branch, user);
        String content = String.format("项目[%s%s]构建失败, summary:%s, duration:%s", build.getProject().getDisplayName(), build.getDisplayName(), build.getBuildStatusSummary().message, build.getDurationString());

        String link = getBuildUrl();
        logger.info(link);
        if (onFailed) {
            logger.info("send link msg from " + listener.toString());
            sendLinkMessage(link, content, title, pic);
        }
    }

    @Override
    public void abort() {
        String branch = env.expand("$branch") == null ? env.expand("$BRANCH") : env.expand("$branch");
        String user = env.expand("$BUILD_USER") == null ? "未知用户" : env.expand("$BUILD_USER"); // 依赖插件：build-user-vars-plugin
        String pic = "http://www.iconsdb.com/icons/preview/soylent-red/x-mark-3-xxl.png";
        String title = String.format("%s%s构建中断，分支：%s，打包人：%s", build.getProject().getDisplayName(), build.getDisplayName(),
            branch, user);
        String content = String.format("项目[%s%s]构建中断, summary:%s, duration:%s", build.getProject().getDisplayName(),
            build.getDisplayName(), build.getBuildStatusSummary().message, build.getDurationString());

        String link = getBuildUrl();
        logger.info(link);
        if (onAbort) {
            logger.info("send link msg from " + listener.toString());
            sendLinkMessage(link, content, title, pic);
        }
    }

    private void sendMarkDownMessage(String title, String msg) {
        JSONObject body = new JSONObject();
        body.put("msgtype", "markdown");
        JSONObject markdownObject = new JSONObject();
        markdownObject.put("title", title);
        markdownObject.put("text", msg);
        body.put("markdown", markdownObject);
        doPost(body);
    }

    private void sendTextMessage(String msg) {
        JSONObject body = new JSONObject();
        body.put("msgtype", "text");
        JSONObject textObject = new JSONObject();
        textObject.put("content", msg);
        body.put("text", textObject);

        doPost(body);
    }

    private void doPost(JSONObject body) {
        HttpClient client = getHttpClient();
        PostMethod post = new PostMethod(api);
        try {
            post.setRequestEntity(new StringRequestEntity(body.toJSONString(), "application/json", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            logger.error("build request error", e);
        }
        try {
            client.executeMethod(post);
            logger.info(post.getResponseBodyAsString());
        } catch (IOException e) {
            logger.error("send msg error", e);
        }
        post.releaseConnection();
    }

    private void sendLinkMessage(String link, String msg, String title, String pic) {
        JSONObject body = new JSONObject();
        body.put("msgtype", "link");
        JSONObject linkObject = new JSONObject();
        linkObject.put("text", msg);
        linkObject.put("title", title);
        linkObject.put("picUrl", pic);
        linkObject.put("messageUrl", link);
        body.put("link", linkObject);

        doPost(body);
    }

    private HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null && jenkins.proxy != null) {
            ProxyConfiguration proxy = jenkins.proxy;
            if (proxy != null && client.getHostConfiguration() != null) {
                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
                String username = proxy.getUserName();
                String password = proxy.getPassword();
                // Consider it to be passed if username specified. Sufficient?
                if (username != null && !"".equals(username.trim())) {
                    logger.info("Using proxy authentication (user=" + username + ")");
                    client.getState().setProxyCredentials(AuthScope.ANY,
                            new UsernamePasswordCredentials(username, password));
                }
            }
        }
        return client;
    }
}
