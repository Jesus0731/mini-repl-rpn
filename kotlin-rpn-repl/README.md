# Evaluador Postfijo / Mini-REPL en RPN (Kotlin)

**Universidad de Carabobo**  
**Facultad Experimental de Ciencias y Tecnología (FACYT)**  
**CAO604: Lenguajes de Programación**  
**Asignación #1 (Semestre 1-2026) — Parte II: Componente Práctico**  
**Lenguaje seleccionado:** Kotlin (`kotlinc-jvm 2.4.21` sobre JVM / JDK 21+)  
**Integrantes:**
- `Jesús Herrada`
- `Samuel Reyna`

---

## 1. Descripción General

Aplicación interactiva de consola (**REPL**: *Read-Eval-Print Loop*) desarrollada en **Kotlin** que evalúa expresiones matemáticas y comparaciones lógicas utilizando estrictamente **Notación Polaca Inversa (Postfija / RPN)** basada en una pila explícita (*stack*), tokenizando la entrada por espacios en blanco y poniendo en práctica los cuatro ejes analizados en el Reporte Técnico.

### Cumplimiento de los 4 Requisitos Técnicos del Enunciado

1. **Evaluación en Pila y Manejo de Errores:**
   - Soporta las operaciones aritméticas básicas (`+`, `-`, `*`, `/`) y comparaciones lógicas (`==`, `!=`, `<`, `>`) operando sobre la pila en estricto orden postfijo.
   - Maneja de forma controlada todos los errores léxicos, sintácticos y de ejecución (**división por cero**, **pila insuficiente / *stack underflow***, **tokens inválidos** y **desbordamiento numérico**) utilizando el tipo algebraico idiomático `ResultadoEval<out T>` (`Ok` / `Err`), garantizando que el Mini-REPL nunca aborte abruptamente.
2. **Sistema de Tipos del Evaluador:**
   - Representa los valores de la pila, tokens y operadores mediante tipos compuestos nativos de Kotlin: `sealed interface`, `data class`, `data object` y `enum class`.
   - Distingue estrictamente entre números enteros (`Int`), números de punto flotante (`Float`) y booleanos (`Bool`), reportando un error de tipos controlado cuando se intenta una operación inválida en la pila (por ejemplo, `5 true +`).
3. **Variables y Paso Explícito de Parámetros:**
   - Permite declarar y actualizar variables en notación postfija (ej. `x 10 =`, `x 5 2 + =`) y consultar las variables activas en memoria mediante el comando `vars`.
   - La pila de evaluación (`PilaEvaluacion`) y la tabla de símbolos (`Entorno` / `VistaAlcance`) **se pasan explícitamente como parámetros a todas las subrutinas del evaluador** (sin variables globales ocultas), demostrando en el código el paso por valor de referencias (*call-by-sharing*) de Kotlin y el control de mutabilidad a través de interfaces de solo lectura (`VistaAlcance`) frente a clases mutables (`Entorno`).
4. **Alcance Local (*Scoping* y *Shadowing*):**
   - Incluye los comandos `begin` (apertura de bloque) y `end` (cierre de bloque) respaldados por una pila de marcos (`ArrayDeque<MutableMap<String, ValorRpn>>`).
   - Las variables definidas o modificadas dentro de un ámbito local ocultarán temporalmente (*shadowing*) a las variables homónimas externas sin sobrescribirlas permanentemente una vez cerrado el bloque con `end`.

---

## 2. Estructura del Repositorio

```text
.
├── .gitignore
├── README.md
└── src/
    ├── Main.kt                 # Punto de entrada: instancia PilaEvaluacion, Entorno y ConsolaRepl
    ├── model/
    │   ├── ValorRpn.kt         # Tipos del evaluador: ValorEntero (Int), ValorFlotante (Float), ValorBooleano (Bool)
    │   ├── Token.kt            # Unidades léxicas (sealed interface Token) y enums de operadores y comandos
    │   └── ResultadoEval.kt    # Manejo idiomático de errores: ResultadoEval<out T> (Ok / Err) y ErrorEval
    ├── core/
    │   ├── Tokenizador.kt      # Analizador léxico por espacios en blanco
    │   ├── PilaEvaluacion.kt   # Pila LIFO explícita que distingue valores (R-value) y referencias (L-value)
    │   ├── Entorno.kt          # Tabla de símbolos con pila de alcances (begin/end) e interfaz VistaAlcance
    │   └── Evaluador.kt        # Motor de evaluación postfija con paso explícito de pila y entorno
    └── repl/
        └── ConsolaRepl.kt      # Ciclo interactivo de consola (Read-Eval-Print)
```

