package com.github.xiyoo0812.gocd.model;

import com.google.gson.Gson;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.github.xiyoo0812.utils.StringUtils.isBlank;

public class Message {
    public final String name;
    public final String text;
    public final String link;
    public final Type type;
    public final List<Tag> tags;

    public static class Tag {
        public final String name;
        public final String value;
        public final boolean isShort;

        private Tag(String name, String value, boolean isShort) {
            this.name = name;
            this.value = value;
            this.isShort = isShort;
        }
    }

    private Message(String name, String text, String link, Type type, List<Tag> tags) {
        this.name = name;
        this.text = text;
        this.link = link;
        this.type = type;
        this.tags = tags;
    }

    public enum Type {
        GOOD, NEUTRAL, BAD
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    public static class Builder {
        private final List<Tag> tags = new LinkedList<>();
        private String name;
        private String text;
        private String link;
        private Type type;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder link(String link) {
            this.link = link;
            return this;
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder tag(String name, String value) {
            return tag(name, value, true);
        }

        public Builder tag(String name, String value, boolean isShort) {
            if (!isBlank(name) && !isBlank(value)) {
                this.tags.add(new Tag(name, value, isShort));
            }
            return this;
        }

        public Builder longTag(String name, String value) {
            return tag(name, value, false);
        }

        public Message build() {
            return new Message(name, text, link,
                type != null ? type : Type.NEUTRAL,
                Collections.unmodifiableList(tags));
        }
    }
}
