package framework.servlet;

// filepath: framework/src/framework/servlet/ViewHandler.java

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import framework.view.ModelView;
import java.io.IOException;
import java.util.Map;
import java.io.PrintWriter;
import java.util.Set;

public class ViewHandler {

    public static void handle(ModelView mv, HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        String viewPath = mv.getView();
        if (viewPath == null || viewPath.isEmpty()) {
            renderHtml(res, "Erreur ModelView", "<div class='error'>Aucune vue spécifiée dans ModelView</div>");
            return;
        }

        // Passer les données à la requête (pour JSP si besoin)
        if (mv.getData() != null) {
            for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                req.setAttribute(entry.getKey(), entry.getValue());
            }
        }
        // Passer le message à la requête
        if (mv.getMessage() != null) {
            req.setAttribute("message", mv.getMessage());
        }

        // Essayer de forward vers la JSP
        RequestDispatcher dispatcher = req.getServletContext().getRequestDispatcher("/" + viewPath);
        if (dispatcher != null) {
            dispatcher.forward(req, res);
            return;
        }

        // Fallback : afficher le nom de la vue et les données en HTML
        StringBuilder body = new StringBuilder();
        body.append("<h2>Vue demandée : ").append(escape(viewPath)).append("</h2>");
        if (mv.getMessage() != null) {
            body.append("<div class='info'>").append(escape(mv.getMessage())).append("</div>");
        }
        if (mv.getData() != null && !mv.getData().isEmpty()) {
            body.append("<h3>Données transmises :</h3><ul>");
            for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                body.append("<li><b>").append(escape(entry.getKey())).append("</b> : ")
                    .append(escape(String.valueOf(entry.getValue()))).append("</li>");
            }
            body.append("</ul>");
        }
        renderHtml(res, "ModelView", body.toString());
    }

    public static void handleString(String text, HttpServletResponse res) throws IOException {
        renderHtml(res, "Résultat String", "<pre>" + escape(text) + "</pre>");
    }

    public static void handleNotFound(String path, HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setStatus(HttpServletResponse.SC_NOT_FOUND); // mettre AVANT d'écrire dans la réponse
        String body = "<h2 class='error'>404 - Non trouvé</h2>"
            + "<p>L'URL demandée n'existe pas :</p>"
            + "<code>" + escape(path) + "</code>"
            + "<hr><p><a href='/test'>Retour à l'accueil</a></p>";
        renderHtml(res, "404 - Non trouvé", body);
    }

    public static void handleException(Exception ex, String path, HttpServletRequest req, HttpServletResponse res) throws IOException {
        StringBuilder body = new StringBuilder();
        body.append("<div class='error'><h2>Erreur serveur</h2>")
            .append("<p><b>").append(escape(ex.getMessage())).append("</b></p>")
            .append("<pre>").append(escape(stackTraceToString(ex))).append("</pre>")
            .append("</div>")
            .append("<p>URL : <code>").append(escape(path)).append("</code></p>");
        renderHtml(res, "Erreur serveur", body.toString());
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    public static void show405(String path, Set<String> allowed, HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        String body = "<h2 class='error'>405 - Method Not Allowed</h2>"
            + "<p>Méthodes autorisées : <b>" + String.join(", ", allowed) + "</b></p>"
            + "<code>" + escape(path) + "</code>";
        renderHtml(res, "405 - Method Not Allowed", body);
    }

    private static void renderHtml(HttpServletResponse res, String title, String bodyContent) throws IOException {
        res.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"fr\">");
            out.println("<head>");
            out.println("<meta charset=\"UTF-8\">");
            out.println("<title>" + escape(title) + "</title>");
            out.println("<style>body{font-family:Arial,sans-serif;margin:40px;background:#f8f9fa;color:#333;}"
                + ".container{max-width:900px;margin:auto;padding:30px;background:white;border-radius:10px;box-shadow:0 4px 12px rgba(0,0,0,0.1);}"
                + "h1,h2{color:#2c3e50;}.error{color:#e74c3c;background:#ffeaa7;padding:15px;border-radius:8px;border-left:5px solid #e74c3c;}"
                + ".info{background:#ecf0f1;padding:12px;border-radius:6px;margin:10px 0;}pre{background:#2c3e50;color:#1abc9c;padding:15px;border-radius:8px;overflow-x:auto;}"
                + "a{color:#0984e3;text-decoration:none;}a:hover{text-decoration:underline;}</style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<div class=\"container\">");
            out.println("<h1>" + escape(title) + "</h1>");
            out.println(bodyContent);
            out.println("</div>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String stackTraceToString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}