package framework.servlet;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import framework.annotation.Controller;
import framework.annotation.Url;

public class Scanner {
    // Une seule map : URL complète -> méthode
    public Map<String, Method> urlToMethod = new HashMap<>();

    // Map pour les patterns dynamiques : "/etudiant/{id}" -> méthode
    public Map<String, Method> urlPatternToMethod = new HashMap<>();

    public void scanControllers(File dir, String packageName) throws Exception {
        if (!dir.exists()) return;
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                String nextPackage = packageName.isEmpty() ? file.getName() : packageName + "." + file.getName();
                scanControllers(file, nextPackage);
            } else if (file.getName().endsWith(".class") && !packageName.isEmpty()) {
                processClassFile(file, packageName);
            }
        }
    }
    
    private void processClassFile(File file, String packageName) throws Exception {
        String className = file.getName().replace(".class", "");
        Class<?> clazz = Class.forName(packageName + "." + className);
        
        if (clazz.isAnnotationPresent(Controller.class)) {
            Controller ctrlAnn = clazz.getAnnotation(Controller.class);
            String baseUrl = ctrlAnn.value();
            
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Url.class)) {
                    Url ann = method.getAnnotation(Url.class);
                    String fullUrl = baseUrl + ann.value();
                    if (fullUrl.contains("{") && fullUrl.contains("}")) {
                        urlPatternToMethod.put(fullUrl, method);
                    } else {
                        urlToMethod.put(fullUrl, method);
                    }
                }
            }
        }
    }
}