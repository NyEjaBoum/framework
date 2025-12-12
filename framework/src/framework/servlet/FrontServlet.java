package framework.servlet;  

import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.io.File;
import java.util.Map;
import java.lang.reflect.Parameter;
import framework.annotation.ParametreRequete;
import framework.annotation.VariableChemin;
import framework.view.ModelView;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashMap;

public class FrontServlet extends HttpServlet {

    RequestDispatcher defaultDispatcher;
    private Map<String, List<InfoUrl>> urlToInfoList; // Ajouté

    @Override
    public void init() {
        System.out.println("=== INITIALISATION FRAMEWORK ===");
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        try {
            String classesPath = getServletContext().getRealPath("/WEB-INF/classes");
            Scanner scanner = new Scanner();
            scanner.scanControllers(new File(classesPath), "");
            urlToInfoList = scanner.urlToInfoList; // Correction : stocker la map dans l'attribut
            getServletContext().setAttribute("urlToInfoList", urlToInfoList);

            System.out.println("Framework initialisé. URLs: " + urlToInfoList.keySet());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String httpMethod = req.getMethod();

        // Correction : récupérer la map depuis l'attribut ou l'attribut de classe
        Map<String, List<InfoUrl>> urlToInfoList = (Map<String, List<InfoUrl>>) getServletContext().getAttribute("urlToInfoList");
        if (urlToInfoList == null) urlToInfoList = this.urlToInfoList;

        try {
            // déléguer routing au Router
            RouteResult route = Router.findRoute(path, httpMethod, urlToInfoList);
            Method method = null;
            Map<String,String> pathParams = Collections.emptyMap();
            if (route != null) {
                method = route.method;
                pathParams = route.pathParams != null ? route.pathParams : Collections.emptyMap();
            }

            if (method != null) {
                Class<?> controllerClass = method.getDeclaringClass();
                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();

                // construire args pour l'appel : priorité -> @ParametreRequete -> valeurs d'URL (args) -> req.getParameter(nom)
                Parameter[] params = method.getParameters();
                Object[] invokedArgs = new Object[params.length];

                for (int i = 0; i < params.length; i++) {
                    Parameter p = params[i];
                    Class<?> type = p.getType();
                    Object value = null;

                    // Injection automatique de tous les paramètres de requête dans Map<String,Object>
                    if (Map.class.isAssignableFrom(type)) {
                        Map<String, Object> paramMap = new HashMap<>();
                        Map<String, String[]> paramValues = req.getParameterMap();
                        for (Map.Entry<String, String[]> entry : paramValues.entrySet()) {
                            String key = entry.getKey();
                            String[] vals = entry.getValue();
                            if (vals == null) continue;
                            if (vals.length == 1) {
                                paramMap.put(key, vals[0]);
                            } else {
                                paramMap.put(key, vals);
                            }
                        }
                        value = paramMap;
                    }
                    // 1. VariableChemin
                    else if (p.isAnnotationPresent(VariableChemin.class)) {
                        VariableChemin ann = p.getAnnotation(VariableChemin.class);
                        String key = ann.value().isEmpty() ? p.getName() : ann.value();
                        String s = pathParams.get(key);
                        if (s != null) value = convertString(s, type);
                        if (value == null && ann.required()) {
                            throw new IllegalArgumentException("Paramètre de chemin manquant: " + key + " (type " + type.getSimpleName() + ")");
                        }
                    }
                    // 2. ParametreRequete
                    else if (p.isAnnotationPresent(ParametreRequete.class)) {
                        ParametreRequete ann = p.getAnnotation(ParametreRequete.class);
                        String key = ann.value().isEmpty() ? p.getName() : ann.value();
                        String s = req.getParameter(key);
                        if (s != null) value = convertString(s, type);
                        if (value == null && ann.required()) {
                            throw new IllegalArgumentException("Paramètre de requête manquant: " + key + " (type " + type.getSimpleName() + ")");
                        }
                    }
                    // 3. Aucun annotation : priorité chemin > requête
                    else {
                        String key = p.getName();
                        if (pathParams.containsKey(key)) {
                            String s = pathParams.get(key);
                            if (s != null) value = convertString(s, type);
                        }
                        if (value == null) {
                            String s = req.getParameter(key);
                            if (s != null) value = convertString(s, type);
                        }
                        if (value == null && type.isPrimitive()) {
                            throw new IllegalArgumentException("Paramètre manquant: " + key + " (type " + type.getSimpleName() + ")");
                        }
                    }
                    invokedArgs[i] = value;
                }

                req.setAttribute("calledMethod", method);

                Object result = method.invoke(controllerInstance, invokedArgs);
                handleResult(result, path, req, res);
            } else if (route != null && route.allowedMethods != null) {
                // 405 Method Not Allowed
                res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                res.setHeader("Allow", String.join(", ", route.allowedMethods));
                // Correction : ViewHandler.show405 doit exister
                ViewHandler.show405(path, route.allowedMethods, req, res);
                return;
            } else {
                // déléguer la réponse 404 à ViewHandler (limite HTML ici)
                ViewHandler.handleNotFound(path, req, res);
            }

        } catch (Exception e) {
            // déléguer la gestion d'erreur à ViewHandler
            ViewHandler.handleException(e, path, req, res);
        }
    }

    // Traiter le résultat retourné par la méthode
    private void handleResult(Object result, String path, HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        if (result == null) {
            res.setContentType("text/plain; charset=UTF-8");
            res.getWriter().println("Méthode exécutée: " + path);
            return;
        }

        if (result instanceof String) {
            res.setContentType("text/plain; charset=UTF-8");
            res.getWriter().println("Résultat: " + result);
            return;
        }

        if (result instanceof ModelView) {
            ViewHandler.handle((ModelView) result, req, res);
            return;
        }

        // Ajout : gestion JSON
        Method calledMethod = (Method) req.getAttribute("calledMethod");
        boolean isJson = false;
        if (calledMethod != null && calledMethod.isAnnotationPresent(framework.annotation.Json.class)) {
            isJson = true;
        }

        if (isJson) {
            res.setContentType("application/json; charset=UTF-8");
            String json = toJsonResponse(result);
            res.getWriter().write(json);
            return;
        }

        // Autres types (int, etc.)
        res.setContentType("text/plain; charset=UTF-8");
        res.getWriter().println("Résultat (" + result.getClass().getSimpleName() + "): " + result);
    }

    private Object convertString(String s, Class<?> target) {
        if (s == null) return null;
        if (target == String.class) return s;
        if (target == int.class || target == Integer.class) return Integer.parseInt(s);
        if (target == long.class || target == Long.class) return Long.parseLong(s);
        if (target == boolean.class || target == Boolean.class) return Boolean.parseBoolean(s);
        // ajouter selon besoin
        return s;
    }

    // Ajoute une méthode utilitaire pour formatter la réponse JSON
    private String toJsonResponse(Object result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"statut\": \"success\",\n");
        sb.append("  \"code\": 200,\n");
        if (result instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) result;
            sb.append("  \"count\": ").append(list.size()).append(",\n");
            sb.append("  \"data\": ").append(listToJson(list)).append("\n");
        } else {
            sb.append("  \"data\": ").append(objectToJson(result)).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private String listToJson(java.util.List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(objectToJson(list.get(i)));
            if (i < list.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    private String objectToJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return "\"" + obj.toString() + "\"";
        }
        // Simple POJO to JSON (fields only, no nested objects)
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            fields[i].setAccessible(true);
            try {
                sb.append("\"").append(fields[i].getName()).append("\": ");
                Object val = fields[i].get(obj);
                if (val == null) sb.append("null");
                else if (val instanceof String) sb.append("\"").append(val).append("\"");
                else sb.append(val.toString());
            } catch (Exception e) {
                sb.append("\"error\"");
            }
            if (i < fields.length - 1) sb.append(", ");
        }
        sb.append("}");
        return sb.toString();
    }
}