package com.playerPlugin.playerTaskX.web;

public class ApiResponse<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = 0;
        resp.msg = "ok";
        resp.data = data;
        return resp;
    }

    public static <T> ApiResponse<T> error(int code, String msg) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = code;
        resp.msg = msg;
        return resp;
    }

    public int getCode() { return code; }
    public String getMsg() { return msg; }
    public T getData() { return data; }
}
