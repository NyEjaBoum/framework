package framework.servlet;

import java.lang.reflect.Method;

public class RouteResult {
    public Method method;
    public Object[] args;

    public RouteResult(Method method, Object[] args) {
        this.method = method;
        this.args = args;
    }
}