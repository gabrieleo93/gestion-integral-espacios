# Prueba local de principio a fin. Cada ejecución correcta crea una incidencia en la BD de desarrollo.
param([Parameter(Mandatory)][SecureString]$ClaveDemo)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$cliente = [Net.Http.HttpClient]::new()
$cliente.BaseAddress = [Uri]'http://localhost:8080'
$correo = 'vecino.demo@example.test'

function Leer-Json([string]$ruta) {
    $respuesta = $cliente.GetAsync($ruta).GetAwaiter().GetResult()
    try {
        $null = $respuesta.EnsureSuccessStatusCode()
        $respuesta.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    } finally { $respuesta.Dispose() }
}

try {
    # Spring exige CSRF en el login y renueva el token al autenticar la sesión.
    $csrf = Leer-Json '/api/csrf'
    $campos = [Collections.Generic.Dictionary[string,string]]::new()
    $campos.Add('username', $correo)
    $campos.Add('password', ([pscredential]::new($correo,$ClaveDemo)).GetNetworkCredential().Password)
    $campos.Add($csrf.parameterName, $csrf.token)
    $form = [Net.Http.FormUrlEncodedContent]::new($campos)
    $login = $cliente.PostAsync('/login',$form).GetAwaiter().GetResult()
    $login.Dispose()
    $form.Dispose()
    $campos.Clear()
    if ((Leer-Json '/api/sesion').email -ne $correo) { throw 'No se ha autenticado la cuenta demo.' }

    # Seleccionamos el espacio real del usuario sin fijar identificadores de esta instalación.
    $espacio = @(Leer-Json '/api/espacios') | Where-Object { $_.nombre -eq 'Portal principal' -and $_.comunidadNombre -eq 'Comunidad de demostración' } | Select-Object -First 1
    if (!$espacio) { throw 'La cuenta demo no tiene acceso al Portal principal de su comunidad.' }
    $csrf = Leer-Json '/api/csrf'
    $cliente.DefaultRequestHeaders.Add([string]$csrf.headerName,[string]$csrf.token)
    $datos = @{titulo='El pulsador del portal no responde'; descripcion='Al pulsar el botón de la entrada, la luz del portal no se enciende.'; prioridad='MEDIA'; espacioId=$espacio.id}
    $body = [Net.Http.StringContent]::new(($datos | ConvertTo-Json),[Text.Encoding]::UTF8,'application/json')
    $alta = $cliente.PostAsync('/api/incidencias',$body).GetAwaiter().GetResult()
    if ([int]$alta.StatusCode -ne 201) { throw "Alta rechazada: HTTP $([int]$alta.StatusCode)." }
    $nueva = $alta.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    $id = $nueva.id
    Write-Output "Alta HTTP 201. Incidencia creada: $id."

    # Seguimos el ID devuelto por el POST para verificar exactamente esa incidencia.
    if (!$id -or $nueva.estado -ne 'PENDIENTE') { throw 'Respuesta de alta inesperada.' }
    if ($id -notin @((Leer-Json '/api/incidencias').id)) { throw 'La incidencia no aparece en el listado.' }
    $detalle = Leer-Json "/api/incidencias/$id"
    if ($detalle.id -ne $id -or $detalle.titulo -ne $datos.titulo -or $detalle.descripcion -ne $datos.descripcion -or $detalle.espacioId -ne $espacio.id) { throw 'El detalle no coincide con el alta.' }
    $historial = @(Leer-Json "/api/incidencias/$id/historial")
    if ($historial.Count -lt 1 -or $historial[0].accion -ne 'CREACION' -or $historial[0].estadoNuevo -ne 'PENDIENTE' -or $historial[0].usuarioActorId -ne $detalle.usuarioReportaId) { throw 'No se ha encontrado el evento inicial esperado.' }
    Write-Output "Listado, detalle e historial correctos. Evento $($historial[0].accion), estado $($detalle.estado), espacio $($detalle.espacioNombre)."
} finally { $cliente.Dispose() }
