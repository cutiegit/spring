package com.study.mvc.action;

import com.study.framework.annotation.Autowired;
import com.study.framework.annotation.Controller;
import com.study.framework.annotation.RequestMapping;
import com.study.framework.annotation.RequestParam;
import com.study.mvc.service.DemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @Auther: xiangy@paraview.cn
 * @description:
 * @Date: 2019/08/27
 */

@Controller
@RequestMapping("/demo")
public class DemoAction {

    @Autowired
    private DemoService service;

    @RequestMapping("/getId")
    public void fun(HttpServletRequest request, HttpServletResponse response, @RequestParam("name") String name) {
        String id = service.getId(name);
        try {
            response.getWriter().write(id);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
