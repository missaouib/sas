package com.js.sas.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import springfox.documentation.annotations.ApiIgnore;

/**
 * @ClassName SysteController
 * @Description 系统Controller
 * @Author zc
 * @Date 2019/6/18 15:00
 **/
@Controller
@ApiIgnore
public class SystemController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/settlementCustomerSummary")
    public String settlementCustomerSummary() {
        return "/pages/finance/settlementSummary";
    }

    @GetMapping("/customerStatement")
    public String customerStatement() {
        return "/pages/finance/customerStatement.html";
    }
}
