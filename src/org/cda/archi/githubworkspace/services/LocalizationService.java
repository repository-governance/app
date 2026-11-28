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
            Map.entry("Restart required", "Se requiere reiniciar"),
            Map.entry("The language change will take effect after restarting Archi. Restart now?",
                    "El cambio de idioma se aplicará al reiniciar Archi. ¿Desea reiniciarlo ahora?"),
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
                    "Se encontró un espacio de trabajo Governance existente. No se moverá, modificará ni eliminará."),
            Map.entry("Choose a data type.", "Seleccione un tipo de dato."),
            Map.entry("Choose a template.", "Seleccione una plantilla."),
            Map.entry("Choose a workspace location.", "Seleccione una ubicación para el espacio de trabajo."),
            Map.entry("Configure a Data Repository URL in Preferences.",
                    "Configure la URL del repositorio de datos en Preferencias."),
            Map.entry("Data Repository URL must be an HTTPS GitHub repository URL.",
                    "La URL del repositorio de datos debe ser una URL HTTPS de un repositorio de GitHub."),
            Map.entry("Enter a valid HTTPS GitHub repository URL.",
                    "Ingrese una URL HTTPS válida de un repositorio de GitHub."),
            Map.entry("Symbolic links and redirected workspace paths are not allowed.",
                    "No se permiten enlaces simbólicos ni rutas de espacio de trabajo redirigidas."),
            Map.entry("A workspace path component is not a directory.",
                    "Un componente de la ruta del espacio de trabajo no es un directorio."),
            Map.entry("The workspace location is not a writable directory.",
                    "La ubicación del espacio de trabajo no es un directorio con permisos de escritura."),
            Map.entry("The workspace location is not a valid path.",
                    "La ubicación del espacio de trabajo no es una ruta válida."),
            Map.entry("The workspace location cannot be accessed.",
                    "No se puede acceder a la ubicación del espacio de trabajo."),
            Map.entry("Choose Automatic, English, or Español.", "Seleccione Automático, English o Español."),
            Map.entry("Code must use the format SBB-0099999.", "El código debe tener el formato SBB-0099999."),
            Map.entry("Choose AM - Application Module or SD - Service Domain.",
                    "Seleccione AM - Application Module o SD - Service Domain."),
            Map.entry("Title can contain letters and spaces only.",
                    "El título solo puede contener letras y espacios."),
            Map.entry("Plan ID must match D-[0-9]+ (for example, D-09999).",
                    "El ID del plan debe coincidir con D-[0-9]+ (por ejemplo, D-09999)."),
            Map.entry("Enter a valid GitHub organization name.", "Ingrese un nombre válido de organización de GitHub."),
            Map.entry("The selected repository has an invalid browser URL.",
                    "El repositorio seleccionado tiene una URL de navegador no válida."),
            Map.entry("Only GitHub HTTPS repository pages can be opened.",
                    "Solo se pueden abrir páginas HTTPS de repositorios de GitHub."),
            Map.entry("The operating system does not provide a default browser action.",
                    "El sistema operativo no proporciona una acción de navegador predeterminada."),
            Map.entry("The primary key name or type cannot change while records exist.",
                    "El nombre o tipo de la clave primaria no puede cambiar mientras existan registros."),
            Map.entry("The column rename mapping is invalid. Refresh the schema editor and retry.",
                    "El mapeo de nombres de columna no es válido. Actualice el editor de esquemas e inténtelo de nuevo."),
            Map.entry("Primary keys cannot be changed.", "No se pueden cambiar las claves primarias."),
            Map.entry("Record exceeds 256 KiB.", "El registro supera los 256 KiB."),
            Map.entry("Stored JSON type does not match", "El tipo JSON almacenado no coincide con"),
            Map.entry("Record contains fields that are not defined in the schema.",
                    "El registro contiene campos que no están definidos en el esquema."),
            Map.entry("enter true or false", "ingrese true o false"),
            Map.entry("enter a valid email", "ingrese un correo electrónico válido"),
            Map.entry("enter an HTTP(S) URL", "ingrese una URL HTTP(S)"),
            Map.entry("A local Plan ID path is not a directory.",
                    "La ruta local del ID del plan no es un directorio."),
            Map.entry("The repository workspace is not a directory.",
                    "El espacio de trabajo del repositorio no es un directorio."),
            Map.entry("The repository does not contain source/design.archimate.",
                    "El repositorio no contiene source/design.archimate."),
            Map.entry("The plan path is outside the configured workspace.",
                    "La ruta del plan está fuera del espacio de trabajo configurado."),
            Map.entry("The plan destination has no existing parent.",
                    "El destino del plan no tiene un directorio principal existente."),
            Map.entry("Select a GitHub repository.", "Seleccione un repositorio de GitHub."),
            Map.entry("Install Git and make sure it is available in PATH.",
                    "Instale Git y asegúrese de que esté disponible en PATH."),
            Map.entry("No Git credential helper is configured. Configure Git Credential Manager locally.",
                    "No hay un asistente de credenciales de Git configurado. Configure Git Credential Manager localmente."),
            Map.entry("The repository default branch is invalid.",
                    "La rama predeterminada del repositorio no es válida."),
            Map.entry("The selected template folder is empty.", "La carpeta de plantilla seleccionada está vacía."),
            Map.entry("Choose a template folder.", "Seleccione una carpeta de plantilla."),
            Map.entry("The template repository returned invalid folder data.",
                    "El repositorio de plantillas devolvió datos de carpeta no válidos."),
            Map.entry("The selected folder is no longer present in the template repository.",
                    "La carpeta seleccionada ya no está en el repositorio de plantillas."),
            Map.entry("The selected template folder is too large to copy safely.",
                    "La carpeta de plantilla seleccionada es demasiado grande para copiarla de forma segura."),
            Map.entry("Template folders cannot contain nested repositories or special Git links.",
                    "Las carpetas de plantilla no pueden contener repositorios anidados ni enlaces especiales de Git."),
            Map.entry("Template folders cannot contain symbolic links or special file types.",
                    "Las carpetas de plantilla no pueden contener enlaces simbólicos ni tipos de archivo especiales."),
            Map.entry("The selected template exceeds the 25 MB copy limit.",
                    "La plantilla seleccionada supera el límite de copia de 25 MB."),
            Map.entry("The template repository returned invalid file data.",
                    "El repositorio de plantillas devolvió datos de archivo no válidos."),
            Map.entry("The selected template has more than {0} files.",
                    "La plantilla seleccionada tiene más de {0} archivos."),
            Map.entry("GitHub returned invalid template file contents.",
                    "GitHub devolvió contenido de archivo de plantilla no válido."),
            Map.entry("A template file has an unsafe path.", "Un archivo de plantilla tiene una ruta no segura."),
            Map.entry("Configure the template repository in Preferences.",
                    "Configure el repositorio de plantillas en Preferencias."),
            Map.entry("Template repository must be a GitHub URL or owner/repository.",
                    "El repositorio de plantillas debe ser una URL de GitHub o tener el formato propietario/repositorio."),
            Map.entry("Schema must be a JSON object.", "El esquema debe ser un objeto JSON."),
            Map.entry("Schema is missing required metadata.", "Faltan metadatos obligatorios en el esquema."),
            Map.entry("Schema columns are missing or exceed the limit.",
                    "Faltan columnas en el esquema o se supera el límite permitido."),
            Map.entry("Invalid column definition.", "La definición de columna no es válida."),
            Map.entry("Invalid schema string value.", "El valor de texto del esquema no es válido."),
            Map.entry("Invalid schema boolean value.", "El valor booleano del esquema no es válido."),
            Map.entry("GitHub returned an invalid user response.", "GitHub devolvió una respuesta de usuario no válida."),
            Map.entry("GitHub returned invalid repository search JSON.",
                    "GitHub devolvió JSON de búsqueda de repositorios no válido."),
            Map.entry("The repository thumbnail is too large.", "La miniatura del repositorio es demasiado grande."),
            Map.entry("Topic must be 1–50 lowercase letters, numbers, or hyphens.",
                    "El tema debe tener entre 1 y 50 letras minúsculas, números o guiones."),
            Map.entry("Invalid repository URL.", "La URL del repositorio no es válida."),
            Map.entry("The template repository is empty.", "El repositorio de plantillas está vacío."),
            Map.entry("GitHub returned invalid organization repository data.",
                    "GitHub devolvió datos de repositorios de organización no válidos."),
            Map.entry("Expected list.", "Se esperaba una lista."),
            Map.entry("Expected root listing.", "Se esperaba un listado del directorio raíz."),
            Map.entry("Unsafe template folder name.", "El nombre de la carpeta de plantilla no es seguro."),
            Map.entry("GitHub initialized the repository without reporting its default branch.",
                    "GitHub inicializó el repositorio, pero no informó su rama predeterminada."),
            Map.entry("The default branch does not point to a commit.",
                    "La rama predeterminada no apunta a un commit."),
            Map.entry("GitHub returned invalid initial branch data.",
                    "GitHub devolvió datos iniciales de rama no válidos."),
            Map.entry("GitHub returned invalid initial commit data.",
                    "GitHub devolvió datos iniciales de commit no válidos."),
            Map.entry("GitHub created the repository but returned invalid repository metadata.",
                    "GitHub creó el repositorio, pero devolvió metadatos no válidos."),
            Map.entry("GitHub could not complete the repository clone.",
                    "GitHub no pudo completar la clonación del repositorio."),
            Map.entry("Git could not complete the repository clone.",
                    "Git no pudo completar la clonación del repositorio."),
            Map.entry("The local repository path does not match the selected repository.",
                    "La ruta del repositorio local no coincide con el repositorio seleccionado."),
            Map.entry("An operation for this local repository is already in progress.",
                    "Ya hay una operación en curso para este repositorio local."),
            Map.entry("The plan destination does not match the selected repository and Plan ID.",
                    "El destino del plan no coincide con el repositorio y el ID de plan seleccionados."),
            Map.entry("An operation for this plan is already in progress.",
                    "Ya hay una operación en curso para este plan."),
            Map.entry("GitHub did not provide a default branch for this repository.",
                    "GitHub no proporcionó una rama predeterminada para este repositorio."),
            Map.entry("This local checkout is on branch", "Esta copia local está en la rama"),
            Map.entry("The local repository path exists but is not a safe directory.",
                    "La ruta del repositorio local existe, pero no es un directorio seguro."),
            Map.entry("The existing directory is not a valid Git working copy.",
                    "El directorio existente no es una copia de trabajo válida de Git."),
            Map.entry("The existing Git working copy has a different root directory.",
                    "La copia de trabajo de Git existente tiene un directorio raíz diferente."),
            Map.entry("The existing working copy's origin does not match the selected GitHub repository.",
                    "El origen de la copia de trabajo existente no coincide con el repositorio de GitHub seleccionado."),
            Map.entry("Git could not validate the local working copy.",
                    "Git no pudo validar la copia de trabajo local."),
            Map.entry("Archi could not open source/design.archimate as a native model.",
                    "Archi no pudo abrir source/design.archimate como modelo nativo."),
            Map.entry("source/design.archimate is missing or is not a regular file.",
                    "Falta source/design.archimate o no es un archivo normal."),
            Map.entry("The Archi workbench is not available on the SWT UI thread.",
                    "El entorno de trabajo de Archi no está disponible en el hilo de interfaz de SWT."),
            Map.entry("The already-open Archi model is no longer available.",
                    "El modelo de Archi que estaba abierto ya no está disponible."),
            Map.entry("Archi could not activate the Models tree.",
                    "Archi no pudo activar el árbol de modelos."),
            Map.entry("GitHub session has been closed.", "La sesión de GitHub se ha cerrado."),
            Map.entry("Unsupported schema version.", "La versión del esquema no es compatible."),
            Map.entry("A table must have between 1 and 64 columns.",
                    "La tabla debe tener entre 1 y 64 columnas.")
            ,Map.entry("Define exactly one primary key.", "Defina exactamente una clave primaria."),
            Map.entry("Primary key metadata does not match the columns.",
                    "Los metadatos de la clave primaria no coinciden con las columnas.")
            ,Map.entry("Column names must be safe identifiers of at most 64 characters.",
                    "Los nombres de columna deben ser identificadores seguros de hasta 64 caracteres.")
            ,Map.entry("Choose a column data type.", "Seleccione un tipo de dato para la columna."),
            Map.entry("Primary keys must use String, Integer, or UUID.",
                    "Las claves primarias deben usar String, Integer o UUID.")
            ,Map.entry("Regex validation is only available for String columns.",
                    "La validación Regex solo está disponible para columnas String.")
            ,Map.entry("Regex pattern is limited to 256 characters.",
                    "La expresión Regex puede tener como máximo 256 caracteres.")
            ,Map.entry("Validation message is limited to 300 characters.",
                    "El mensaje de validación puede tener como máximo 300 caracteres.")
            ,Map.entry("Enter a GitHub organization URL or name.", "Ingrese una URL o nombre de organización de GitHub."),
            Map.entry("A template folder needs a name and Git tree.",
                    "La carpeta de plantilla necesita un nombre y un árbol Git.")
            ,Map.entry("GitHub returned invalid thumbnail image data.",
                    "GitHub devolvió datos de imagen de miniatura no válidos.")
            ,Map.entry("GitHub returned invalid thumbnail directory metadata.",
                    "GitHub devolvió metadatos de directorio de miniaturas no válidos.")
            ,Map.entry("GitHub returned an unsupported template file encoding.",
                    "GitHub devolvió una codificación de archivo de plantilla no compatible.")
            ,Map.entry("GitHub returned invalid file data.", "GitHub devolvió datos de archivo no válidos.")
            ,Map.entry("GitHub's 1,000-result search boundary was reached. "
                    + "Narrow the topic or organization search.", "Se alcanzó el límite de búsqueda de 1.000 resultados de GitHub. "
                    + "Restrinja la búsqueda por tema u organización.")
            ,Map.entry("GitHub Search can return at most 1,000 results. "
                    + "Narrow the topic or organization search.", "GitHub Search puede devolver como máximo 1.000 resultados. "
                    + "Restrinja la búsqueda por tema u organización.")
            ,Map.entry("The search result changed during pagination. Retry the search.",
                    "Los resultados cambiaron durante la paginación. Repita la búsqueda.")
            ,Map.entry("GitHub returned duplicate search results. Retry the search.",
                    "GitHub devolvió resultados de búsqueda duplicados. Repita la búsqueda.")
            ,Map.entry("GitHub returned an incomplete result set. Retry the search.",
                    "GitHub devolvió un conjunto de resultados incompleto. Repita la búsqueda.")
            ,Map.entry("GitHub returned a short page before the search was complete. Retry the search.",
                    "GitHub devolvió una página incompleta antes de finalizar la búsqueda. Repita la búsqueda.")
            ,Map.entry("The organization repository list exceeded 10,000 entries.",
                    "La lista de repositorios de la organización supera las 10.000 entradas.")
            ,Map.entry("The selected template has more than 500 files.",
                    "La plantilla seleccionada tiene más de 500 archivos."),
            Map.entry("Table names must be safe identifiers of 1–48 letters, digits, underscores, or hyphens.",
                    "Los nombres de tabla deben ser identificadores seguros de 1 a 48 letras, números, guiones bajos o guiones."),
            Map.entry("Invalid regular expression: {0}", "Expresión regular no válida: {0}"),
            Map.entry("Unclosed character class", "clase de caracteres sin cerrar"),
            Map.entry("Choose a GitHub repository.", "Seleccione un repositorio de GitHub."),
            Map.entry("The plan path escapes its repository workspace.",
                    "La ruta del plan sale del espacio de trabajo de su repositorio."),
            Map.entry("The ArchiMate model path escapes the plan workspace.",
                    "La ruta del modelo ArchiMate sale del espacio de trabajo del plan."),
            Map.entry("The ArchiMate model resolves outside the plan workspace.",
                    "La ruta resuelta del modelo ArchiMate queda fuera del espacio de trabajo del plan."),
            Map.entry("The selected repository has an invalid GitHub identity.",
                    "La identidad de GitHub del repositorio seleccionado no es válida."),
            Map.entry("GitHub returned an invalid HTTPS clone URL.",
                    "GitHub devolvió una URL HTTPS de clonación no válida."),
            Map.entry("The selected repository clone URL does not match its GitHub identity.",
                    "La URL de clonación no coincide con la identidad de GitHub del repositorio seleccionado."),
            Map.entry("A workspace path resolves outside its root.",
                    "La ruta del espacio de trabajo queda fuera de su directorio raíz."),
            Map.entry("The configured template repository has no folders at its root.",
                    "El repositorio de plantillas configurado no tiene carpetas en el directorio raíz."),
            Map.entry("GitHub could not create the repository. Check that its name is available "
                    + "and that you have permission to create repositories in the organization.",
                    "GitHub no pudo crear el repositorio. Compruebe que el nombre esté disponible "
                    + "y que tenga permiso para crear repositorios en la organización."),
            Map.entry("GitHub returned invalid repository metadata.",
                    "GitHub devolvió metadatos de repositorio no válidos."),
            Map.entry("The template repository is not configured.", "El repositorio de plantillas no está configurado."),
            Map.entry("Invalid updated_at.", "El campo updated_at no es válido."),
            Map.entry("Missing incomplete_results.", "Falta el campo incomplete_results."),
            Map.entry("Missing total_count.", "Falta el campo total_count."),
            Map.entry("Missing items.", "Falta el campo items."),
            Map.entry("Missing tree entries.", "Faltan las entradas del árbol."),
            Map.entry("GitHub returned invalid " + "{0} metadata.", "GitHub devolvió metadatos no válidos de {0}."),
            Map.entry("The schema change is incompatible with record {0} in column {1}.",
                    "El cambio de esquema es incompatible con el registro {0} en la columna {1}."),
            Map.entry("A value already exists for {0}.", "Ya existe un valor para {0}."),
            Map.entry("The configured data repository is not a valid GitHub repository.",
                    "El repositorio de datos configurado no es un repositorio de GitHub válido."),
            Map.entry("Could not save the authenticated GitHub account.",
                    "No se pudo guardar la cuenta de GitHub autenticada."),
            Map.entry("The repository catalog is not connected to its controller.",
                    "El catálogo de repositorios no está conectado a su controlador."),
            Map.entry("Could not open the branch workspace dialog: {0}",
                    "No se pudo abrir el diálogo del espacio de trabajo de rama: {0}"),
            Map.entry("Could not open the branch workspace dialog: unexpected SWT/JFace error.",
                    "No se pudo abrir el diálogo del espacio de trabajo de rama: error inesperado de SWT/JFace."),
            Map.entry("Data Manager operation failed.", "La operación del administrador de datos falló."),
            Map.entry("GitHub Workspace could not complete the requested operation.",
                    "GitHub Workspace no pudo completar la operación solicitada."),
            Map.entry("GitHub Workspace could not complete repository management.",
                    "GitHub Workspace no pudo completar la administración de repositorios."),
            Map.entry("Publishing the initial branch...", "Publicando la rama inicial..."),
            Map.entry("Assigning the default topic...", "Asignando el tema predeterminado..."),
            Map.entry("Waiting for Git Credential Manager...", "Esperando a Git Credential Manager..."),
            Map.entry("Authenticated with GitHub.", "Autenticado con GitHub."),
            Map.entry("The default branch cannot be determined.",
                    "No se puede determinar la rama predeterminada."),
            Map.entry("The local branch already exists.", "La rama local ya existe."),
            Map.entry("The plan workspace already exists.", "El espacio de trabajo del plan ya existe."),
            Map.entry("The source repository is not available.", "El repositorio de origen no está disponible."),
            Map.entry("The selected template folder is not available.",
                    "La carpeta de plantilla seleccionada no está disponible.")
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
        if ((values = capture(english, "(.+) is required\\.")) != null) {
            return formatSpanish("{0} es obligatorio.", values);
        }
        if ((values = capture(english, "A table must have between 1 and (\\d+) columns\\.")) != null) {
            return formatSpanish("A table must have between 1 and {0} columns.", values);
        }
        if ((values = capture(english, "Invalid value for (.+): (.+)")) != null) {
            values[1] = translate(String.valueOf(values[1]), SPANISH);
            return formatSpanish("Invalid value for {0}: {1}", values);
        }
        if ((values = capture(english, "Invalid stored value for (.+): (.+)")) != null) {
            values[1] = translate(String.valueOf(values[1]), SPANISH);
            return formatSpanish("Invalid stored value for {0}: {1}", values);
        }
        if ((values = capture(english, "A table named (.+) already exists\\.")) != null) {
            return formatSpanish("A table named {0} already exists.", values);
        }
        if ((values = capture(english, "A record with primary key (.+) already exists\\.")) != null) {
            return formatSpanish("A record with primary key {0} already exists.", values);
        }
        if ((values = capture(english, "A value already exists for (.+)\\.")) != null) {
            return formatSpanish("A value already exists for {0}.", values);
        }
        if ((values = capture(english, "Invalid regular expression: (.+)")) != null) {
            values[0] = translate(String.valueOf(values[0]), SPANISH);
            return formatSpanish("Invalid regular expression: {0}", values);
        }
        if ((values = capture(english, "Unsupported data type: (.+)")) != null) {
            return "Tipo de dato no compatible: " + values[0];
        }
        if ((values = capture(english, "Missing GitHub (.+)\\.")) != null) {
            return "Falta el campo " + values[0] + " de GitHub.";
        }
        if ((values = capture(english, "GitHub returned invalid (.+) metadata\\.")) != null) {
            return "GitHub devolvió metadatos no válidos de " + values[0] + ".";
        }
        if ((values = capture(english, "Could not open the branch workspace dialog: (.+)")) != null) {
            values[0] = translate(String.valueOf(values[0]), SPANISH);
            return formatSpanish("Could not open the branch workspace dialog: {0}", values);
        }
        if ((values = capture(english, "The schema change is incompatible with record (.+) in column (.+)\\.")) != null) {
            return formatSpanish("The schema change is incompatible with record {0} in column {1}.", values);
        }
        if ((values = capture(english, "Preparing (.+)\\.\\.\\.")) != null) {
            return formatSpanish("Preparing {0}...", values);
        }
        if ((values = capture(english, "Validating local plan (.+)\\.\\.\\.")) != null) {
            return formatSpanish("Validating local plan {0}...", values);
        }
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
        if ((values = capture(english, "Created (.+) from folder (.+)\\.")) != null) {
            return formatSpanish("Created {0} from folder {1}.", values);
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
        String spanishPattern = SPANISH_TEXT.get(pattern);
        if (spanishPattern == null) {
            spanishPattern = switch (pattern) {
                case "{0} es obligatorio." -> "{0} es obligatorio.";
                case "A table must have between 1 and {0} columns." ->
                    "La tabla debe tener entre 1 y {0} columnas.";
                case "Invalid value for {0}: {1}" -> "El valor de {0} no es válido: {1}";
                case "Invalid stored value for {0}: {1}" -> "El valor almacenado de {0} no es válido: {1}";
                case "A table named {0} already exists." -> "Ya existe una tabla llamada {0}.";
                case "A record with primary key {0} already exists." ->
                    "Ya existe un registro con la clave primaria {0}.";
                case "A value already exists for {0}." -> "Ya existe un valor para {0}.";
                case "The schema change is incompatible with record {0} in column {1}." ->
                    "El cambio de esquema es incompatible con el registro {0} en la columna {1}.";
                case "Preparing {0}..." -> "Preparando {0}...";
                case "Validating local plan {0}..." -> "Validando el plan local {0}...";
                default -> pattern;
            };
        }
        return new MessageFormat(spanishPattern, Locale.forLanguageTag("es")).format(arguments);
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
