package com.joblens.resume;

import com.joblens.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class UnreadableResumeException extends ApiException {

    public UnreadableResumeException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
