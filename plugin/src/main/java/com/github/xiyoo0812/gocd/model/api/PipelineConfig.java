package com.github.xiyoo0812.gocd.model.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PipelineConfig {
    @SerializedName("name")
    public String name;

    @SerializedName("group")
    public String group;

    @SerializedName("parameters")
    public List<Parameter> parameters;
    
    public static class Parameter {
        @SerializedName("name")
        public String name;

        @SerializedName("value")
        public String value;
    }
}
