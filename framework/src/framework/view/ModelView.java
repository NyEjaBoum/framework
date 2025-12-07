package framework.view;

import java.util.HashMap;
import java.util.Map;

public class ModelView {
    private String view;
    private Map<String, Object> data = new HashMap<>();
    private String message; // Ajouté

    public ModelView() {}
    public ModelView(String view) { this.view = view; }

    public String getView() { return view; }
    public void setView(String view) { this.view = view; }

    public void addAttribute(String key, Object value) { data.put(key, value); }
    public Map<String, Object> getData() { return data; }

    // Ajouté : gestion du message
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}