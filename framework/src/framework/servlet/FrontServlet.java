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
import framework.view.ModelView;

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

        try {
            if (urlToMethod != null && urlToMethod.containsKey(path)) {
                Method method = urlToMethod.get(path);
                Class<?> controllerClass = method.getDeclaringClass();
                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                Object result = method.invoke(controllerInstance);
                // passer req pour pouvoir forwarder si ModelView
                handleResult(result, path, req, res);
            } else {
                res.setContentType("text/html; charset=UTF-8");
                res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                res.getWriter().println("<h1>URL non supportée: " + path + "</h1>");
            }
        } catch (Exception e) {
            res.setContentType("text/plain");
            res.getWriter().println("Erreur: " + e.getMessage());
            e.printStackTrace();
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
            ModelView mv = (ModelView) result;
            String viewPath = mv.getView();
            if (viewPath == null || viewPath.isEmpty()) {
                res.setContentType("text/plain; charset=UTF-8");
                res.getWriter().println("Erreur: Aucune vue spécifiée dans ModelView");
                return;
            }
            RequestDispatcher dispatcher = getServletContext().getRequestDispatcher("/" + viewPath);
            if (dispatcher != null) {
                dispatcher.forward(req, res);
            } else {
                res.setContentType("text/plain; charset=UTF-8");
                res.getWriter().println("Erreur: Vue non trouvée - " + viewPath);
            }
            return;
        }

        // Autres types (int, etc.)
        res.setContentType("text/plain; charset=UTF-8");
        res.getWriter().println("Résultat (" + result.getClass().getSimpleName() + "): " + result);
    }
}