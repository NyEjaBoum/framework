# Sprints — SP1 → SP8 (résumé)

## SP1 — Initialisation et FrontServlet
- But : intercepter toutes les requêtes via le FrontServlet.
- Implémentation : mapping du servlet dans [test/WEB-INF/web.xml](test/WEB-INF/web.xml) (doit être `/*` pour tout capter), initialisation du scanner dans [`framework.servlet.FrontServlet`](framework/src/framework/servlet/FrontServlet.java).
- Fichiers :
  - [`framework.servlet.FrontServlet`](framework/src/framework/servlet/FrontServlet.java)
  - [test/WEB-INF/web.xml](test/WEB-INF/web.xml)

## SP2 — Scanning des annotations (@Controller, @Url)
- But : au démarrage, construire la table URL → méthode.
- Implémentation : [`framework.servlet.Scanner`](framework/src/framework/servlet/Scanner.java) parcourt `WEB-INF/classes` et remplit :
  - `urlToMethod` (URL exactes)
  - `urlPatternToMethod` (patterns avec `{...}`)
- Fichiers :
  - [`framework.servlet.Scanner`](framework/src/framework/servlet/Scanner.java)

## SP3 — Variables d'URL (chemins dynamiques) with 3-ter
- But : supporter `/ressource/{id}` et extraire la valeur.
- Règles :
  - Priorité aux URL exactes (vérifier `urlToMethod` avant `urlPatternToMethod`).
  - Pour les patterns, construire un regex et extraire les placeholders.
- Implémentation : [`framework.servlet.Router`](framework/src/framework/servlet/Router.java) (matching) et extraction (FrontServlet si besoin).
- Exemple :
  - `/etudiant/{id}` → appel de `get(int id)` dans [test/etudiant/EtudiantController.java](test/etudiant/EtudiantController.java)

## SP4 — Exécution des méthodes & retours
- But : invoquer la méthode du controller et gérer le résultat.
- Comportement :
  - `String` → affichage texte (PrintWriter).
  - `ModelView` → forward JSP via [`framework.servlet.ViewHandler`](framework/src/framework/servlet/ViewHandler.java).
- Fichiers :
  - [`framework.view.ModelView`](framework/src/framework/view/ModelView.java)
  - [`framework.servlet.ViewHandler`](framework/src/framework/servlet/ViewHandler.java)

## SP5 — Passage de données vers les vues
- But : permettre aux controllers d'envoyer des attributs à la vue.
- Implémentation : `ModelView.addAttribute(...)` → `ViewHandler` copie les paires dans `request.setAttribute(...)` avant forward.
- Exemple :
  - [test/etudiant/EtudiantController.list()](test/etudiant/EtudiantController.java) et JSP [test/WEB-INF/etudiant/list.jsp](test/WEB-INF/etudiant/list.jsp)

## SP6 — Paramètres de requête (formulaires / query) et priorité
- But : injecter automatiquement les paramètres dans les arguments des méthodes.
- Règles d'injection (ordre) :
  1. `@ParametreRequete("name")` sur le paramètre → `req.getParameter("name")` (`framework.annotation.ParametreRequete`)
  2. Valeurs extraites depuis l'URL (pattern `{...}`)
  3. `req.getParameter(paramName)` (nom Java du paramètre ; nécessite compilation avec `-parameters` ou utiliser `@ParametreRequete`)
- Conversion automatique : gérée par `FrontServlet.convertString`.
- Exemple & test :
  - Controller : [test/etudiant/EtudiantController](test/etudiant/EtudiantController.java) (`/add` attend `nom` et `age`)
  - Formulaire JSP : [test/WEB-INF/etudiant/add.jsp](test/WEB-INF/etudiant/add.jsp)
- Problème courant :
  - Si `/etudiant/add` n’est pas enregistré, le pattern `/etudiant/{id}` peut capturer `"add"` → `NumberFormatException`.
  - Vérifier scanner (`framework.servlet.Scanner`) et prioriser URL exactes (déjà implémenté) ou rendre le matching tolerant (`framework.servlet.Router`).
