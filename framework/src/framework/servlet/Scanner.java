package framework.servlet;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

import framework.annotation.Controller;
import framework.annotation.Url;
import framework.annotation.GetMethod;
import framework.annotation.PostMethod;

public class Scanner {
    // Map URL → List<InfoUrl>
    public Map<String, List<InfoUrl>> urlToInfoList = new HashMap<>();

    public void scanControllers(File dir, String packageName) throws Exception {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String nextPackage = packageName.isEmpty() ? file.getName() : packageName + "." + file.getName();
                scanControllers(file, nextPackage);
            } else if (file.getName().endsWith(".class") && !packageName.isEmpty()) {
                String className = file.getName().replace(".class", "");
                Class<?> clazz = Class.forName(packageName + "." + className);

                if (clazz.isAnnotationPresent(Controller.class)) {
                    String baseUrl = clazz.getAnnotation(Controller.class).value();

                    for (Method method : clazz.getDeclaredMethods()) {
                        Set<String> httpMethods = new HashSet<>();
                        String url = null;

                        if (method.isAnnotationPresent(GetMethod.class)) {
                            url = baseUrl + method.getAnnotation(GetMethod.class).value();
                            httpMethods.add("GET");
                        }
                        if (method.isAnnotationPresent(PostMethod.class)) {
                            url = baseUrl + method.getAnnotation(PostMethod.class).value();
                            httpMethods.add("POST");
                        }
                        if (method.isAnnotationPresent(Url.class)) {
                            url = baseUrl + method.getAnnotation(Url.class).value();
                            httpMethods.add("GET");
                            httpMethods.add("POST");
                        }

                        if (url != null) {
                            InfoUrl info = new InfoUrl(url, method, httpMethods);
                            urlToInfoList.computeIfAbsent(url, k -> new ArrayList<>()).add(info);
                        }
                    }
                }
            }
        }
    }
}