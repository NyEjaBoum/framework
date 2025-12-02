package framework.servlet;

import java.lang.reflect.Method;
import java.util.Map;

public class Router {
    
    public static RouteResult findRoute(String path, Map<String, Method> urlToMethod, Map<String, Method> urlPatternToMethod) {
        
        // 1. URL exacte
        if (urlToMethod != null && urlToMethod.containsKey(path)) {
            return new RouteResult(urlToMethod.get(path), new Object[0]);
        }

        // 2. Pattern dynamique
        if (urlPatternToMethod != null) {
            for (String pattern : urlPatternToMethod.keySet()) {
                String regex = pattern.replaceAll("\\{[^/]+\\}", "([^/]+)");
                if (path.matches(regex)) {
                    Method method = urlPatternToMethod.get(pattern);
                    Object[] args = extractArgs(path, pattern, method);
                    return new RouteResult(method, args);
                }
            }
        }

        return null; // Pas trouvé
    }

    private static Object[] extractArgs(String path, String pattern, Method method) {
        String[] pathParts = path.split("/");
        String[] patternParts = pattern.split("/");
        
        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                String value = pathParts[i];
                if (method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == int.class) {
                    return new Object[]{Integer.parseInt(value)};
                } else {
                    return new Object[]{value};
                }
            }
        }
        return new Object[0];
    }
}