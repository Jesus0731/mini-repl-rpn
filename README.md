# Evaluador Postfijo / Mini-REPL en RPN (Kotlin)

**Universidad de Carabobo**  
**Facultad Experimental de Ciencias y Tecnología (FACYT)**  
**U.A. de Algoritmos, Programación y Lenguajes — CAO604: Lenguajes de Programación**  
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
├── src/
│   ├── Main.kt                 # Punto de entrada: instancia PilaEvaluacion, Entorno y ConsolaRepl
│   ├── model/
│   │   ├── ValorRpn.kt         # Tipos del evaluador: ValorEntero (Int), ValorFlotante (Float), ValorBooleano (Bool)
│   │   ├── Token.kt            # Unidades léxicas (sealed interface Token) y enums de operadores y comandos
│   │   └── ResultadoEval.kt    # Manejo idiomático de errores: ResultadoEval<out T> (Ok / Err) y ErrorEval
│   ├── core/
│   │   ├── Tokenizador.kt      # Analizador léxico por espacios en blanco
│   │   ├── PilaEvaluacion.kt   # Pila LIFO explícita que distingue valores (R-value) y referencias (L-value)
│   │   ├── Entorno.kt          # Tabla de símbolos con pila de alcances (begin/end) e interfaz VistaAlcance
│   │   └── Evaluador.kt        # Motor de evaluación postfija con paso explícito de pila y entorno
│   └── repl/
│       └── ConsolaRepl.kt      # Ciclo interactivo de consola (Read-Eval-Print)
└── test/
    └── TestRunner.kt           # Suite de 62 pruebas automatizadas (unitarias y de integración E2E)
```

---

## 3. Cómo Compilar, Ejecutar y Probar el Proyecto

### Requisitos Previos
- **JDK (Java Development Kit)** 17, 21 o 25 instalado (`java -version`).
- **Compilador de Kotlin (`kotlinc`)** en el `PATH` (`kotlinc -version`).

### A. Compilar y ejecutar el Mini-REPL interactivo (`src/`)

1. **Compilar el proyecto principal:**
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

2. **Ejecutar el Mini-REPL:**
   * **En Windows:**
     ```powershell
     java -jar out\kotlin-rpn-repl.jar
     ```
   * **En Linux / macOS:**
     ```bash
     java -jar out/kotlin-rpn-repl.jar
     ```

### B. Compilar y ejecutar la suite de pruebas (`test/TestRunner.kt`)

Para compilar los módulos del proyecto junto con [`test/TestRunner.kt`](test/TestRunner.kt) (excluyendo `src/Main.kt` para usar el `main()` del ejecutor de pruebas) y correr los **62 casos de prueba automatizados**:

* **En Windows (PowerShell):**
  ```powershell
  if (!(Test-Path out)) { mkdir out }
  kotlinc src\model src\core src\repl test -include-runtime -d out\kotlin-rpn-tests.jar
  java -jar out\kotlin-rpn-tests.jar
  ```
* **En Windows (CMD):**
  ```cmd
  if not exist out mkdir out
  kotlinc src\model src\core src\repl test -include-runtime -d out\kotlin-rpn-tests.jar
  java -jar out\kotlin-rpn-tests.jar
  ```
* **En Linux / macOS:**
  ```bash
  mkdir -p out
  kotlinc src/model src/core src/repl test -include-runtime -d out/kotlin-rpn-tests.jar
  java -jar out/kotlin-rpn-tests.jar
  ```
  **Resultado esperado:** `Resumen TestRunner -> Total: 62 | Aprobadas: 62 | Fallidas: 0`.

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

### Tabla Paso a Paso (Entrada vs. Salida Esperada)

| # | Entrada en RPN | Salida Esperada | Requisito Verificado |
| :---: | :--- | :--- | :--- |
| 1 | `x 10 =` | `x = 10 (Int)` | Requisito 3: Declaración postfija de variable |
| 2 | `x 5 + 2 *` | `=> 30 (Int)` | Requisito 1 y 3: Evaluación `(x + 5) * 2` en pila |
| 3 | `x 5 2 + =` | `x = 7 (Int)` | Requisito 3: Actualización postfija con expresión |
| 4 | `x 7 ==` | `=> true (Bool)` | Requisito 1 y 2: Comparación lógica que retorna `Bool` |
| 5 | `5 true +` | `Error de tipos: no se puede aplicar '+': se esperaba dos numeros (Int o Float), se recibio Int y Bool` | Requisito 2: Error de tipos controlado (`Int` + `Bool`) |
| 6 | `10 0 /` | `Error de ejecucion: division por cero al dividir 10 (Int)` | Requisito 1: División por cero controlada sin abortar |
| 7 | `5 +` | `Error de ejecucion: pila insuficiente para '+' (requiere 2, disponibles 1)` | Requisito 1: *Stack underflow* controlado |
| 8 | `5 @ +` | `Error lexico: token invalido '@'` | Requisito 1: Token inválido controlado |
| 9 | `vars` | Muestra `x = 7 (Int)` en `[nivel 0 / global]` | Requisito 3: Consulta de tabla de símbolos activa |
| 10 | `begin` | `Bloque abierto (nivel 1)` (el prompt cambia a `rpn[1]>`) | Requisito 4: Apertura de ámbito local temporal |
| 11 | `x 99 =` | `x = 99 (Int)` | Requisito 4: *Shadowing* local de `x` en nivel 1 |
| 12 | `y 3.5 =` | `y = 3.5 (Float)` | Requisito 2 y 4: Variable local `Float` en nivel 1 |
| 13 | `vars` | Muestra `x = 99`, `y = 3.5` en nivel 1 y `x = 7 [oculta por shadowing]` en nivel 0 | Requisito 3 y 4: Inspección de alcance y *shadowing* |
| 14 | `end` | `Bloque cerrado (nivel 0)` (el prompt vuelve a `rpn>`) | Requisito 4: Cierre de bloque y descarte del entorno local |
| 15 | `vars` | Muestra únicamente `x = 7 (Int)` en `[nivel 0 / global]` (`y` desapareció) | Requisito 4: La variable global `x` no fue sobrescrita |
| 16 | `x` | `=> 7 (Int)` | Requisito 4: Confirmación del valor original de `x` |
| 17 | `exit` | `Hasta luego.` | Cierre limpio de la sesión interactiva |
