package com.github.xiyoo0812.gocd.webhook;

import com.github.xiyoo0812.gocd.Notifier;
import com.github.xiyoo0812.gocd.model.Message;

import com.thoughtworks.go.plugin.api.logging.Logger;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.HttpURLConnection;

import java.util.*;

public class WebhookNotifier implements Notifier {
    private static final Logger LOGGER = Logger.getLoggerFor(WebhookNotifier.class);
    private final String webhookUrl;
    private final String webhookType;
    private final Gson gson = new Gson();

    public WebhookNotifier(String webhookType, String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.webhookType = webhookType;
    }

    @Override
    public void sendMessage(String userEmail, Message message) {
        try {
            String body = buildBody(message);
            if (body != null) {
                HttpURLConnection connection = (HttpURLConnection) new URL(this.webhookUrl).openConnection();
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setDoInput(true);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
                writer.write(body);
                writer.close();

                String line;
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder result = new StringBuilder();
                while ((line = bufferedReader.readLine()) != null) {
                    result.append(line);
                }
                String response = result.toString();
                LOGGER.info("WebhookNotifier sendMessage: " + response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildBody(Message message) {
        String msgContent = buildContent(message);
        Map<String, Object> param = new HashMap();
        if (this.webhookType.equals("wechat")) {
            param.put("msgtype", "text");
            Map<String, Object> param_msg = new HashMap();
        	param_msg.put("content", msgContent);
            param.put("text", param_msg);
            return gson.toJson(param);
        }
        if (this.webhookType.equals("lark")) {
            param.put("msg_type", "text");
            Map<String, Object> param_msg = new HashMap();
        	param_msg.put("text", msgContent);
            param.put("content", param_msg);
            return gson.toJson(param);
        }
        if (this.webhookType.equals("dingding")) {
            param.put("msgtype", "text");
            Map<String, Object> param_msg = new HashMap();
        	param_msg.put("content", msgContent);
            param.put("text", param_msg);
            return gson.toJson(param);
        }
        return null;
    }

    private String buildContent(Message message) {
        StringBuilder sb = new StringBuilder();
        sb.append(message.text);
        sb.append("\n\n");
        if (message.link != null) {
            sb.append(message.link);
            sb.append("\n");
        }
        for (Message.Tag tag : message.tags) {
            sb.append(tag.name);
            sb.append(": ");
            sb.append(tag.value);
            sb.append("\n");
        }
        return sb.toString();
    }
}

