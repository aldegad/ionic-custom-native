package io.ionic.starter.util;

import com.google.gson.annotations.SerializedName;

public class BleData {
    @SerializedName("name")
    public String name;
    @SerializedName("job")
    public String job;
    @SerializedName("id")
    public String id;
    @SerializedName("createdAt")
    public String createdAt;

    public BleData(String name, String job) {
        this.name = name;
        this.job = job;
    }
}
