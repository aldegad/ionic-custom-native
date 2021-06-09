package io.ionic.starter.util;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface APIInterface {


    @POST("api/users")
    default Call<BleData> sendBleData(@Body BleData bdata) {
        return null;
    }

//    @GET("api/users?")
//    Call<UserList> doGetUserList(@Query("page") String page);

//    @FormUrlEncoded
//    @POST("api/users?")
//    Call<UserList> doCreateUserWithField(@Field("name") String name, @Field("job") String job);
}
