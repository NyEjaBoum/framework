package framework.servlet;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

public class RouteResult {
    public Method method;
    public Map<String, String> pathParams;
    public Set<String> allowedMethods; // null si OK

    public RouteResult(Method method, Map<String, String> pathParams) {
        this.method = method;
        this.pathParams = pathParams;
        this.allowedMethods = null;
    }

    public RouteResult(Method method, Set<String> allowedMethods) {
        this.method = method;
        this.allowedMethods = allowedMethods;
        this.pathParams = null;
    }
}