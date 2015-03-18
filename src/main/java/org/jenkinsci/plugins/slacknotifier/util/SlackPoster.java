package org.jenkinsci.plugins.slacknotifier.util;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import hudson.ProxyConfiguration;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;

public class SlackPoster {

    private static final Logger logger = Logger.getLogger(SlackPoster.class.getName());

    private String webHookEndpoint = "";

    public SlackPoster(String webhookEndpoint) {
        super();
        this.webHookEndpoint = webhookEndpoint;
    }

    public void publish(JSONObject json) {
            String url = webHookEndpoint;
            logger.info("Posting to " + webHookEndpoint);
            HttpClient client = getHttpClient();
            PostMethod post = new PostMethod(url);

            try {

                post.addParameter("payload", json.toString());
                post.getParams().setContentCharset("UTF-8");
                int responseCode = client.executeMethod(post);
                String response = post.getResponseBodyAsString();
                if(responseCode != HttpStatus.SC_OK) {
                    logger.log(Level.WARNING, "Slack post may have failed. Response: " + response);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error posting to Slack", e);
            } finally {
                logger.info("Posting succeeded");
                post.releaseConnection();
            }
    }

    private HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
        if (Jenkins.getInstance() != null) {
            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
            if (proxy != null) {
                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
                String username = proxy.getUserName();
                String password = proxy.getPassword();
                // Consider it to be passed if username specified. Sufficient?
                if (username != null && !"".equals(username.trim())) {
                    logger.info("Using proxy authentication (user=" + username + ")");
                    // http://hc.apache.org/httpclient-3.x/authentication.html#Proxy_Authentication
                    // and
                    // http://svn.apache.org/viewvc/httpcomponents/oac.hc3x/trunk/src/examples/BasicAuthenticationExample.java?view=markup
                    client.getState().setProxyCredentials(AuthScope.ANY,
                            new UsernamePasswordCredentials(username, password));
                }
            }
        }
        return client;
    }

}