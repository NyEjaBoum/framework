#!/usr/bin/env bash
set -euo pipefail

# build.sh - compile le framework, crée framework/framework.jar, copie dans Tomcat lib
# et crée le dossier d'uploads utilisé par l'application.

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="${APP_NAME:-test}"
TOMCAT_HOME="${TOMCAT_HOME:-/opt/tomcat10}"
TOMCAT_LIB="${TOMCAT_LIB:-$TOMCAT_HOME/lib}"

FRAMEWORK_SRC_DIR="$ROOT_DIR/framework/src/framework"
BUILD_DIR="$ROOT_DIR/build/classes"
FRAMEWORK_JAR="$ROOT_DIR/framework/framework.jar"
JAKARTA_JAR="$ROOT_DIR/framework/lib/jakarta.servlet-api-5.0.0.jar"

echo "=== BUILD FRAMEWORK ==="
echo "ROOT_DIR=$ROOT_DIR"
echo "APP_NAME=$APP_NAME"
echo "TOMCAT_HOME=$TOMCAT_HOME"
echo "TOMCAT_LIB=$TOMCAT_LIB"

mkdir -p "$BUILD_DIR"
rm -f "$ROOT_DIR/build/framework-sources.txt"

# Lister sources
find "$FRAMEWORK_SRC_DIR" -name '*.java' > "$ROOT_DIR/build/framework-sources.txt"

if [ ! -s "$ROOT_DIR/build/framework-sources.txt" ]; then
  echo "Aucune source framework trouvée dans $FRAMEWORK_SRC_DIR"
  exit 0
fi

echo "[1/3] Compilation des sources framework..."
if [ -f "$JAKARTA_JAR" ]; then
  JAVAC_CP="$JAKARTA_JAR"
else
  JAVAC_CP=""
  echo "Avertissement: $JAKARTA_JAR introuvable, compilation sans dépendance servlet-api (peut échouer)."
fi

javac -d "$BUILD_DIR" ${JAVAC_CP:+-cp "$JAVAC_CP"} @"$ROOT_DIR/build/framework-sources.txt" || {
  echo "ERREUR: compilation du framework a échoué" >&2
  exit 1
}
rm -f "$ROOT_DIR/build/framework-sources.txt"

echo "[2/3] Création du JAR : $FRAMEWORK_JAR"
mkdir -p "$(dirname "$FRAMEWORK_JAR")"
jar cvf "$FRAMEWORK_JAR" -C "$BUILD_DIR" . >/dev/null 2>&1

if [ ! -f "$FRAMEWORK_JAR" ]; then
  echo "ERREUR: Le JAR n'a pas été créé" >&2
  exit 1
fi

# Copier dans Tomcat global lib si possible
if [ -d "$TOMCAT_LIB" ]; then
  echo "Copie du framework.jar vers $TOMCAT_LIB/"
  cp -f "$FRAMEWORK_JAR" "$TOMCAT_LIB/"
  echo "✓ Copié : $TOMCAT_LIB/$(basename "$FRAMEWORK_JAR")"
else
  echo "Avertissement: dossier Tomcat lib introuvable: $TOMCAT_LIB. JAR laissé dans $FRAMEWORK_JAR"
fi

# --- [3/3] créer dossier d'upload avec permissions correctes ---
echo "[3/3] Configuration du dossier d'upload..."

UPLOAD_DIR="${UPLOAD_DIR:-}"
if [ -z "$UPLOAD_DIR" ]; then
  if [ -n "${TOMCAT_HOME:-}" ]; then
    UPLOAD_DIR="${TOMCAT_HOME}/uploads"
  else
    UPLOAD_DIR="$ROOT_DIR/framework/uploads"
  fi
fi

echo "Création du dossier d'upload : $UPLOAD_DIR"

# Créer le dossier (avec sudo si nécessaire)
if [ -d "$UPLOAD_DIR" ]; then
  echo "Dossier existe déjà : $UPLOAD_DIR"
else
  if mkdir -p "$UPLOAD_DIR" 2>/dev/null; then
    echo "Dossier créé : $UPLOAD_DIR"
  else
    echo "Création avec sudo..."
    sudo mkdir -p "$UPLOAD_DIR"
  fi
fi

# Ajuster les permissions pour que Tomcat puisse écrire
# Option 1: Si l'utilisateur tomcat existe
if id tomcat >/dev/null 2>&1; then
  echo "Changement owner vers tomcat:tomcat..."
  sudo chown tomcat:tomcat "$UPLOAD_DIR" 2>/dev/null || true
  sudo chmod 755 "$UPLOAD_DIR" 2>/dev/null || true
# Option 2: Si Tomcat tourne sous l'utilisateur courant
else
  echo "Utilisateur tomcat non trouvé, application des permissions pour l'utilisateur courant..."
  # Rendre le dossier accessible en écriture pour tous (pour dev/test)
  sudo chmod 777 "$UPLOAD_DIR" 2>/dev/null || chmod 777 "$UPLOAD_DIR" 2>/dev/null || true
fi

# Vérifier le résultat
if [ -d "$UPLOAD_DIR" ] && [ -w "$UPLOAD_DIR" ]; then
  echo "✓ Dossier d'upload prêt et accessible en écriture : $UPLOAD_DIR"
  ls -ld "$UPLOAD_DIR"
else
  echo "⚠️ Attention: le dossier existe mais n'est peut-être pas accessible en écriture"
  echo "Exécutez manuellement : sudo chmod 777 $UPLOAD_DIR"
  ls -ld "$UPLOAD_DIR" 2>/dev/null || true
fi

echo ""
echo "=== BUILD FRAMEWORK TERMINÉ ==="
echo ""
echo "Prochaines étapes :"
echo "  1. ./compile.sh    # compile et déploie le projet test"
echo "  2. Redémarrer Tomcat si nécessaire"
echo "  3. Tester l'upload sur http://localhost:8080/$APP_NAME/etudiant/upload-form"