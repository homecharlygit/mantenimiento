# Mantenimiento

App para Android que calcula el **rendimiento de combustible** de tus vehículos y lleva la **bitácora de mantenimiento**, hecha en Kotlin + Jetpack Compose con **Material 3 Expressive** oficial (`androidx.compose.material3:material3:1.5.0-rc01`).

## Funciones
- **Varios vehículos:** nombre, marca/modelo, año, placas, kilometraje y color. Cambias entre ellos con los chips de arriba (toca el elegido o mantén presionado para editarlo).
- **Cargas de gasolina:** capturas el kilometraje del tablero, litros y total pagado. El rendimiento se calcula al momento contra la carga anterior, con vista previa mientras escribes y comparación contra la carga pasada.
  - Cálculo *lleno a lleno*: las cargas parciales se acumulan hasta la siguiente carga de tanque lleno, así el número sale bien aunque no siempre llenes.
  - Valida que el kilometraje sea mayor al de la carga anterior.
  - Estadísticas: último, promedio, mejor, peor, gasto total y costo por km. Gráfica de las últimas 20 cargas (arrastra para ver cada una).
  - Unidad en km/L o L/100 km.
- **Pregunta diaria:** a la hora que elijas llega una notificación por vehículo: *"¿Le hiciste algo a … hoy?"*. Puedes **responder desde la notificación** (se guarda como mantenimiento) o tocar *Nada hoy*. Si ya registraste algo ese día no pregunta.
- **Mantenimiento programado:** por fecha, por kilometraje o lo que llegue primero, con repetición opcional. Notificación cuando está por vencer (días/km configurables) y cuando vence, con botón *Ya lo hice* que lo registra y lo reprograma solo. Barra de progreso ondulada (`LinearWavyProgressIndicator`).

## Qué usa de M3 Expressive
- `MaterialExpressiveTheme` + `MotionScheme.expressive()`
- `FloatingActionButtonMenu` + `ToggleFloatingActionButton` para agregar
- Botones conectados (`ToggleButton` + `ButtonGroupDefaults.connected*ButtonShapes`)
- Botones con forma que se transforma al presionar (`ButtonDefaults.shapes()`)
- `MaterialShapes` (Cookie, Sunny) animadas, `LinearWavyProgressIndicator`
- Material You (colores del wallpaper) en Android 12+ y 8 paletas propias; el cambio de color se anima en toda la app
- Animaciones con resortes: entrada escalonada, números que ruedan, transiciones entre pantallas y regreso predictivo (Android 14+)

## Ajustes
- **Modo oscuro / claro** (toggle; la primera vez sigue al sistema)
- **Color del tema**
- **Pregunta diaria:** activar/desactivar, hora (selector de hora) y botón para probar
- **Avisar antes de que venza:** 1/3/7/14 días y 200/500/1000/2000 km
- **Desenfoque:** Dual Kawase (shaders AGSL, Android 13+) o Gaussiano (`RenderEffect`, Android 12+). Se aplica detrás de los formularios, del menú de agregar y en las transiciones. Con vista previa.
- **Respuesta háptica** con intensidad ajustable

## Notificaciones
Los recordatorios usan WorkManager, así que sobreviven reinicios del teléfono. En Android 13+ la app pide el permiso de notificaciones al abrirla. Algunas capas (MIUI, One UI con optimización agresiva) pueden retrasar el aviso unos minutos.

## Datos
Todo se guarda en el teléfono (`data.json` en el almacenamiento privado de la app) y entra en el respaldo automático de Android.

## Compilar
- **GitHub Actions:** el workflow `Compilar APK` genera el APK debug y release. Se descarga en *Actions → artifact `mantenimiento-apk`*. Para firmar con una llave fija agrega los secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` y `SIGNING_KEY_PASSWORD`.
- **Local:** `./gradlew assembleDebug` (JDK 17+).

Versiones: AGP 9.2.1 · Kotlin 2.3.10 · Compose BOM 2026.08.00 · material3 1.5.0-rc01 · WorkManager 2.10.1 · minSdk 26 · targetSdk 37.

## Licencia
GPL-3.0. Ver [LICENSE](LICENSE). El texto completo también viene dentro de la app en *Acerca de → Ver licencia*.
