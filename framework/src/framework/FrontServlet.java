package framework;

import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/") // attrape seulement la racine
public class FrontServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/html");
        PrintWriter out = resp.getWriter();

        String path = req.getRequestURI();
        
        out.println("<html><body>");
        out.println("<h1>Page d'accueil du Framework</h1>");
        out.println("<p><strong>Framework intercepté à la racine !</strong></p>");
        out.println("<p>L'URL demandée : <code>" + path + "</code></p>");
        out.println("<p>Cette requête a été interceptée par le FrontServlet du framework.</p>");
        out.println("<hr>");
        out.println("<small>Généré par le framework JAR</small>");
        out.println("</body></html>");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }
}