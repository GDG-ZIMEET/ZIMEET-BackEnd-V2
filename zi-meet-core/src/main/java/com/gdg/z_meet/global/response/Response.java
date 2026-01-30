package com.gdg.z_meet.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"status", "code", "message", "data"})
public class Response<T> {

    private final HttpStatus status;
    private final String code;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T data;

    public static Response<Void> ok() {

        return new Response<>(Code.OK.getStatus(), Code.OK.getCode(), Code.OK.getMessage(), null);
    }

    public static <T> Response<T> ok(T data) {

        return new Response<>(Code.OK.getStatus(), Code.OK.getCode(), Code.OK.getMessage(), data);
    }

    public static <T> Response<T> of(BaseCode code, T data){

        return new Response<>(code.getReason().getStatus(),
                code.getReason().getCode(),
                code.getReason().getMessage(),
                data);
    }

    public static <T> Response<T> fail(BaseCode code) {

        return new Response<>(code.getReason().getStatus(),
                code.getReason().getCode(),
                code.getReason().getMessage(),
                null);
    }

    public static <T> Response<T> fail(BaseCode code, T data) {

        return new Response<>(code.getReason().getStatus(),
                code.getReason().getCode(),
                code.getReason().getMessage(),
                data);
    }

}
