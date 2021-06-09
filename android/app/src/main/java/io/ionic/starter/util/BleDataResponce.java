package io.ionic.starter.util;

import com.google.gson.annotations.SerializedName;

class BleDataResponce {
    @SerializedName("name")
    public String name;
    @SerializedName("job")
    public String job;
    @SerializedName("id")
    public String id;
    @SerializedName("createdAt")
    public String createdAt;
}
