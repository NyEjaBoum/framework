package framework.servlet;

import java.lang.reflect.Method;
import java.util.Map;

public class RouteResult {
    public Method method;
    public Map<String,String> pathParams;

    public RouteResult(Method method, Map<String,String> pathParams) {
        this.method = method;
        this.pathParams = pathParams;
    }
}