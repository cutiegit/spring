package com.study.framework.servlet;

import com.study.framework.annotation.Autowired;
import com.study.framework.annotation.Controller;
import com.study.framework.annotation.RequestMapping;
import com.study.framework.annotation.Service;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @Auther: xiangy@paraview.cn
 * @description:
 * @Date: 2019/08/27
 */
public class DispatcherServlet extends HttpServlet {


    private static final long serialVersionUID = -3027359512372275307L;
    private static final String LOCATION = "contextConfigLocation";
    public static Properties p = new Properties();
    private List<String> classNames = new ArrayList<String>();
    private Map<String, Object> ioc = new HashMap<String, Object>();
    private Map<String, Method> handlerMapping = new HashMap<String, Method>();

    public DispatcherServlet() {
        super();
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1、加载配置文件

        doLoadConfig(config.getInitParameter(LOCATION));

        // 2、扫描相关类
        doScanner(p.getProperty("scalaPackage"));

        // 3、实例化所有类并加入到IoC容器
        doInstance();

        // 4、依赖注入
        doAutoWired();

        // 5、处理器映射
        initHandlerMapping();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

//     1、从请求中获取url,与映射器中保存的url进行比对，查找对应的方法
//     2、从请求中获取请求参数，并根据方法类型对请求的值重新处理，保存到数组集合中；
//     3、方法调用invoke回调，传入该方法所有类的实例对象和发送请求的值集合，然后继续执行。

    protected void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (this.handlerMapping.isEmpty()) return;
        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath();
        requestURI = requestURI.replace(contextPath, "").replaceAll("/+", "/");
        if (!this.handlerMapping.containsKey(requestURI)) {
            resp.getWriter().write("404 Not Found!!");
            return;
        }
        Map<String, String[]> requestParameterMap = req.getParameterMap();
        Method controllerMethod = this.handlerMapping.get(requestURI);
        Class<?>[] controllerMethodParameterTypes = controllerMethod.getParameterTypes();
        Object[] methodParamValues = new Object[controllerMethodParameterTypes.length];
        // 将请求的参数值与controller方法参数值绑定,通过遍历bean的setter方法来绑定关系，这里简单处理
        // 请求集合 :<name:zs>
        // 对应方法 : HttpServletRequest request,HttpServletResponse response,String name
        // 绑定后结果: result[]=[request,response,nameValue] ,方法回调invoke(bean,result[])使得方法被执行

        for (int i = 0; i < controllerMethodParameterTypes.length; i++) {
            Class controllerMethodParameterType = controllerMethodParameterTypes[i];
            if (controllerMethodParameterType == HttpServletRequest.class) {
                methodParamValues[i] = req;
                continue;
            } else if (controllerMethodParameterType == HttpServletResponse.class) {
                methodParamValues[i] = resp;
                continue;
                // 将请求的参数绑定到方法的参数上去
            } else if (controllerMethodParameterType == String.class) {
                for (Map.Entry<String, String[]> param : requestParameterMap.entrySet()) {
                    String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "")
                            .replaceAll(",\\s", ",");
                    methodParamValues[i] = value;
                }
            }
        }
        try {
            String beanName = lowerFirstCase(controllerMethod.getDeclaringClass().getSimpleName());
            controllerMethod.invoke(this.ioc.get(beanName), methodParamValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    // 加载配置文件
    private void doLoadConfig(String parameter) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(parameter);
        try {
            p.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (resourceAsStream != null) {
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 扫描所有类
    private void doScanner(String packageName) {
        URL resource = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File classDir = new File(resource.getFile());
        for (File classFile : classDir.listFiles()) {
            if (classFile.isDirectory()) {
                doScanner(packageName + "." + classFile.getName());
            } else {
                String className = (packageName + "." + classFile.getName()).replace(".class", "").trim();
                classNames.add(className);
            }
        }
    }

    // 类实例化
    private void doInstance() {
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    //Controller处理< action,new Action()>
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    // Service 处理,servcie 注解是否有值，有值则将该值加入IoC，没值就默认首字母小写
                    Service service = clazz.getAnnotation(Service.class);
                    String beanName = service.value();
                    if ("".equals(beanName.trim())) {
                        ioc.put(lowerFirstCase(clazz.getSimpleName()), clazz.newInstance());
                        continue;
                    }
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(), clazz.newInstance());
                    }
                } else {
                    continue;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    // 属性注入
    private void doAutoWired() {
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 获取实例的所有属性
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                // 如果属性加了autoWired注解,需要从IoC容器取得已实例化的实例并应用该单例(使用setter方法)
                if (field.isAnnotationPresent(Autowired.class)) {
                    Autowired autowired = field.getAnnotation(Autowired.class);
                    String beanName = autowired.value();
                    if ("".equals(beanName)) {
                        beanName = lowerFirstCase(field.getType().getSimpleName());
                    }
                    // 设置private权限
                    field.setAccessible(true);
                    try {
                        field.set(entry.getValue(), ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    // 对IoC容器中所有的Controller注解过的实例的 ,由RequestMapping注解的Url路径与Method进行关联
    private void initHandlerMapping() {
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> aClass = entry.getValue().getClass();
            if (!aClass.isAnnotationPresent(Controller.class)) continue;

            //被Controller注解，被RequestMapping注解
            String baseUrl = "";
            if (aClass.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping requestMapping = aClass.getAnnotation(RequestMapping.class);
                baseUrl = requestMapping.value();
            }

            Method[] methods = aClass.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                    String url = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                    handlerMapping.put(url, method);
                    System.out.println("mapped" + url + "," + method);
                }
            }
        }
    }

    private String lowerFirstCase(String str) {
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }
}
