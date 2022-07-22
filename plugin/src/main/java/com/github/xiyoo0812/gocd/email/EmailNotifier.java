package com.github.xiyoo0812.gocd.email;

import com.github.xiyoo0812.gocd.Notifier;
import com.github.xiyoo0812.gocd.model.Message;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;

import static com.github.xiyoo0812.utils.StringUtils.isBlank;
import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml3;

public class EmailNotifier implements Notifier {
    private final String hostname;
    private final int port;
    private final String username;
    private final String password;
    private final boolean ssl;
    private final String from;
    private final String cc;

    public EmailNotifier(String hostname, int port, String username, String password, boolean ssl, String from, String cc) {
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
        this.ssl = ssl;
        this.from = from;
        this.cc = cc;
    }

    @Override
    public void sendMessage(String userEmail, Message message) {
        String html = buildHtml(message);

        try {
            Email email = new HtmlEmail();
            email.setHostName(hostname);
            email.setSmtpPort(port);
            email.setCharset("UTF-8");
            if (!isBlank(username)) {
                email.setAuthenticator(new DefaultAuthenticator(username, password));
            }
            email.setSSLOnConnect(ssl);
            email.setFrom(from);
            email.setSubject("pipeline build message");
            email.setMsg(html);
            email.addTo(userEmail);
            if (!isBlank(cc)) {
                for (String cc : this.cc.split("\\s*[,;]\\s*")) {
                    email.addCc(cc);
                }
            }
            email.send();

        } catch (EmailException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildHtml(Message message) {
        StringBuilder tags = new StringBuilder();
        int rowSpan = 1;
        for (Message.Tag tag : message.tags) {
            if (rowSpan > 1) {
                tags.append("</tr><tr>");
            }
            tags.append("<td width=\"100\"><b>");
            tags.append(escapeHtml3(tag.name));
            tags.append("</b><br/>");
            tags.append(escapeHtml3(tag.value).replace("\n", "<br/>").replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;"));
            tags.append("</td>");
            rowSpan++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"width: 100%;\"><tr><td colSpan=\"3\">");
        sb.append(escapeHtml3(message.text));
        sb.append("</td></tr><tr><td colSpan=\"3\">&nbsp;</td></tr><tr><td rowSpan=\"");
        sb.append(rowSpan + (message.link != null ? 1 : 0));
        sb.append("\" style=\"background-color: ");
        sb.append(message.type == Message.Type.GOOD ? "#36a64f" : message.type == Message.Type.BAD ? "#d00000" : "#E8E8E8");
        sb.append(";\" width=\"1\"></td>");
        if (message.link != null) {
            sb.append("<td colSpan=\"2\"><a href=\"");
            sb.append(message.link);
            sb.append("\">");
            sb.append(escapeHtml3(message.link));
            sb.append("</a></td>");
        }
        sb.append("</tr>");
        if (tags.length() > 0) {
            sb.append("<tr>");
            sb.append(tags);
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }
}
