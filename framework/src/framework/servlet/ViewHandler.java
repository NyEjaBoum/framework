package framework.servlet;

// filepath: framework/src/framework/servlet/ViewHandler.java

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import framework.view.ModelView;
import java.io.IOException;
import java.util.Map;

public class ViewHandler {

    public static void handle(ModelView mv, HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        String viewPath = mv.getView();
        if (viewPath == null || viewPath.isEmpty()) {
            sendPlain(res, "Erreur: Aucune vue spécifiée dans ModelView", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        // Passer les données à la requête
        if (mv.getData() != null) {
            for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                req.setAttribute(entry.getKey(), entry.getValue());
            }
        }

        // Essayer de forward vers une JSP (préfixe "/" pour dispatcher)
        try {
            RequestDispatcher dispatcher = req.getServletContext().getRequestDispatcher("/" + viewPath);
            if (dispatcher != null) {
                dispatcher.forward(req, res);
                return;
            }
        } catch (Exception ex) {
            // fallback vers texte simple si forward échoue
        }

        // Fallback : afficher texte simple
        sendPlain(res, "Vue non trouvée: " + viewPath, HttpServletResponse.SC_NOT_FOUND);
    }

    public static void handleString(String text, HttpServletResponse res) throws IOException {
        sendPlain(res, text, HttpServletResponse.SC_OK);
    }

    public static void handleNotFound(String path, HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        // essayer JSP dédiée /WEB-INF/error/404.jsp
        req.setAttribute("path", path);
        try {
            RequestDispatcher dispatcher = req.getServletContext().getRequestDispatcher("/WEB-INF/error/404.jsp");
            if (dispatcher != null) {
                dispatcher.forward(req, res);
                return;
            }
        } catch (Exception e) {
            // ignore et fallback
        }

        // fallback texte
        sendPlain(res, "URL non supportée: " + path, HttpServletResponse.SC_NOT_FOUND);
    }

    public static void handleException(Exception ex, String path, HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        // log simple
        ex.printStackTrace();

        // essayer JSP dédiée /WEB-INF/error/500.jsp
        req.setAttribute("exception", ex);
        req.setAttribute("path", path);
        try {
            RequestDispatcher dispatcher = req.getServletContext().getRequestDispatcher("/WEB-INF/error/500.jsp");
            if (dispatcher != null) {
                dispatcher.forward(req, res);
                return;
            }
        } catch (Exception e) {
            // fallback
        }

        // fallback texte
        String message = "Erreur serveur pour " + path + " : " + ex.getMessage();
        sendPlain(res, message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    private static void sendPlain(HttpServletResponse res, String message, int status) throws IOException {
        res.setStatus(status);
        res.setContentType("text/plain; charset=UTF-8");
        res.getWriter().println(message);
    }
}