package com.heima.user.controller.v1;

import com.heima.model.common.dtos.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/test")
public class TestController {

    @GetMapping("")
    public ResponseResult test() {
        return ResponseResult.okResult("test");
    }
}
