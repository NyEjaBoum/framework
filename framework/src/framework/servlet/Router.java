package framework.servlet;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Router {
    public static RouteResult findRoute(String path, Map<String, Method> urlToMethod, Map<String, Method> urlPatternToMethod) {

        // 1) exact
        if (urlToMethod != null && urlToMethod.containsKey(path)) {
            return new RouteResult(urlToMethod.get(path), new HashMap<String,String>());
        }

        // 2) patterns
        if (urlPatternToMethod != null) {
            for (String pattern : urlPatternToMethod.keySet()) {
                String regex = "^" + pattern.replaceAll("\\{[^/]+\\}", "([^/]+)") + "$";
                Pattern p = Pattern.compile(regex);
                Matcher m = p.matcher(path);
                if (m.matches()) {
                    Method method = urlPatternToMethod.get(pattern);
                    // collect placeholder names in order
                    List<String> names = new ArrayList<>();
                    Matcher nameMatcher = Pattern.compile("\\{([^/]+)\\}").matcher(pattern);
                    while (nameMatcher.find()) names.add(nameMatcher.group(1));
                    Map<String,String> params = new HashMap<>();
                    for (int i = 0; i < names.size(); i++) params.put(names.get(i), m.group(i + 1));
                    return new RouteResult(method, params);
                }
            }
        }
        return null;
    }
}