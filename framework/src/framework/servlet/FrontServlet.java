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

public class FrontServlet extends HttpServlet {

    RequestDispatcher defaultDispatcher;

    @Override
    public void init() {
        System.out.println("=== INITIALISATION FRAMEWORK ===");
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        try {
            String classesPath = getServletContext().getRealPath("/WEB-INF/classes");
            Scanner scanner = new Scanner();
            scanner.scanControllers(new File(classesPath), "");
            getServletContext().setAttribute("urlToMethod", scanner.urlToMethod);
            getServletContext().setAttribute("urlPatternToMethod", scanner.urlPatternToMethod);

            System.out.println("Framework initialisé. URLs: " + scanner.urlToMethod.keySet());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        Map<String, Method> urlToMethod = (Map<String, Method>) getServletContext().getAttribute("urlToMethod");
        Map<String, Method> urlPatternToMethod = (Map<String, Method>) getServletContext().getAttribute("urlPatternToMethod");

        try {
            // déléguer routing au Router
            RouteResult route = Router.findRoute(path, urlToMethod, urlPatternToMethod);
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

                    // 1. VariableChemin
                    if (p.isAnnotationPresent(VariableChemin.class)) {
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

                Object result = method.invoke(controllerInstance, invokedArgs);
                handleResult(result, path, req, res);
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
}