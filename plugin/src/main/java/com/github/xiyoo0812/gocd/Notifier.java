package com.github.xiyoo0812.gocd;
import com.github.xiyoo0812.gocd.model.Message;

public interface Notifier {
    void sendMessage(String userEmail, Message message);
}
