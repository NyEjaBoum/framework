package framework.servlet;

import java.util.*;
import java.util.regex.*;

public class Router {
    public static RouteResult findRoute(String path, String httpMethod, Map<String, List<InfoUrl>> urlToInfoList) {
        // 1) exact
        if (urlToInfoList != null && urlToInfoList.containsKey(path)) {
            for (InfoUrl info : urlToInfoList.get(path)) {
                if (info.supportsMethod(httpMethod)) {
                    return new RouteResult(info.method, new HashMap<String, String>());
                }
            }
            // 405 si aucune méthode correspond
            Set<String> allowed = new HashSet<>();
            for (InfoUrl info : urlToInfoList.get(path)) allowed.addAll(info.httpMethods);
            return new RouteResult(null, allowed);
        }

        // 2) patterns
        for (String pattern : urlToInfoList.keySet()) {
            String regex = "^" + pattern.replaceAll("\\{[^/]+\\}", "([^/]+)") + "$";
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(path);
            if (m.matches()) {
                for (InfoUrl info : urlToInfoList.get(pattern)) {
                    if (info.supportsMethod(httpMethod)) {
                        // collect placeholder names in order
                        List<String> names = new ArrayList<>();
                        Matcher nameMatcher = Pattern.compile("\\{([^/]+)\\}").matcher(pattern);
                        while (nameMatcher.find()) names.add(nameMatcher.group(1));
                        Map<String, String> params = new HashMap<>();
                        for (int i = 0; i < names.size(); i++) params.put(names.get(i), m.group(i + 1));
                        return new RouteResult(info.method, params);
                    }
                }
                // 405 si aucune méthode correspond
                Set<String> allowed = new HashSet<>();
                for (InfoUrl info : urlToInfoList.get(pattern)) allowed.addAll(info.httpMethods);
                return new RouteResult(null, allowed);
            }
        }
        return null;
    }
}