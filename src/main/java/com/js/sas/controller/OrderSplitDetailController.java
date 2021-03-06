package com.js.sas.controller;

import com.js.sas.service.OrderProductService;
import com.js.sas.service.OrderSplitDetailService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping("orderSplitDetail")
@Controller
public class OrderSplitDetailController {

    @Autowired
    private OrderSplitDetailService orderSplitDetailService;

    @RequestMapping(value = "getPage",method = RequestMethod.POST)
    @ResponseBody
    public Object OrderSplitDetail(HttpServletRequest request) {
        Map<String, String> requestMap = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        if (StringUtils.isNotBlank(request.getParameter("startDate"))) {
            requestMap.put("startDate", request.getParameter("startDate"));
        }
        if (StringUtils.isNotBlank(request.getParameter("endDate"))) {
            requestMap.put("endDate", request.getParameter("endDate"));
        }
        if (StringUtils.isNotBlank(request.getParameter("orderno"))){
            requestMap.put("orderno",request.getParameter("orderno"));
        }
        if (StringUtils.isNotBlank(request.getParameter("sort"))) {
            requestMap.put("sort", request.getParameter("sort").trim());
        }
        if (StringUtils.isNotBlank(request.getParameter("sortOrder"))) {
            requestMap.put("sortOrder", request.getParameter("sortOrder").trim());
        }
        if (StringUtils.isNotBlank(request.getParameter("limit"))) {
            requestMap.put("limit", request.getParameter("limit"));
        } else {
            requestMap.put("limit", "10");
        }
        if (StringUtils.isNotBlank(request.getParameter("offset"))) {
            requestMap.put("offset", request.getParameter("offset"));
        } else {
            requestMap.put("offset", "0");
        }
        List<Map<String, Object>> page = orderSplitDetailService.getPage(requestMap);
        result.put("rows", page);
        result.put("total", orderSplitDetailService.getCount(requestMap));
        return result;
    }

    @RequestMapping(value = "getBackPage",method = RequestMethod.POST)
    @ResponseBody
    public Object getBackPage(HttpServletRequest request) {
        Map<String, String> requestMap = new HashMap<>();
        Map<String, Object> result = new HashMap<>();

        requestMap.put("back", "back");
        if (StringUtils.isNotBlank(request.getParameter("startDate"))) {
            requestMap.put("startDate", request.getParameter("startDate"));
        }
        if (StringUtils.isNotBlank(request.getParameter("endDate"))) {
            requestMap.put("endDate", request.getParameter("endDate"));
        }
        if (StringUtils.isNotBlank(request.getParameter("orderno"))){
            requestMap.put("orderno",request.getParameter("orderno"));
        }
        if (StringUtils.isNotBlank(request.getParameter("sort"))) {
            requestMap.put("sort", request.getParameter("sort").trim());
        }
        if (StringUtils.isNotBlank(request.getParameter("sortOrder"))) {
            requestMap.put("sortOrder", request.getParameter("sortOrder").trim());
        }
        if (StringUtils.isNotBlank(request.getParameter("limit"))) {
            requestMap.put("limit", request.getParameter("limit"));
        } else {
            requestMap.put("limit", "10");
        }
        if (StringUtils.isNotBlank(request.getParameter("offset"))) {
            requestMap.put("offset", request.getParameter("offset"));
        } else {
            requestMap.put("offset", "0");
        }
        List<Map<String, Object>> page = orderSplitDetailService.getPage(requestMap);
        result.put("rows", page);
        result.put("total", orderSplitDetailService.getCount(requestMap));
        return result;
    }
    @RequestMapping(value = "unsendOrders",method = RequestMethod.POST)
    @ResponseBody
    public Object unsendOrders(HttpServletRequest request) {
        Map<String, String> requestMap = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        requestMap.put("unsend", "unsend");
        if (StringUtils.isNotBlank(request.getParameter("startDate"))) {
            requestMap.put("startDate", request.getParameter("startDate"));
        }
        if (StringUtils.isNotBlank(request.getParameter("endDate"))) {
            requestMap.put("endDate", request.getParameter("endDate"));
        }
        if (StringUtils.isNotBlank(request.getParameter("orderno"))){
            requestMap.put("orderno",request.getParameter("orderno"));
        }
        if (StringUtils.isNotBlank(request.getParameter("sort"))) {
            requestMap.put("sort", request.getParameter("sort").trim());
        }
        if (StringUtils.isNotBlank(request.getParameter("sortOrder"))) {
            requestMap.put("sortOrder", request.getParameter("sortOrder").trim());
        }
        if (StringUtils.isNotBlank(request.getParameter("limit"))) {
            requestMap.put("limit", request.getParameter("limit"));
        } else {
            requestMap.put("limit", "10");
        }
        if (StringUtils.isNotBlank(request.getParameter("offset"))) {
            requestMap.put("offset", request.getParameter("offset"));
        } else {
            requestMap.put("offset", "0");
        }
        List<Map<String, Object>> page = orderSplitDetailService.getPage(requestMap);
        result.put("rows", page);
        result.put("total", orderSplitDetailService.getCount(requestMap));
        return result;
    }

}
