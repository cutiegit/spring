package com.study.mvc.service;

import com.study.framework.annotation.Service;

/**
 * @Auther: xiangy@paraview.cn
 * @description:
 * @Date: 2019/08/27
 */
@Service
public class DemoService {

    public String getId(String name) {
        return "ID=" + name;
    }
}
