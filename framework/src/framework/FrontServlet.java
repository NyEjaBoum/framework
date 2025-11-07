package framework;  

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class FrontServlet extends HttpServlet {

    RequestDispatcher defaultDispatcher;
    private Scanner scanner = new Scanner();

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        try {
            String classesPath = getServletContext().getRealPath("/WEB-INF/classes");
            scanner.scanControllers(new File(classesPath), "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        try {
            if (scanner.urlToMethod.containsKey(path)) {
                Method method = scanner.urlToMethod.get(path);
                Class<?> controllerClass = method.getDeclaringClass();
                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                method.invoke(controllerInstance);
                res.setContentType("text/plain");
                res.getWriter().println("Méthode appelée pour l'URL : " + path);
            } else {
                res.setContentType("text/plain");
                res.getWriter().println("URL non supportée : " + path);
            }
        } catch (Exception e) {
            res.setContentType("text/plain");
            res.getWriter().println("Erreur lors de l'appel de la méthode : " + e.getMessage());
        }
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            String uri = req.getRequestURI();
            String responseBody = """
                <html>
                    <head><title>Resource Not Found</title></head>
                    <body>
                        <h1>Ressource inconnue</h1>
                        <p>La ressource demandée n'a pas été trouvée : <strong>%s</strong></p>
                        <p><em>Intercepté par le Framework Servlet</em></p>
                    </body>
                </html>
                """.formatted(uri);

            out.println(responseBody);
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}