package framework.servlet;

import java.lang.reflect.Method;
import java.util.Set;

public class InfoUrl {
    public String urlPattern;
    public Method method;
    public Set<String> httpMethods; // ex: ["GET", "POST"]

    public InfoUrl(String urlPattern, Method method, Set<String> httpMethods) {
        this.urlPattern = urlPattern;
        this.method = method;
        this.httpMethods = httpMethods;
    }

    public boolean supportsMethod(String httpMethod) {
        return httpMethods.contains(httpMethod);
    }
}