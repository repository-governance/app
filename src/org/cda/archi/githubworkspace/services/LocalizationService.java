package org.cda.archi.githubworkspace.services;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;

/** Per-plugin language selection and English/Spanish UI text. */
public final class LocalizationService {
    public static final String AUTOMATIC = "automatic";
    public static final String ENGLISH = "en";
    public static final String SPANISH = "es";

    private static final Map<String, String> SPANISH_TEXT = Map.ofEntries(
            Map.entry("GitHub Workspace", "GitHub Workspace"),
            Map.entry("Browse Repositories", "Explorar repositorios"),
            Map.entry("Repositories in the organization and topic configured in Preferences.",
                    "Repositorios de la organización y el tema configurados en Preferencias."),
            Map.entry("Repository discovery, local workspace and authentication status.",
                    "Búsqueda de repositorios, espacio de trabajo local y estado de autenticación."),
            Map.entry("Filter repositories...", "Filtrar repositorios..."),
            Map.entry("Refresh", "Actualizar"),
            Map.entry("Preferences", "Preferencias"),
            Map.entry("Retry Connection", "Reintentar conexión"),
            Map.entry("Cancel", "Cancelar"),
            Map.entry("Refresh repositories using the saved organization and topic.",
                    "Actualizar repositorios con la organización y el tema guardados."),
            Map.entry("Edit repository filters and workspace location.",
                    "Editar los filtros de repositorio y la ubicación del espacio de trabajo."),
            Map.entry("Retry authentication with Git Credential Manager.",
                    "Reintentar la autenticación con Git Credential Manager."),
            Map.entry("Cancel the current operation.", "Cancelar la operación actual."),
            Map.entry("Operation cancelled.", "Operación cancelada."),
            Map.entry("Loading authorized repositories...", "Cargando repositorios autorizados..."),
            Map.entry("A legacy Governance workspace exists and will not be changed. Loading configured repositories.",
                    "Existe un espacio de trabajo Governance anterior y no se modificará. Cargando repositorios configurados."),
            Map.entry("Git is available, but no Git credential helper is configured.",
                    "Git está disponible, pero no hay un gestor de credenciales configurado."),
            Map.entry("View Log", "Ver registro"),
            Map.entry("Preferences saved.", "Preferencias guardadas."),
            Map.entry("Could not connect to GitHub. {0}", "No se pudo conectar con GitHub. {0}"),
            Map.entry("Could not open GitHub Workspace Preferences.", "No se pudieron abrir las preferencias de GitHub Workspace."),
            Map.entry("1 repository found", "Se encontró 1 repositorio"),
            Map.entry("{0} repositories found", "Se encontraron {0} repositorios"),
            Map.entry("Preview unavailable", "Vista previa no disponible"),
            Map.entry("No description", "Sin descripción"),
            Map.entry("Visibility and default branch", "Visibilidad y rama predeterminada"),
            Map.entry("Choose a local branch name and clone this repository from its default branch.",
                    "Elija un nombre para la rama local y clone este repositorio desde su rama predeterminada."),
            Map.entry("Local branches ({0}): {1}", "Ramas locales ({0}): {1}"),
            Map.entry("Open {0}", "Abrir {0}"),
            Map.entry("Open the local workspace for branch {0}.", "Abrir el espacio de trabajo local de la rama {0}."),
            Map.entry("Browse...", "Examinar..."),
            Map.entry("Select GitHub Workspace Location", "Seleccionar ubicación de GitHub Workspace"),
            Map.entry("View Schema JSON", "Ver JSON del esquema"),
            Map.entry("Reload tables and selected records", "Recargar tablas y registros seleccionados"),
            Map.entry("Create Table", "Crear tabla"),
            Map.entry("Edit Table Schema", "Editar esquema de tabla"),
            Map.entry("Save Schema", "Guardar esquema"),
            Map.entry("Define a table schema. Exactly one String, Integer, or UUID primary key is required.",
                    "Defina el esquema de la tabla. Se requiere exactamente una clave primaria de tipo String, Integer o UUID."),
            Map.entry("Update the schema. Existing records must remain compatible. Renames migrate their stored values.",
                    "Actualice el esquema. Los registros existentes deben seguir siendo compatibles. Los cambios de nombre migran sus valores."),
            Map.entry("architects", "architects"),
            Map.entry("Use 1–48 letters, digits, underscores, or hyphens; begin with a letter or underscore.",
                    "Use entre 1 y 48 letras, números, guiones bajos o guiones; comience con una letra o guion bajo."),
            Map.entry("A table with this name already exists.", "Ya existe una tabla con ese nombre."),
            Map.entry("Column names must be unique.", "Los nombres de columna deben ser únicos."),
            Map.entry("Table Name is required.", "El nombre de la tabla es obligatorio."),
            Map.entry("Create Repository from Template", "Crear repositorio desde plantilla"),
            Map.entry("Enter the Building Block details and choose a template.",
                    "Ingrese los datos del Building Block y elija una plantilla."),
            Map.entry("Building Block", "Building Block"),
            Map.entry("Código:", "Código:"),
            Map.entry("Tipo:", "Tipo:"),
            Map.entry("Título:", "Título:"),
            Map.entry("Descripción:", "Descripción:"),
            Map.entry("Plantilla:", "Plantilla:"),
            Map.entry("Create Repository", "Crear repositorio"),
            Map.entry("Add Column", "Agregar columna"),
            Map.entry("Edit Column", "Editar columna"),
            Map.entry("Column Name:", "Nombre de columna:"),
            Map.entry("Data Type:", "Tipo de dato:"),
            Map.entry("Primary Key", "Clave primaria"),
            Map.entry("Required", "Obligatorio"),
            Map.entry("Validate with regular expression", "Validar con expresión regular"),
            Map.entry("Regex Pattern:", "Patrón Regex:"),
            Map.entry("Validation Message:", "Mensaje de validación:"),
            Map.entry("Example: ^[A-Z]{3}-[0-9]{4}$", "Ejemplo: ^[A-Z]{3}-[0-9]{4}$"),
            Map.entry("Shown when the value does not match", "Se muestra cuando el valor no coincide"),
            Map.entry("Java regular expression; the complete value must match.",
                    "Expresión regular de Java; el valor completo debe coincidir."),
            Map.entry("This message appears next to the field when the regex does not match.",
                    "Este mensaje aparece junto al campo cuando el valor no coincide con la expresión regular."),
            Map.entry("Enter a regular expression pattern or disable regex validation.",
                    "Ingrese una expresión regular o desactive la validación Regex."),
            Map.entry("Code:", "Código:"),
            Map.entry("Type:", "Tipo:"),
            Map.entry("Title:", "Título:"),
            Map.entry("Template:", "Plantilla:"),
            Map.entry("Repository:", "Repositorio:"),
            Map.entry("Visibility", "Visibilidad"),
            Map.entry("Default branch", "Rama predeterminada"),
            Map.entry("Repository", "Repositorio"),
            Map.entry("Topic", "Tema"),
            Map.entry("Description", "Descripción"),
            Map.entry("Repositories tagged with the default topic \"{0}\".",
                    "Repositorios etiquetados con el tema predeterminado \"{0}\"."),
            Map.entry("{0} repository tagged {1} in {2}", "{0} repositorio etiquetado como {1} en {2}"),
            Map.entry("{0} repositories tagged {1} in {2}", "{0} repositorios etiquetados como {1} en {2}"),
            Map.entry(" repository tagged ", " repositorio etiquetado "),
            Map.entry(" repositories tagged ", " repositorios etiquetados "),
            Map.entry("Connecting to GitHub...", "Conectando con GitHub..."),
            Map.entry("Loading repositories tagged with {0}...", "Cargando repositorios etiquetados con {0}..."),
            Map.entry("Preparing repository from template...", "Preparando el repositorio desde la plantilla..."),
            Map.entry("Choose a template folder and repository name.", "Elija una carpeta de plantilla y un nombre de repositorio."),
            Map.entry("Created {0} from folder {1}.", "Se creó {0} desde la carpeta {1}."),
            Map.entry("Repository management could not complete.",
                    "No se pudo completar la administración de repositorios."),
            Map.entry("Loading template folders...", "Cargando carpetas de plantillas..."),
            Map.entry("Validating branch name...", "Validando el nombre de la rama..."),
            Map.entry("Locating source/design.archimate...", "Localizando source/design.archimate..."),
            Map.entry("Opening source/design.archimate in Archi...", "Abriendo source/design.archimate en Archi..."),
            Map.entry("Preparing {0}...", "Preparando {0}..."),
            Map.entry("Validating local plan {0}...", "Validando el plan local {0}..."),
            Map.entry("Authenticating with the local Git credential helper...",
                    "Autenticando con el gestor de credenciales local de Git..."),
            Map.entry("Refreshing authorized repositories...", "Actualizando los repositorios autorizados..."),
            Map.entry("The repository default branch is unavailable.", "La rama predeterminada del repositorio no está disponible."),
            Map.entry("Branch name must match D-[0-9]+ (for example, D-09999).",
                    "El nombre de rama debe coincidir con D-[0-9]+ (por ejemplo, D-09999)."),
            Map.entry("(invalid branch name)", "(nombre de rama no válido)"),
            Map.entry("This becomes the name of the local branch and workspace folder.",
                    "Este será el nombre de la rama local y de la carpeta del espacio de trabajo."),
            Map.entry("GitHub account authenticated with Git Credential Manager.",
                    "Cuenta de GitHub autenticada con Git Credential Manager."),
            Map.entry("The destination path is invalid.", "La ruta de destino no es válida."),
            Map.entry("Code", "Código"),
            Map.entry("Type", "Tipo"),
            Map.entry("Title", "Título"),
            Map.entry("Template", "Plantilla"),
            Map.entry("Yes", "Sí"),
            Map.entry("No", "No"),
            Map.entry("Whole number", "Número entero"),
            Map.entry("Decimal number", "Número decimal"),
            Map.entry("Enter true or false.", "Ingrese true o false."),
            Map.entry("Enter a valid email address.", "Ingrese una dirección de correo válida."),
            Map.entry("Enter an HTTP(S) URL.", "Ingrese una URL HTTP(S)."),
            Map.entry("Invalid {0} value.", "El valor de {0} no es válido."),
            Map.entry("Primary key: {0}", "Clave primaria: {0}"),
            Map.entry("View Schema — {0}", "Ver esquema — {0}"),
            Map.entry("New Record", "Nuevo registro"),
            Map.entry("Edit Record", "Editar registro"),
            Map.entry("Enter values for {0}.", "Ingrese los valores de {0}."),
            Map.entry("Edit values for {0}.", "Edite los valores de {0}."),
            Map.entry("Save", "Guardar"),
            Map.entry("GitHub Workspace Log", "Registro de GitHub Workspace"),
            Map.entry("Diagnostic trace saved at {0}. Tokens and credentials are not logged.",
                    "Registro de diagnóstico guardado en {0}. No se registran tokens ni credenciales."),
            Map.entry("Could not read the log file: {0}", "No se pudo leer el archivo de registro: {0}"),
            Map.entry("Create Branch Workspace", "Crear espacio de trabajo de rama"),
            Map.entry("Create a local branch from the selected repository's default branch.",
                    "Crear una rama local desde la rama predeterminada del repositorio seleccionado."),
            Map.entry("SOURCE REPOSITORY", "REPOSITORIO DE ORIGEN"),
            Map.entry("Branch name:", "Nombre de la rama:"),
            Map.entry("Solution Architect Code:", "Código del Solution Architect:"),
            Map.entry("Creation mode:", "Modo de creación:"),
            Map.entry("Create a local branch from the repository's default branch",
                    "Crear una rama local desde la rama predeterminada del repositorio"),
            Map.entry("Result", "Resultado"),
            Map.entry("Branch:", "Rama:"),
            Map.entry("(enter branch name)", "(ingrese el nombre de la rama)"),
            Map.entry("Local workspace:", "Espacio de trabajo local:"),
            Map.entry("Destination unavailable", "Destino no disponible"),
            Map.entry("Not available", "No disponible"),
            Map.entry("Enabled", "Habilitado"),
            Map.entry("Set", "Establecer"),
            Map.entry("Generate", "Generar"),
            Map.entry("Management Repositories", "Administrar repositorios"),
            Map.entry("Repositories tagged with the default topic \"{0}\" in the configured organization.",
                    "Repositorios con el tema predeterminado \"{0}\" en la organización configurada."),
            Map.entry("Create Repository...", "Crear repositorio..."),
            Map.entry("Could not complete repository management.", "No se pudo completar la administración de repositorios."),
            Map.entry("The operation failed.", "La operación falló."),
            Map.entry("Close", "Cerrar"),
            Map.entry("repository", "repositorio"),
            Map.entry("repositories", "repositorios"),
            Map.entry(" found", " encontrados"),
            Map.entry("Private", "Privado"),
            Map.entry("Public", "Público"),
            Map.entry("Clone", "Clonar"),
            Map.entry("View on GitHub", "Ver en GitHub"),
            Map.entry("Data Manager", "Administrador de datos"),
            Map.entry("Manage table schemas and JSON records in the configured GitHub repository.",
                    "Administre esquemas de tablas y registros JSON en el repositorio GitHub configurado."),
            Map.entry("Loading tables from the configured data repository...", "Cargando tablas del repositorio de datos configurado..."),
            Map.entry("The data repository is empty. Use New Table to initialize it with your first table.",
                    "El repositorio de datos está vacío. Use Nueva tabla para crear la primera."),
            Map.entry("No tables found. Use New Table to create the first one.", "No se encontraron tablas. Use Nueva tabla para crear la primera."),
            Map.entry("Loaded {0} table(s).", "Se cargaron {0} tabla(s)."),
            Map.entry("Loading Data Repository...", "Cargando el repositorio de datos..."),
            Map.entry("Loading records from {0}...", "Cargando registros de {0}..."),
            Map.entry("Creating table {0}...", "Creando la tabla {0}..."),
            Map.entry("Validating and saving schema {0}...", "Validando y guardando el esquema {0}..."),
            Map.entry("Creating record...", "Creando registro..."),
            Map.entry("Updating record...", "Actualizando registro..."),
            Map.entry("Deleting record...", "Eliminando registro..."),
            Map.entry("Loaded {0} record(s) from {1}.", "Se cargaron {0} registro(s) de {1}."),
            Map.entry("Created record {0}/{1}.", "Se creó el registro {0}/{1}."),
            Map.entry("Updated record {0}/{1}.", "Se actualizó el registro {0}/{1}."),
            Map.entry("Deleted record {0}/{1}.", "Se eliminó el registro {0}/{1}."),
            Map.entry("Created table {0}.", "Se creó la tabla {0}."),
            Map.entry("Updated schema for table {0}.", "Se actualizó el esquema de la tabla {0}."),
            Map.entry("Renamed table {0} to {1}.", "Se cambió el nombre de la tabla {0} a {1}."),
            Map.entry("TABLES", "TABLAS"),
            Map.entry("Search tables", "Buscar tablas"),
            Map.entry("New Table", "Nueva tabla"),
            Map.entry("Select a table to view its records.", "Seleccione una tabla para ver sus registros."),
            Map.entry("Records", "Registros"),
            Map.entry("Schema", "Esquema"),
            Map.entry("Review and edit this table's structure. Renaming moves the schema and records together.",
                    "Revise y edite la estructura de la tabla. Al cambiarle el nombre, se mueven juntos el esquema y los registros."),
            Map.entry("Edit Schema", "Editar esquema"),
            Map.entry("Delete Record", "Eliminar registro"),
            Map.entry("Search records", "Buscar registros"),
            Map.entry("0 records", "0 registros"),
            Map.entry("{0} record", "{0} registro"),
            Map.entry("{0} records", "{0} registros"),
            Map.entry("No records yet. Select New Record to add the first record.",
                    "Aún no hay registros. Seleccione Nuevo registro para agregar el primero."),
            Map.entry("Delete record {0}/{1}? This cannot be undone.", "¿Eliminar el registro {0}/{1}? Esta acción no se puede deshacer."),
            Map.entry("The repository is public; do not store sensitive or personal data.",
                    "El repositorio es público; no almacene datos personales o confidenciales."),
            Map.entry("Table Properties", "Propiedades de la tabla"),
            Map.entry("Table Name:", "Nombre de la tabla:"),
            Map.entry("Description:", "Descripción:"),
            Map.entry("Column Name", "Nombre de columna"),
            Map.entry("Data Type", "Tipo de dato"),
            Map.entry("Remove", "Quitar"),
            Map.entry("Move Up", "Subir"),
            Map.entry("Move Down", "Bajar"),
            Map.entry("No log entries yet.", "Aún no hay entradas en el registro."),
            Map.entry("Language:", "Idioma:"),
            Map.entry("Automatic (system)", "Automático (sistema)"),
            Map.entry("English", "English"),
            Map.entry("Español", "Español"),
            Map.entry("Repository Discovery", "Búsqueda de repositorios"),
            Map.entry("Organization URL:", "URL de la organización:"),
            Map.entry("Topic by default:", "Tema predeterminado:"),
            Map.entry("Repository Templates", "Plantillas de repositorio"),
            Map.entry("Template Repository URL:", "URL del repositorio de plantillas:"),
            Map.entry("Data Management", "Administración de datos"),
            Map.entry("Data Repository URL:", "URL del repositorio de datos:"),
            Map.entry("Language", "Idioma"),
            Map.entry("Warning: Data Repository is public; do not store sensitive or personal data.",
                    "Advertencia: el repositorio de datos es público; no almacene datos personales o confidenciales."),
            Map.entry("Table", "Tabla"),
            Map.entry("Columns", "Columnas"),
            Map.entry("Fixed default topic used for repository discovery and newly created repositories.",
                    "Tema predeterminado fijo para buscar repositorios y crear repositorios nuevos."),
            Map.entry("GitHub repository whose root folders provide repository templates.",
                    "Repositorio de GitHub cuyas carpetas raíz proporcionan plantillas."),
            Map.entry("GitHub repository used to store table schemas and JSON records.",
                    "Repositorio de GitHub donde se guardan los esquemas de tablas y los registros JSON."),
            Map.entry("Could not save GitHub Workspace preferences.", "No se pudieron guardar las preferencias de GitHub Workspace."),
            Map.entry("Enter a valid workspace path.", "Ingrese una ruta válida para el espacio de trabajo."),
            Map.entry("Local Workspace", "Espacio de trabajo local"),
            Map.entry("Use default location", "Usar ubicación predeterminada"),
            Map.entry("Workspace Location:", "Ubicación del espacio de trabajo:"),
            Map.entry("Git Authentication", "Autenticación de Git"),
            Map.entry("Setting", "Configuración"),
            Map.entry("Value", "Valor"),
            Map.entry("Git installation", "Instalación de Git"),
            Map.entry("Available", "Disponible"),
            Map.entry("Git version", "Versión de Git"),
            Map.entry("Unknown", "Desconocida"),
            Map.entry("Git Credential Manager / helper", "Git Credential Manager / asistente"),
            Map.entry("Configured", "Configurado"),
            Map.entry("Not configured", "No configurado"),
            Map.entry("Authenticated GitHub account", "Cuenta autenticada de GitHub"),
            Map.entry("Not connected", "Sin conexión"),
            Map.entry("Checking...", "Comprobando..."),
            Map.entry("An existing Governance workspace was found. It will not be moved, modified, or deleted.",
                    "Se encontró un espacio de trabajo Governance existente. No se moverá, modificará ni eliminará.")
    );

