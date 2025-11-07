package framework;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class Scanner {
    public Map<String, Method> urlToMethod = new HashMap<>();
    public Map<String, Class<?>> urlToController = new HashMap<>();

    public void scanControllers(File dir, String packageName) throws Exception {
        if (!dir.exists()) return;
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                String nextPackage = packageName.isEmpty() ? file.getName() : packageName + "." + file.getName();
                scanControllers(file, nextPackage);
            } else if (file.getName().endsWith(".class")) {
                String className = file.getName().replace(".class", "");
                if (packageName.isEmpty()) continue;
                Class<?> clazz = Class.forName(packageName + "." + className);
                if (clazz.isAnnotationPresent(framework.annotation.Controller.class)) {
                    framework.annotation.Controller ctrlAnn = clazz.getAnnotation(framework.annotation.Controller.class);
                    String baseUrl = ctrlAnn.value();
                    urlToController.put(baseUrl, clazz);
                    for (Method m : clazz.getDeclaredMethods()) {
                        if (m.isAnnotationPresent(framework.annotation.MyAnnotation.class)) {
                            framework.annotation.MyAnnotation ann = m.getAnnotation(framework.annotation.MyAnnotation.class);
                            String fullUrl = baseUrl + ann.value();
                            urlToMethod.put(fullUrl, m);
                        }
                    }
                }
            }
        }
    }
}