---

## 3. Cómo Compilar y Ejecutar el Proyecto

### Requisitos Previos
- **JDK (Java Development Kit)** 17, 21 o 25 instalado (`java -version`).
- **Compilador de Kotlin (`kotlinc`)** en el `PATH` (`kotlinc -version`).

### Paso 1: Compilar el código fuente
Desde la raíz del repositorio (`kotlin-rpn-repl`), compilar todo el directorio `src` en un archivo `.jar` autocontenido con el runtime de Kotlin incluido:

* **En Windows (PowerShell):**
  ```powershell
  if (!(Test-Path out)) { mkdir out }
  kotlinc src -include-runtime -d out\kotlin-rpn-repl.jar
  ```
* **En Windows (CMD):**
  ```cmd
  if not exist out mkdir out
  kotlinc src -include-runtime -d out\kotlin-rpn-repl.jar
  ```
* **En Linux / macOS:**
  ```bash
  mkdir -p out
  kotlinc src -include-runtime -d out/kotlin-rpn-repl.jar
  ```

### Paso 2: Ejecutar el Mini-REPL
* **En Windows:**
  ```powershell
  java -jar out\kotlin-rpn-repl.jar
  ```
* **En Linux / macOS:**
  ```bash
  java -jar out/kotlin-rpn-repl.jar
  ```

---

## 4. Referencia Rápida de Sintaxis RPN y Comandos

| Categoría | Sintaxis / Operadores | Ejemplo de Entrada | Salida Esperada |
| :--- | :--- | :--- | :--- |
| **Aritmética** | `+`, `-`, `*`, `/` | `3 5 + 2 *` | `=> 16 (Int)` |
| **Comparación lógica** | `==`, `!=`, `<`, `>` | `10 5 >` | `=> true (Bool)` |
| **Asignación de variable** | `<identificador> <expresion> =` | `x 10 =` / `x 5 2 + =` | `x = 10 (Int)` / `x = 7 (Int)` |
| **Consulta de variables** | `vars` | `vars` | Lista variables por nivel y *shadowing* |
| **Alcance local** | `begin` / `end` | `begin` ... `end` | Abre / cierra un ámbito local temporal |
| **Ayuda y salida** | `help`, `exit` (o `quit`) | `exit` | `Hasta luego.` |

---

## 5. Sesión de Prueba de Ejemplo (Entradas en RPN y Salidas Esperadas)

A continuación se presenta una sesión breve de prueba que verifica los 4 requisitos técnicos del enunciado (asignación y evaluación postfija, chequeo de tipos, manejo de errores sin abortar y alcance local con `begin`/`end` y `vars`):

### Entradas en RPN
```text
x 10 =
x 5 + 2 *
x 5 2 + =
x 7 ==
5 true +
10 0 /
5 +
5 @ +
vars
begin
x 99 =
y 3.5 =
vars
end
vars
x
exit
```

### Salidas Esperadas (Consola)
```text
============================================================
  Comandos: vars | begin | end | help | exit
============================================================
rpn> x = 10 (Int)
rpn> => 30 (Int)
rpn> x = 7 (Int)
rpn> => true (Bool)
rpn> Error de tipos: no se puede aplicar '+': se esperaba dos numeros (Int o Float), se recibio Int y Bool
rpn> Error de ejecucion: division por cero al dividir 10 (Int)
rpn> Error de ejecucion: pila insuficiente para '+' (requiere 2, disponibles 1)
rpn> Error lexico: token invalido '@'
rpn> Variables activas (profundidad actual: 0):
  [nivel 0 / global]
    x = 7 (Int)
rpn> Bloque abierto (nivel 1)
rpn[1]> x = 99 (Int)
rpn[1]> y = 3.5 (Float)
rpn[1]> Variables activas (profundidad actual: 1):
  [nivel 1 / local]
    x = 99 (Int)
    y = 3.5 (Float)
  [nivel 0 / global]
    x = 7 (Int) [oculta por shadowing]
rpn[1]> Bloque cerrado (nivel 0)
rpn> Variables activas (profundidad actual: 0):
  [nivel 0 / global]
    x = 7 (Int)
rpn> => 7 (Int)
rpn> Hasta luego.
```