- Priorité confirmée :
  1. Variables d'URL `{...}`
  2. Query-string / formulaire
  3. Null ou exception si aucune valeur

## SP6-bis — Priorité et résolution des paramètres (variables d'URL vs paramètres de requête)

Dans le framework, la résolution des arguments des méthodes de contrôleur respecte une **priorité stricte** pour l'injection des valeurs :

### 1. Priorité d'injection

- **Variables d'URL (`{...}`)**
  - Si le paramètre Java est annoté avec `@VariableChemin`, la valeur est extraite exclusivement du chemin de l'URL (ex : `/etudiant/25` → `id=25`).
  - Si la variable est absente et `required=true`, une exception explicite est levée.
  - Si `required=false`, la valeur injectée est `null`.

- **Paramètres de requête (query string ou formulaire)**
  - Si le paramètre Java est annoté avec `@ParametreRequete`, la valeur est extraite exclusivement de la query string ou du formulaire (ex : `/etudiant/add?nom=Jean&age=22`).
  - Même logique pour `required`.

- **Aucune annotation**
  - Le framework applique : priorité variable d'URL (`{...}`) > query string/formulaire > null/exception si type primitif.

### 2. Rétrocompatibilité

- Les contrôleurs existants sans annotation continuent de fonctionner : le framework tente d'abord d'injecter depuis le chemin, puis depuis la requête.

### 3. Gestion des paramètres optionnels

- Les annotations acceptent `required = false` pour rendre un paramètre optionnel.
- Si la valeur est absente et le paramètre optionnel, la méthode reçoit `null`.


### 4. Exemple d'utilisation

```java
@Url(value = "/{id}/note/{noteId}")
public String detail(
    @VariableChemin("id") Integer idEtudiant,
    @VariableChemin(value = "noteId", required = false) Integer idNote
) {
    return "idEtudiant=" + idEtudiant + ", idNote=" + idNote;
}

@Url(value = "/recherche")
public String rechercher(
    @ParametreRequete("q") String recherche,
    @ParametreRequete(value = "page", required = false) Integer page
) {
    // ...
}



## SP7 — Méthodes HTTP (plan)
- But : supporter GET/POST/PUT/DELETE.
- Approche : étendre le mapping (ActionMapping) ou ajouter annotations spécifiques (`@GetMapping`, `@PostMapping`) et stocker la méthode HTTP dans le mapping.

## SP8 — Map → attributs de requête
- But : pouvoir retourner une `Map<String,Object>` via `ModelView` et copier toutes les paires dans `request.setAttribute(...)` (implémentation dans `ViewHandler`).

---

## Commandes / déploiement
- Build & deploy : exécuter [deploy.bat](deploy.bat) (compile le framework, crée `framework.jar`, compile les tests et copie vers `test/WEB-INF/classes` puis Tomcat webapps).
- Vérifications après déploiement :
  - Les `.class` sont présentes sous `test/WEB-INF/classes/...`
  - Logs Tomcat / console affichent les URLs scannées depuis [`framework.servlet.FrontServlet.init()`](framework/src/framework/servlet/FrontServlet.java)

---

## Fichiers importants (références)
- [`framework.servlet.FrontServlet`](framework/src/framework/servlet/FrontServlet.java)  
- [`framework.servlet.Scanner`](framework/src/framework/servlet/Scanner.java)  
- [`framework.servlet.Router`](framework/src/framework/servlet/Router.java)  
- [`framework.servlet.ViewHandler`](framework/src/framework/servlet/ViewHandler.java)  
- [`framework.view.ModelView`](framework/src/framework/view/ModelView.java)  
- [`framework.annotation.ParametreRequete`](framework/src/framework/annotation/ParametreRequete.java)  
- [test/etudiant/EtudiantController.java](test/etudiant/EtudiantController.java)  
- [test/WEB-INF/etudiant/add.jsp](test/WEB-INF/etudiant/add.jsp)  
- [test/WEB-INF/etudiant/list.jsp](test/WEB-INF/etudiant/list.jsp)  
- [test/WEB-INF/web.xml](test/WEB-INF/web.xml)  
- [deploy.bat](deploy.bat)