    private LocalizationService() { }

    public static boolean isSupportedChoice(String language) {
        return AUTOMATIC.equals(language) || ENGLISH.equals(language) || SPANISH.equals(language);
    }

    public static String effectiveLanguage(String choice, Locale systemLocale) {
        if (SPANISH.equals(choice) || ENGLISH.equals(choice)) return choice;
        return systemLocale != null && SPANISH.equalsIgnoreCase(systemLocale.getLanguage()) ? SPANISH : ENGLISH;
    }

    public static String text(String english) {
        return translate(english, effectiveLanguage(languageChoice(), Locale.getDefault()));
    }

    public static String translate(String english, String language) {
        if (english == null || !SPANISH.equals(language)) return english;
        String translated = SPANISH_TEXT.get(english);
        if (translated != null) return translated;
        Object[] values;
        if ((values = capture(english, "Loading records from (.+)\\.\\.\\.")) != null) {
            return formatSpanish("Loading records from {0}...", values);
        }
        if ((values = capture(english, "Creating table (.+)\\.\\.\\.")) != null) {
            return formatSpanish("Creating table {0}...", values);
        }
        if ((values = capture(english, "Validating and saving schema (.+)\\.\\.\\.")) != null) {
            return formatSpanish("Validating and saving schema {0}...", values);
        }
        if ((values = capture(english, "Created table (.+)\\.")) != null) {
            return formatSpanish("Created table {0}.", values);
        }
        if ((values = capture(english, "Updated schema for table (.+)\\.")) != null) {
            return formatSpanish("Updated schema for table {0}.", values);
        }
        if ((values = capture(english, "Renamed table (.+) to (.+)\\.")) != null) {
            return formatSpanish("Renamed table {0} to {1}.", values);
        }
        if ((values = capture(english, "Loaded (\\d+) record\\(s\\) from (.+)\\.")) != null) {
            values[0] = Integer.valueOf(String.valueOf(values[0]));
            return formatSpanish("Loaded {0} record(s) from {1}.", values);
        }
        if ((values = capture(english, "(Created|Updated|Deleted) record (.+)/(.+)\\.")) != null) {
            String template = switch (String.valueOf(values[0])) {
                case "Created" -> "Created record {0}/{1}.";
                case "Updated" -> "Updated record {0}/{1}.";
                default -> "Deleted record {0}/{1}.";
            };
            return formatSpanish(template, values[1], values[2]);
        }
        if ((values = capture(english, "Loading repositories tagged with (.+)\\.\\.\\.")) != null) {
            return formatSpanish("Loading repositories tagged with {0}...", values);
        }
        return english;
    }

    private static Object[] capture(String value, String expression) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(expression).matcher(value);
        if (!matcher.matches()) return null;
        Object[] values = new Object[matcher.groupCount()];
        for (int index = 0; index < values.length; index++) values[index] = matcher.group(index + 1);
        return values;
    }

    private static String formatSpanish(String pattern, Object... arguments) {
        return new MessageFormat(SPANISH_TEXT.getOrDefault(pattern, pattern), Locale.forLanguageTag("es"))
                .format(arguments);
    }

    public static String format(String englishPattern, Object... arguments) {
        String pattern = text(englishPattern);
        return new MessageFormat(pattern, locale()).format(arguments);
    }

    public static Locale locale() {
        return SPANISH.equals(effectiveLanguage(languageChoice(), Locale.getDefault()))
                ? Locale.forLanguageTag("es") : Locale.ENGLISH;
    }

    private static String languageChoice() {
        try {
            return new PreferencesService().getLanguage();
        } catch (RuntimeException | LinkageError ex) {
            return AUTOMATIC;
        }
    }
}
