package framework.servlet;  

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
import jakarta.servlet.http.Part;
import jakarta.servlet.annotation.MultipartConfig;

@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,      // 1 MB
    maxFileSize = 1024 * 1024 * 10,       // 10 MB
    maxRequestSize = 1024 * 1024 * 50     // 50 MB
)
public class FrontServlet extends HttpServlet {

    RequestDispatcher defaultDispatcher;
    private Map<String, List<InfoUrl>> urlToInfoList;
    private Path uploadRoot; // Dossier uploads créé à l'init

    @Override
    public void init() {
        System.out.println("=== INITIALISATION FRAMEWORK ===");
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        
        // Créer le dossier uploads dans le webapp (ex: /opt/tomcat10/webapps/test/uploads)
        try {
            String webappPath = getServletContext().getRealPath("/");
            if (webappPath != null) {
                uploadRoot = Paths.get(webappPath, "uploads");
                Files.createDirectories(uploadRoot);
                System.out.println("[FrontServlet] Dossier uploads créé : " + uploadRoot.toAbsolutePath());
            } else {
                System.err.println("[FrontServlet] ERREUR: Impossible de déterminer le chemin du webapp");
            }
        } catch (Exception e) {
            System.err.println("[FrontServlet] ERREUR création dossier uploads: " + e.getMessage());
            e.printStackTrace();
        }
        
        try {
            String classesPath = getServletContext().getRealPath("/WEB-INF/classes");
            Scanner scanner = new Scanner();
            scanner.scanControllers(new File(classesPath), "");
            urlToInfoList = scanner.urlToInfoList;
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

        Map<String, List<InfoUrl>> urlToInfoList = (Map<String, List<InfoUrl>>) getServletContext().getAttribute("urlToInfoList");
        if (urlToInfoList == null) urlToInfoList = this.urlToInfoList;

        try {
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

                Parameter[] params = method.getParameters();
                Object[] invokedArgs = new Object[params.length];

                // Récupération des fichiers uploadés et sauvegarde sur disque
                Map<String, byte[]> fileMap = new HashMap<>();
                Map<String, String> savedPaths = new HashMap<>();

                // Utiliser le uploadRoot créé à l'init (dans le webapp)
                // Si null, tenter de le recréer
                if (uploadRoot == null) {
                    String webappPath = req.getServletContext().getRealPath("/");
                    if (webappPath != null) {
                        uploadRoot = Paths.get(webappPath, "uploads");
                        try {
                            Files.createDirectories(uploadRoot);
                        } catch (Exception ex) {
                            System.err.println("[FrontServlet] ERREUR création uploadRoot: " + ex.getMessage());
                            uploadRoot = null;
                        }
                    }
                }
                
                System.out.println("[FrontServlet] ========== UPLOAD DEBUG ==========");
                System.out.println("[FrontServlet] uploadRoot = " + (uploadRoot != null ? uploadRoot.toAbsolutePath() : "null"));
                System.out.println("[FrontServlet] Content-Type = " + req.getContentType());
                if (uploadRoot != null) {
                    System.out.println("[FrontServlet] uploadRoot exists = " + Files.exists(uploadRoot));
                    System.out.println("[FrontServlet] uploadRoot writable = " + Files.isWritable(uploadRoot));
                }

                if (req.getContentType() != null && req.getContentType().toLowerCase().startsWith("multipart/")) {
                    System.out.println("[FrontServlet] Requête multipart détectée, traitement des parts...");
                    try {
                        for (Part part : req.getParts()) {
                            String submitted = part.getSubmittedFileName();
                            System.out.println("[FrontServlet] Part: name=" + part.getName() + ", submittedFileName=" + submitted + ", size=" + part.getSize());
                            
                            if (submitted != null && !submitted.isEmpty()) {
                                // safe filename (basename)
                                String safeName = Paths.get(submitted).getFileName().toString();
                                try {
                                    if (uploadRoot != null && Files.isWritable(uploadRoot)) {
                                        String unique = System.currentTimeMillis() + "_" + safeName;
                                        Path target = uploadRoot.resolve(unique);
                                        System.out.println("[FrontServlet] Saving file to: " + target.toAbsolutePath());
                                        
                                        try (InputStream in = part.getInputStream()) {
                                            long copied = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                                            System.out.println("[FrontServlet] File saved: " + target + " (" + copied + " bytes)");
                                        }
                                        
                                        byte[] bytes = Files.readAllBytes(target);
                                        fileMap.put(part.getName(), bytes);
                                        savedPaths.put(part.getName(), target.toString());
                                        System.out.println("[FrontServlet] SUCCESS: File saved to disk: " + target);
                                    } else {
                                        System.out.println("[FrontServlet] uploadRoot is null or not writable, reading file into memory only");
                                        byte[] bytes = part.getInputStream().readAllBytes();
                                        fileMap.put(part.getName(), bytes);
                                    }
                                } catch (Exception e) {
                                    System.err.println("[FrontServlet] ERREUR sauvegarde fichier: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[FrontServlet] ERREUR getParts(): " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("[FrontServlet] Pas de contenu multipart (contentType=" + req.getContentType() + ")");
                }
                
                System.out.println("[FrontServlet] fileMap size = " + fileMap.size());
                System.out.println("[FrontServlet] savedPaths = " + savedPaths);
                System.out.println("[FrontServlet] ========== END UPLOAD DEBUG ==========");

                // rendre accessible pour le binding et pour la JSP
                req.setAttribute("fileMap", fileMap);
                req.setAttribute("uploadedPaths", savedPaths);

                for (int i = 0; i < params.length; i++) {
                    Parameter p = params[i];
                    Class<?> type = p.getType();
                    Object value = null;

                    // Injection du Map des fichiers
                    if (Map.class.isAssignableFrom(type)) {
                        Object attr = req.getAttribute("fileMap");
                        if (attr != null && !((Map<?,?>)attr).isEmpty()) {
                            value = attr;
                        } else if (p.getName().toLowerCase().contains("file") || p.getName().toLowerCase().contains("fichier")) {
                            value = fileMap;
                        } else {
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
                    }
                    else if (p.isAnnotationPresent(VariableChemin.class)) {
                        VariableChemin ann = p.getAnnotation(VariableChemin.class);
                        String key = ann.value().isEmpty() ? p.getName() : ann.value();
                        String s = pathParams.get(key);
                        if (s != null) value = convertString(s, type);
                        if (value == null && ann.required()) {
                            throw new IllegalArgumentException("Paramètre de chemin manquant: " + key + " (type " + type.getSimpleName() + ")");
                        }
                    }
                    else if (p.isAnnotationPresent(ParametreRequete.class)) {
                        ParametreRequete ann = p.getAnnotation(ParametreRequete.class);
                        String key = ann.value().isEmpty() ? p.getName() : ann.value();
                        String s = req.getParameter(key);
                        if (s != null) value = convertString(s, type);
                        if (value == null && ann.required()) {
                            throw new IllegalArgumentException("Paramètre de requête manquant: " + key + " (type " + type.getSimpleName() + ")");
                        }
                    }
                    else if (isCustomClass(type)) {
                        value = bindObject(type, req.getParameterMap());
                    }
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
                res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                res.setHeader("Allow", String.join(", ", route.allowedMethods));
                ViewHandler.show405(path, route.allowedMethods, req, res);
                return;
            } else {
                ViewHandler.handleNotFound(path, req, res);
            }

        } catch (Exception e) {
            ViewHandler.handleException(e, path, req, res);
        }
    }

    private boolean isCustomClass(Class<?> type) {
        return !type.isPrimitive()
            && !type.getName().startsWith("java.")
            && !type.isEnum()
            && !Map.class.isAssignableFrom(type)
            && !List.class.isAssignableFrom(type);
    }

    private Object bindObject(Class<?> clazz, Map<String, String[]> paramMap) {
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                String prefix = clazz.getSimpleName() + "." + field.getName();
                Class<?> fieldType = field.getType();

                if (List.class.isAssignableFrom(fieldType)) {
                    String[] vals = paramMap.get(prefix);
                    if (vals != null) {
                        List<String> list = new java.util.ArrayList<>();
                        for (String v : vals) list.add(v);
                        field.set(instance, list);
                    }
                }
                else if (isCustomClass(fieldType)) {
                    Object subObj = bindObject(fieldType, paramMap);
                    field.set(instance, subObj);
                }
                else {
                    String[] vals = paramMap.get(prefix);
                    if (vals != null && vals.length > 0) {
                        Object converted = convertString(vals[0], fieldType);
                        field.set(instance, converted);
                    }
                }
            }
            return instance;
        } catch (Exception e) {
            return null;
        }
    }

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

        res.setContentType("text/plain; charset=UTF-8");
        res.getWriter().println("Résultat (" + result.getClass().getSimpleName() + "): " + result);
    }

    private Object convertString(String s, Class<?> target) {
        if (s == null) return null;
        if (target == String.class) return s;
        if (target == int.class || target == Integer.class) return Integer.parseInt(s);
        if (target == long.class || target == Long.class) return Long.parseLong(s);
        if (target == boolean.class || target == Boolean.class) return Boolean.parseBoolean(s);
        return s;
    }

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
