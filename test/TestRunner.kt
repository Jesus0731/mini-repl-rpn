package rpn.test

import java.io.BufferedReader
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import kotlin.system.exitProcess
import rpn.core.Entorno
import rpn.core.EntradaPila
import rpn.core.Evaluador
import rpn.core.PilaEvaluacion
import rpn.core.SalidaEval
import rpn.core.Tokenizador
import rpn.core.VistaAlcance
import rpn.model.ErrorEval
import rpn.model.OperadorAritmetico
import rpn.model.OperadorComparacion
import rpn.model.ResultadoEval
import rpn.model.TipoComando
import rpn.model.Token
import rpn.model.ValorBooleano
import rpn.model.ValorEntero
import rpn.model.ValorFlotante
import rpn.repl.ConsolaRepl

/**
 * Ejecutor de pruebas automatizadas (Unitarias + Integración E2E) sin dependencias externas.
 
 */
private class RegistroPruebas {
    private var aprobadas = 0
    private var fallidas = 0

    fun probar(nombre: String, bloque: () -> Unit) {
        try {
            bloque()
            aprobadas++
            println("[PASS] $nombre")
        } catch (ex: Throwable) {
            fallidas++
            println("[FAIL] $nombre -> ${ex.message ?: ex::class.simpleName}")
        }
    }

    fun finalizar(): Int {
        println("============================================================")
        println("Resumen TestRunner -> Total: ${aprobadas + fallidas} | Aprobadas: $aprobadas | Fallidas: $fallidas")
        println("============================================================")
        return if (fallidas == 0) 0 else 1
    }
}

private fun <T> assertIgual(esperado: T, actual: T, contexto: String = "") {
    if (esperado != actual) {
        val prefijo = if (contexto.isNotEmpty()) "$contexto: " else ""
        throw AssertionError("${prefijo}se esperaba <$esperado> pero se obtuvo <$actual>")
    }
}

private fun assertVerdadero(condicion: Boolean, mensaje: String) {
    if (!condicion) {
        throw AssertionError(mensaje)
    }
}

private fun <T> exigirOk(resultado: ResultadoEval<T>): T = when (resultado) {
    is ResultadoEval.Ok -> resultado.valor
    is ResultadoEval.Err -> throw AssertionError("Se esperaba Ok pero ocurrio Err: ${resultado.error.formatear()}")
}

private fun <T> exigirErr(resultado: ResultadoEval<T>): ErrorEval = when (resultado) {
    is ResultadoEval.Ok -> throw AssertionError("Se esperaba Err pero ocurrio Ok(${resultado.valor})")
    is ResultadoEval.Err -> resultado.error
}

/**
 * Ejecuta una sesión completa de [ConsolaRepl] inyectando flujos en memoria y retorna
 * toda la salida textual generada para verificar prompts, resultados y manejo de errores.
 */
private fun ejecutarSesionRepl(entradas: List<String>): String {
    val textoEntrada = if (entradas.isEmpty()) "" else entradas.joinToString(separator = "\n") + "\n"
    val lector = BufferedReader(StringReader(textoEntrada))
    val bufferSalida = StringWriter()
    val escritor = PrintWriter(bufferSalida, true)
    val consola = ConsolaRepl(
        entrada = lector,
        salida = escritor,
        pila = PilaEvaluacion(),
        entorno = Entorno()
    )
    consola.iniciar()
    escritor.flush()
    return bufferSalida.toString()
}

fun main() {
    println("============================================================")
    println("  Suite de Pruebas Automatizadas - Mini-REPL RPN (62 casos)")
    println("============================================================")

    val registro = RegistroPruebas()

    // PRUEBAS DEL TOKENIZADOR (10 pruebas)
    registro.probar("Tokenizador 01: linea vacia y solo espacios devuelven lista vacia") {
        assertIgual(emptyList(), exigirOk(Tokenizador.tokenizar("")))
        assertIgual(emptyList(), exigirOk(Tokenizador.tokenizar("   \t   ")))
    }

    registro.probar("Tokenizador 02: enteros positivos, cero y negativos") {
        val tokens = exigirOk(Tokenizador.tokenizar("0 42 +7 -15"))
        assertIgual(
            listOf(
                Token.Literal(ValorEntero(0)),
                Token.Literal(ValorEntero(42)),
                Token.Literal(ValorEntero(7)),
                Token.Literal(ValorEntero(-15))
            ),
            tokens
        )
    }

    registro.probar("Tokenizador 03: flotantes positivos y negativos") {
        val tokens = exigirOk(Tokenizador.tokenizar("3.14 -0.5 +2.0"))
        assertIgual(
            listOf(
                Token.Literal(ValorFlotante(3.14)),
                Token.Literal(ValorFlotante(-0.5)),
                Token.Literal(ValorFlotante(2.0))
            ),
            tokens
        )
    }

    registro.probar("Tokenizador 04: literales booleanos true y false") {
        val tokens = exigirOk(Tokenizador.tokenizar("true false"))
        assertIgual(
            listOf(
                Token.Literal(ValorBooleano(true)),
                Token.Literal(ValorBooleano(false))
            ),
            tokens
        )
    }

    registro.probar("Tokenizador 05: operadores aritmeticos sin confundir '-' con '-5'") {
        val tokens = exigirOk(Tokenizador.tokenizar("+ - * / -5 +3"))
        assertIgual(
            listOf(
                Token.Aritmetico(OperadorAritmetico.SUMA),
                Token.Aritmetico(OperadorAritmetico.RESTA),
                Token.Aritmetico(OperadorAritmetico.MULTIPLICACION),
                Token.Aritmetico(OperadorAritmetico.DIVISION),
                Token.Literal(ValorEntero(-5)),
                Token.Literal(ValorEntero(3))
            ),
            tokens
        )
    }

    registro.probar("Tokenizador 06: comparaciones logicas y asignacion") {
        val tokens = exigirOk(Tokenizador.tokenizar("== != < > ="))
        assertIgual(
            listOf(
                Token.Comparacion(OperadorComparacion.IGUAL),
                Token.Comparacion(OperadorComparacion.DISTINTO),
                Token.Comparacion(OperadorComparacion.MENOR),
                Token.Comparacion(OperadorComparacion.MAYOR),
                Token.Asignacion
            ),
            tokens
        )
    }

    registro.probar("Tokenizador 07: comandos especiales incluyendo alias quit") {
        val tokens = exigirOk(Tokenizador.tokenizar("vars begin end help exit quit"))
        assertIgual(
            listOf(
                Token.Comando(TipoComando.VARS),
                Token.Comando(TipoComando.BEGIN),
                Token.Comando(TipoComando.END),
                Token.Comando(TipoComando.HELP),
                Token.Comando(TipoComando.EXIT),
                Token.Comando(TipoComando.EXIT)
            ),
            tokens
        )
    }

    registro.probar("Tokenizador 08: identificadores de variable validos") {
        val tokens = exigirOk(Tokenizador.tokenizar("x _tmp contador_1 valorMax"))
        assertIgual(
            listOf(
                Token.Identificador("x"),
                Token.Identificador("_tmp"),
                Token.Identificador("contador_1"),
                Token.Identificador("valorMax")
            ),
            tokens
        )
    }

    registro.probar("Tokenizador 09: expresiones reales del enunciado") {
        val asig = exigirOk(Tokenizador.tokenizar("x 10 ="))
        assertIgual(
            listOf(Token.Identificador("x"), Token.Literal(ValorEntero(10)), Token.Asignacion),
            asig
        )

        val expr = exigirOk(Tokenizador.tokenizar("x   5 +   2 *"))
        assertIgual(
            listOf(
                Token.Identificador("x"),
                Token.Literal(ValorEntero(5)),
                Token.Aritmetico(OperadorAritmetico.SUMA),
                Token.Literal(ValorEntero(2)),
                Token.Aritmetico(OperadorAritmetico.MULTIPLICACION)
            ),
            expr
        )
    }

    registro.probar("Tokenizador 10: tokens invalidos y desbordamiento entero/flotante") {
        for (invalido in listOf("@", "12abc", "5..2", "3.", "99999999999999", "1.0e999")) {
            val err = exigirErr(Tokenizador.tokenizar("5 $invalido +"))
            assertIgual(ErrorEval.TokenInvalido(invalido), err, "Para lexema '$invalido'")
        }
    }

    // PRUEBAS DE LA PILA DE EVALUACION (4 pruebas)
    registro.probar("PilaEvaluacion 11: orden LIFO con push y popOrNull") {
        val pila = PilaEvaluacion()
        assertVerdadero(pila.estaVacia, "La pila recien creada debe estar vacia")
        assertIgual(null, pila.popOrNull())

        pila.pushValor(ValorEntero(10))
        pila.pushValor(ValorEntero(20))
        assertIgual(2, pila.tamano)
        assertIgual(EntradaPila.EntradaValor(ValorEntero(20)), pila.popOrNull())
        assertIgual(EntradaPila.EntradaValor(ValorEntero(10)), pila.popOrNull())
        assertVerdadero(pila.estaVacia, "La pila debe quedar vacia tras extraer todo")
    }

    registro.probar("PilaEvaluacion 12: EntradaValor vs RefVariable con extraerPar") {
        val pila = PilaEvaluacion()
        pila.pushVariable("x")
        pila.pushValor(ValorFlotante(2.5))

        val (izq, der) = exigirOk(pila.extraerPar("="))
        assertIgual(EntradaPila.RefVariable("x"), izq)
        assertIgual(EntradaPila.EntradaValor(ValorFlotante(2.5)), der)
        assertVerdadero(pila.estaVacia, "La pila debe quedar vacia tras extraer el par")
    }

    registro.probar("PilaEvaluacion 13: extraerPar es atomico y devuelve PilaInsuficiente sin vaciar") {
        val pila = PilaEvaluacion()
        val errVacia = exigirErr(pila.extraerPar("+"))
        assertIgual(ErrorEval.PilaInsuficiente("+", requeridos = 2, disponibles = 0), errVacia)

        pila.pushValor(ValorEntero(5))
        val errUno = exigirErr(pila.extraerPar("-"))
        assertIgual(ErrorEval.PilaInsuficiente("-", requeridos = 2, disponibles = 1), errUno)
        assertIgual(1, pila.tamano, "El elemento unico debe permanecer intacto si extraerPar falla")

        pila.pushValor(ValorEntero(3))
        val (izq, der) = exigirOk(pila.extraerPar("-"))
        assertIgual(EntradaPila.EntradaValor(ValorEntero(5)), izq)
        assertIgual(EntradaPila.EntradaValor(ValorEntero(3)), der)
    }

    registro.probar("PilaEvaluacion 14: limpiar() vacia toda la pila") {
        val pila = PilaEvaluacion()
        pila.pushValor(ValorEntero(1))
        pila.pushVariable("z")
        pila.limpiar()
        assertVerdadero(pila.estaVacia, "La pila debe estar vacia tras limpiar()")
        assertIgual(0, pila.tamano)
    }


    //PRUEBAS DEL ENTORNO Y SHADOWING (8 pruebas)
    registro.probar("Entorno 15: profundidad inicial 0 y variable inexistente retorna null") {
        val entorno = Entorno()
        assertIgual(0, entorno.profundidad)
        assertIgual(null, entorno.buscar("inexistente"))
        assertIgual(emptyMap(), entorno.variablesActivas())
    }

    registro.probar("Entorno 16: declaracion, actualizacion global y orden en variablesActivas") {
        val entorno = Entorno()
        entorno.asignar("a", ValorEntero(1))
        entorno.asignar("b", ValorFlotante(2.5))
        entorno.asignar("a", ValorEntero(7))

        assertIgual(ValorEntero(7), entorno.buscar("a"))
        assertIgual(ValorFlotante(2.5), entorno.buscar("b"))
        assertIgual(listOf("a", "b"), entorno.variablesActivas().keys.toList())
    }

    registro.probar("Entorno 17: herencia de lectura en begin y desaparicion de locales al hacer end") {
        val entorno = Entorno()
        entorno.asignar("g", ValorEntero(100))

        assertIgual(1, entorno.abrirAlcance())
        assertIgual(ValorEntero(100), entorno.buscar("g"))
        entorno.asignar("local", ValorBooleano(true))
        assertIgual(ValorBooleano(true), entorno.buscar("local"))

        assertIgual(0, exigirOk(entorno.cerrarAlcance()))
        assertIgual(null, entorno.buscar("local"))
        assertIgual(ValorEntero(100), entorno.buscar("g"))
    }

    registro.probar("Entorno 18: shadowing real sin sobrescribir variable global tras end") {
        val entorno = Entorno()
        entorno.asignar("x", ValorEntero(10))

        entorno.abrirAlcance()
        entorno.asignar("x", ValorEntero(99))
        assertIgual(ValorEntero(99), entorno.buscar("x"))

        exigirOk(entorno.cerrarAlcance())
        assertIgual(ValorEntero(10), entorno.buscar("x"))
    }

    registro.probar("Entorno 19: bloques anidados de 2 niveles (0 -> 1 -> 2 -> 1 -> 0)") {
        val entorno = Entorno()
        entorno.asignar("x", ValorEntero(1))
        entorno.abrirAlcance()
        entorno.asignar("x", ValorEntero(2))
        entorno.abrirAlcance()
        entorno.asignar("x", ValorEntero(3))
        assertIgual(ValorEntero(3), entorno.buscar("x"))
        exigirOk(entorno.cerrarAlcance())
        assertIgual(ValorEntero(2), entorno.buscar("x"))
        exigirOk(entorno.cerrarAlcance())
        assertIgual(ValorEntero(1), entorno.buscar("x"))
    }

    registro.probar("Entorno 20: cerrar alcance en nivel global 0 produce error controlado") {
        val entorno = Entorno()
        val err = exigirErr(entorno.cerrarAlcance())
        assertVerdadero(err is ErrorEval.OperacionDeAlcanceInvalida, "Se esperaba OperacionDeAlcanceInvalida")
        assertIgual(0, entorno.profundidad)
    }

    registro.probar("Entorno 21: copias defensivas e independencia en VistaAlcance") {
        val entorno = Entorno()
        entorno.asignar("x", ValorEntero(10))
        entorno.abrirAlcance()
        entorno.asignar("x", ValorEntero(99))

        val vista: VistaAlcance = entorno
        val activasAntes = vista.variablesActivas()
        val jerarquiaAntes = vista.jerarquiaAlcances()
        assertIgual(2, jerarquiaAntes.size)

        entorno.asignar("nueva", ValorBooleano(false))
        assertVerdadero("nueva" !in activasAntes, "variablesActivas() debe ser copia independiente")
        assertVerdadero("nueva" !in jerarquiaAntes[1], "jerarquiaAlcances() debe ser copia independiente")
    }

    registro.probar("Entorno 22: jerarquiaAlcances preserva el valor global y el local por separado") {
        val entorno = Entorno()
        entorno.asignar("x", ValorEntero(10))
        entorno.abrirAlcance()
        entorno.asignar("x", ValorEntero(99))

        val jerarquia = entorno.jerarquiaAlcances()
        assertIgual(ValorEntero(10), jerarquia[0]["x"])
        assertIgual(ValorEntero(99), jerarquia[1]["x"])
    }

    // PRUEBAS DEL EVALUADOR RPN (22 pruebas)
    registro.probar("Evaluador 23: aritmetica entera, orden no conmutativo y negativos") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertIgual(SalidaEval.ResultadoValor(ValorEntero(7)), exigirOk(Evaluador.evaluarLinea("10 3 -", pila, entorno)))
        assertIgual(SalidaEval.ResultadoValor(ValorEntero(2)), exigirOk(Evaluador.evaluarLinea("5 2 /", pila, entorno)))
        assertIgual(SalidaEval.ResultadoValor(ValorEntero(16)), exigirOk(Evaluador.evaluarLinea("3 5 + 2 *", pila, entorno)))
        assertIgual(SalidaEval.ResultadoValor(ValorEntero(2)), exigirOk(Evaluador.evaluarLinea("-4 6 +", pila, entorno)))
    }

    registro.probar("Evaluador 24: aritmetica flotante y promocion mixta explicita Int/Float") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertIgual(SalidaEval.ResultadoValor(ValorFlotante(7.5)), exigirOk(Evaluador.evaluarLinea("5 2.5 +", pila, entorno)))
        assertIgual(SalidaEval.ResultadoValor(ValorFlotante(2.5)), exigirOk(Evaluador.evaluarLinea("5 2.0 /", pila, entorno)))
        assertIgual(SalidaEval.ResultadoValor(ValorFlotante(10.0)), exigirOk(Evaluador.evaluarLinea("2.5 4 *", pila, entorno)))
        assertIgual(SalidaEval.ResultadoValor(ValorFlotante(1.5)), exigirOk(Evaluador.evaluarLinea("4.0 2.5 -", pila, entorno)))
    }

    registro.probar("Evaluador 25: comparaciones numericas incluyendo mixtos Int/Float") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertIgual(SalidaEval.ResultadoValor(ValorBooleano(true)), exigirOk(Evaluador.evaluarLinea("3 7 <", pila, entorno)))
        assertIgual(SalidaEval.ResultadoValor(ValorBooleano(false)), exigirOk(Evaluador.evaluarLinea("3 7 >", pila, entorno)))
        assertIgual(SalidaEval.ResultadoValor(ValorBooleano(true)), exigirOk(Evaluador.evaluarLinea("5 5.0 ==", pila, entorno)))
        assertIgual(SalidaEval.ResultadoValor(ValorBooleano(true)), exigirOk(Evaluador.evaluarLinea("5 4 !=", pila, entorno)))
    }

    registro.probar("Evaluador 26: comparaciones de igualdad y desigualdad entre booleanos") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertIgual(SalidaEval.ResultadoValor(ValorBooleano(true)), exigirOk(Evaluador.evaluarLinea("true true ==", pila, entorno)))
        assertIgual(SalidaEval.ResultadoValor(ValorBooleano(true)), exigirOk(Evaluador.evaluarLinea("true false !=", pila, entorno)))
        assertIgual(SalidaEval.ResultadoValor(ValorBooleano(false)), exigirOk(Evaluador.evaluarLinea("true false ==", pila, entorno)))
    }

    registro.probar("Evaluador 27: errores de tipos controlados (5 true +, true 1 <, 5 true ==)") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        for (expr in listOf("5 true +", "true 1 <", "5 true ==", "false 0 !=", "true false +")) {
            val err = exigirErr(Evaluador.evaluarLinea(expr, pila, entorno))
            assertVerdadero(err is ErrorEval.ErrorDeTipos, "Para '$expr' se esperaba ErrorDeTipos y llego $err")
        }
    }

    registro.probar("Evaluador 28: division por cero controlada en Int, Float y mixta") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertVerdadero(exigirErr(Evaluador.evaluarLinea("10 0 /", pila, entorno)) is ErrorEval.DivisionPorCero, "Int / 0")
        assertVerdadero(exigirErr(Evaluador.evaluarLinea("5.0 0.0 /", pila, entorno)) is ErrorEval.DivisionPorCero, "Float / 0.0")
        assertVerdadero(exigirErr(Evaluador.evaluarLinea("5 0.0 /", pila, entorno)) is ErrorEval.DivisionPorCero, "Int / 0.0")
        assertVerdadero(exigirErr(Evaluador.evaluarLinea("5.0 0 /", pila, entorno)) is ErrorEval.DivisionPorCero, "Float / 0")
    }

    registro.probar("Evaluador 29: stack underflow en operadores aritmeticos, logicos y '='") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        for (expr in listOf("+", "5 +", "=", "x =", "<")) {
            val err = exigirErr(Evaluador.evaluarLinea(expr, pila, entorno))
            assertVerdadero(err is ErrorEval.PilaInsuficiente, "Para '$expr' se esperaba PilaInsuficiente y llego $err")
        }
    }

    registro.probar("Evaluador 30: asignacion postfija, lectura directa y expresion del enunciado") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertIgual(
            SalidaEval.ResultadoAsignacion("x", ValorEntero(10)),
            exigirOk(Evaluador.evaluarLinea("x 10 =", pila, entorno))
        )
        assertIgual(
            SalidaEval.ResultadoValor(ValorEntero(30)),
            exigirOk(Evaluador.evaluarLinea("x 5 + 2 *", pila, entorno))
        )
        assertIgual(
            SalidaEval.ResultadoAsignacion("x", ValorEntero(7)),
            exigirOk(Evaluador.evaluarLinea("x 5 2 + =", pila, entorno))
        )
        assertIgual(
            SalidaEval.ResultadoValor(ValorEntero(7)),
            exigirOk(Evaluador.evaluarLinea("x", pila, entorno))
        )
    }

    registro.probar("Evaluador 31: reasignacion autorreferenciada y copia entre variables") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        exigirOk(Evaluador.evaluarLinea("x 10 =", pila, entorno))
        assertIgual(
            SalidaEval.ResultadoAsignacion("x", ValorEntero(11)),
            exigirOk(Evaluador.evaluarLinea("x x 1 + =", pila, entorno))
        )
        assertIgual(
            SalidaEval.ResultadoAsignacion("y", ValorEntero(11)),
            exigirOk(Evaluador.evaluarLinea("y x =", pila, entorno))
        )
        assertIgual(ValorEntero(11), entorno.buscar("y"))
    }

    registro.probar("Evaluador 32: asignacion a literal produce AsignacionInvalida") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertVerdadero(exigirErr(Evaluador.evaluarLinea("5 10 =", pila, entorno)) is ErrorEval.AsignacionInvalida, "5 10 =")
        assertVerdadero(exigirErr(Evaluador.evaluarLinea("true 1 =", pila, entorno)) is ErrorEval.AsignacionInvalida, "true 1 =")
    }

    registro.probar("Evaluador 33: variable no definida en expresion, consulta y RHS de '='") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertIgual(ErrorEval.VariableNoDefinida("z"), exigirErr(Evaluador.evaluarLinea("z 1 +", pila, entorno)))
        assertIgual(ErrorEval.VariableNoDefinida("z"), exigirErr(Evaluador.evaluarLinea("z", pila, entorno)))
        assertIgual(ErrorEval.VariableNoDefinida("z"), exigirErr(Evaluador.evaluarLinea("y z =", pila, entorno)))
        assertIgual(null, entorno.buscar("y"), "'y' no debe crearse si fallo la evaluacion del RHS")
    }

    registro.probar("Evaluador 34: comando vars vacio, con variables y con marca de shadowing") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        val vacio = exigirOk(Evaluador.evaluarLinea("vars", pila, entorno)) as SalidaEval.Mensaje
        assertVerdadero(vacio.texto.contains("No hay variables definidas"), "Debe reportar sin variables")

        exigirOk(Evaluador.evaluarLinea("x 10 =", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("begin", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("x 99 =", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("y 3.5 =", pila, entorno))

        val listado = exigirOk(Evaluador.evaluarLinea("vars", pila, entorno)) as SalidaEval.Mensaje
        assertVerdadero(listado.texto.contains("x = 99 (Int)"), "Debe mostrar x local")
        assertVerdadero(listado.texto.contains("y = 3.5 (Float)"), "Debe mostrar y local")
        assertVerdadero(listado.texto.contains("x = 10 (Int) [oculta por shadowing]"), "Debe marcar x global como oculta")
    }

    registro.probar("Evaluador 35: begin y end protegen la variable homonima global") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        exigirOk(Evaluador.evaluarLinea("x 10 =", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("begin", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("x 99 =", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("local 42 =", pila, entorno))
        assertIgual(SalidaEval.ResultadoValor(ValorEntero(100)), exigirOk(Evaluador.evaluarLinea("x 1 +", pila, entorno)))

        exigirOk(Evaluador.evaluarLinea("end", pila, entorno))
        assertIgual(SalidaEval.ResultadoValor(ValorEntero(10)), exigirOk(Evaluador.evaluarLinea("x", pila, entorno)))
        assertIgual(ErrorEval.VariableNoDefinida("local"), exigirErr(Evaluador.evaluarLinea("local", pila, entorno)))
    }

    registro.probar("Evaluador 36: bloques anidados de dos niveles via comandos begin/end") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        exigirOk(Evaluador.evaluarLinea("x 1 =", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("begin", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("x 2 =", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("begin", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("x 3 =", pila, entorno))

        assertIgual(SalidaEval.ResultadoValor(ValorEntero(3)), exigirOk(Evaluador.evaluarLinea("x", pila, entorno)))
        exigirOk(Evaluador.evaluarLinea("end", pila, entorno))
        assertIgual(SalidaEval.ResultadoValor(ValorEntero(2)), exigirOk(Evaluador.evaluarLinea("x", pila, entorno)))
        exigirOk(Evaluador.evaluarLinea("end", pila, entorno))
        assertIgual(SalidaEval.ResultadoValor(ValorEntero(1)), exigirOk(Evaluador.evaluarLinea("x", pila, entorno)))
    }

    registro.probar("Evaluador 37: end en nivel global produce OperacionDeAlcanceInvalida") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        val err = exigirErr(Evaluador.evaluarLinea("end", pila, entorno))
        assertVerdadero(err is ErrorEval.OperacionDeAlcanceInvalida, "Se esperaba OperacionDeAlcanceInvalida")
    }

    registro.probar("Evaluador 38: comandos help, exit, quit y linea vacia") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertVerdadero(exigirOk(Evaluador.evaluarLinea("   ", pila, entorno)) is SalidaEval.SinOperacion, "Linea vacia")
        val ayuda = exigirOk(Evaluador.evaluarLinea("help", pila, entorno)) as SalidaEval.Mensaje
        assertVerdadero(ayuda.texto.contains("begin / end / vars"), "Ayuda debe listar comandos")
        assertIgual(SalidaEval.SenalSalida, exigirOk(Evaluador.evaluarLinea("exit", pila, entorno)))
        assertIgual(SalidaEval.SenalSalida, exigirOk(Evaluador.evaluarLinea("quit", pila, entorno)))
    }

    registro.probar("Evaluador 39: expresiones mal formadas con comandos mezclados o sobrantes en pila") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertVerdadero(exigirErr(Evaluador.evaluarLinea("begin 5 2 +", pila, entorno)) is ErrorEval.ExpresionMalFormada, "Comando mezclado")
        assertVerdadero(exigirErr(Evaluador.evaluarLinea("5 10", pila, entorno)) is ErrorEval.ExpresionMalFormada, "Operandos sobrantes")
        assertVerdadero(exigirErr(Evaluador.evaluarLinea("99 x 10 =", pila, entorno)) is ErrorEval.ExpresionMalFormada, "Sobrante tras asignacion")
    }

    registro.probar("Evaluador 40: token invalido en evaluarLinea no altera el entorno") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        exigirOk(Evaluador.evaluarLinea("x 10 =", pila, entorno))
        assertIgual(ErrorEval.TokenInvalido("@"), exigirErr(Evaluador.evaluarLinea("x @ +", pila, entorno)))
        assertIgual(ValorEntero(10), entorno.buscar("x"))
    }

    registro.probar("Evaluador 41: resiliencia completa tras multiples errores consecutivos") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        exigirOk(Evaluador.evaluarLinea("x 10 =", pila, entorno))
        exigirErr(Evaluador.evaluarLinea("5 true +", pila, entorno))
        exigirErr(Evaluador.evaluarLinea("10 0 /", pila, entorno))
        exigirErr(Evaluador.evaluarLinea("+", pila, entorno))
        exigirErr(Evaluador.evaluarLinea("end", pila, entorno))

        assertIgual(
            SalidaEval.ResultadoValor(ValorEntero(30)),
            exigirOk(Evaluador.evaluarLinea("x 5 + 2 *", pila, entorno))
        )
        assertVerdadero(pila.estaVacia, "La pila debe quedar limpia tras evaluar la expresion final")
    }

    registro.probar("Evaluador 42: paso explicito por valor de referencia (call-by-sharing)") {
        val pilaOriginal = PilaEvaluacion()
        val entornoOriginal = Entorno()

        exigirOk(Evaluador.evaluarLinea("k 25 =", pilaOriginal, entornoOriginal))
        assertIgual(ValorEntero(25), entornoOriginal.buscar("k"))
        assertIgual(SalidaEval.ResultadoValor(ValorEntero(30)), exigirOk(Evaluador.evaluarLinea("k 5 +", pilaOriginal, entornoOriginal)))
        assertVerdadero(pilaOriginal.estaVacia, "El objeto pilaOriginal compartido en Heap refleja las mutaciones")
    }

    registro.probar("Evaluador 43: formatearVariables acepta VistaAlcance de solo lectura") {
        val entorno = Entorno()
        entorno.asignar("pi", ValorFlotante(3.14))
        val vista: VistaAlcance = entorno
        val salida = Evaluador.formatearVariables(vista)
        assertVerdadero(salida.contains("pi = 3.14 (Float)"), "Debe formatear variables desde VistaAlcance")
    }

    registro.probar("Evaluador 44: expresion compuesta de variables, aritmetica y comparacion") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        exigirOk(Evaluador.evaluarLinea("a 8 =", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("b 3 =", pila, entorno))
        assertIgual(
            SalidaEval.ResultadoValor(ValorBooleano(true)),
            exigirOk(Evaluador.evaluarLinea("a b - 2 * 10 ==", pila, entorno))
        )
    }

    // PRUEBAS E2E DE CONSOLA REPL (6 pruebas)
    registro.probar("ConsolaRepl 45: sesion basica (x 10 =, x 5 + 2 *, vars, exit)") {
        val salida = ejecutarSesionRepl(listOf("x 10 =", "x 5 + 2 *", "vars", "exit"))
        assertVerdadero(salida.contains("Comandos: vars | begin | end | help | exit"), "Debe imprimir el banner inicial")
        assertVerdadero(salida.contains("rpn> x = 10 (Int)"), "Debe mostrar prompt y asignacion")
        assertVerdadero(salida.contains("=> 30 (Int)"), "Debe mostrar resultado 30 (Int)")
        assertVerdadero(salida.contains("x = 10 (Int)"), "vars debe incluir x")
        assertVerdadero(salida.contains("Hasta luego."), "Debe despedirse limpiamente en exit")
    }

    registro.probar("ConsolaRepl 46: prompt dinamico por profundidad de bloque") {
        val salida = ejecutarSesionRepl(listOf("begin", "begin", "end", "end", "exit"))
        assertVerdadero(salida.contains("rpn> Bloque abierto (nivel 1)"), "Prompt inicial debe ser rpn>")
        assertVerdadero(salida.contains("rpn[1]> Bloque abierto (nivel 2)"), "Prompt nivel 1 debe ser rpn[1]>")
        assertVerdadero(salida.contains("rpn[2]> Bloque cerrado (nivel 1)"), "Prompt nivel 2 debe ser rpn[2]>")
        assertVerdadero(salida.contains("rpn[1]> Bloque cerrado (nivel 0)"), "Prompt de vuelta a nivel 1")
        assertVerdadero(salida.contains("rpn> Hasta luego."), "Prompt de vuelta a nivel 0")
    }

    registro.probar("ConsolaRepl 47: resiliencia en sesion ante errores consecutivos") {
        val salida = ejecutarSesionRepl(
            listOf("5 true +", "10 0 /", "5 +", "@", "end", "2 3 +", "exit")
        )
        assertVerdadero(salida.contains("Error de tipos:"), "Debe reportar error de tipos")
        assertVerdadero(salida.contains("Error de ejecucion: division por cero"), "Debe reportar division por cero")
        assertVerdadero(salida.contains("Error de ejecucion: pila insuficiente"), "Debe reportar stack underflow")
        assertVerdadero(salida.contains("Error lexico: token invalido '@'"), "Debe reportar token invalido")
        assertVerdadero(salida.contains("Error de alcance:"), "Debe reportar end invalido en global")
        assertVerdadero(salida.contains("=> 5 (Int)"), "Debe seguir operando normalmente tras los errores")
        assertVerdadero(salida.contains("Hasta luego."), "Debe cerrar limpio con exit")
    }

    registro.probar("ConsolaRepl 48: sesion completa de scoping y shadowing en REPL") {
        val salida = ejecutarSesionRepl(
            listOf("x 10 =", "begin", "x 99 =", "y 3.5 =", "vars", "end", "vars", "x", "exit")
        )
        assertVerdadero(salida.contains("x = 99 (Int)"), "Debe mostrar x local en vars")
        assertVerdadero(salida.contains("y = 3.5 (Float)"), "Debe mostrar y local en vars")
        assertVerdadero(salida.contains("x = 10 (Int) [oculta por shadowing]"), "Debe indicar shadowing")
        assertVerdadero(salida.contains("rpn> => 10 (Int)"), "Al salir del bloque, x debe volver a 10 (Int)")
    }

    registro.probar("ConsolaRepl 49: cierre limpio ante fin de archivo (EOF)") {
        val salidaVacia = ejecutarSesionRepl(emptyList())
        assertVerdadero(
            salidaVacia.contains("Fin de entrada (EOF). Saliendo del Mini-REPL."),
            "Debe manejar EOF limpio sin entrada"
        )

        val salidaConDatos = ejecutarSesionRepl(listOf("4 5 +"))
        assertVerdadero(salidaConDatos.contains("=> 9 (Int)"), "Debe evaluar la linea previa al EOF")
        assertVerdadero(
            salidaConDatos.contains("Fin de entrada (EOF). Saliendo del Mini-REPL."),
            "Debe cerrar limpio al alcanzar EOF"
        )
    }

    registro.probar("ConsolaRepl 50: aviso al salir con bloques locales abiertos") {
        val salida = ejecutarSesionRepl(listOf("begin", "exit"))
        assertVerdadero(
            salida.contains("Aviso: cerrando sesion con 1 bloque(s) local(es) abierto(s)."),
            "Debe avisar si queda 1 bloque abierto al ejecutar exit"
        )
        assertVerdadero(salida.contains("Hasta luego."), "Debe imprimir despedida tras el aviso")
    }

    // COBERTURA DE TIPOS Y AUDITORIA PARA CASOS BORDE (12 pruebas)
    registro.probar("Final 51: variables booleanas (flag 5 3 > =, flag true ==, error en flag 5 +)") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertIgual(
            SalidaEval.ResultadoAsignacion("flag", ValorBooleano(true)),
            exigirOk(Evaluador.evaluarLinea("flag 5 3 > =", pila, entorno))
        )
        assertIgual(
            SalidaEval.ResultadoValor(ValorBooleano(true)),
            exigirOk(Evaluador.evaluarLinea("flag true ==", pila, entorno))
        )
        val err = exigirErr(Evaluador.evaluarLinea("flag 5 +", pila, entorno))
        assertVerdadero(err is ErrorEval.ErrorDeTipos, "flag 5 + debe producir ErrorDeTipos")
    }

    registro.probar("Final 52: variables flotantes y aritmetica combinada (r 2.5 =, r 2 * 5.0 ==)") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertIgual(
            SalidaEval.ResultadoAsignacion("r", ValorFlotante(2.5)),
            exigirOk(Evaluador.evaluarLinea("r 2.5 =", pila, entorno))
        )
        assertIgual(
            SalidaEval.ResultadoValor(ValorBooleano(true)),
            exigirOk(Evaluador.evaluarLinea("r 2 * 5.0 ==", pila, entorno))
        )
    }

    registro.probar("Final 53: comparaciones < y > con negativos y flotantes") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertIgual(
            SalidaEval.ResultadoValor(ValorBooleano(true)),
            exigirOk(Evaluador.evaluarLinea("-5 -2 <", pila, entorno))
        )
        assertIgual(
            SalidaEval.ResultadoValor(ValorBooleano(true)),
            exigirOk(Evaluador.evaluarLinea("-1.5 -2.5 >", pila, entorno))
        )
    }

    registro.probar("Final 54: division entera con negativos (-7 2 / -> -3, 7 -2 / -> -3)") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertIgual(
            SalidaEval.ResultadoValor(ValorEntero(-3)),
            exigirOk(Evaluador.evaluarLinea("-7 2 /", pila, entorno))
        )
        assertIgual(
            SalidaEval.ResultadoValor(ValorEntero(-3)),
            exigirOk(Evaluador.evaluarLinea("7 -2 /", pila, entorno))
        )
    }

    registro.probar("Final 55: multiples reasignaciones dentro de begin/end sin alterar variable global") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        exigirOk(Evaluador.evaluarLinea("x 10 =", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("begin", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("x 20 =", pila, entorno))
        exigirOk(Evaluador.evaluarLinea("x x 5 + =", pila, entorno))
        assertIgual(
            SalidaEval.ResultadoValor(ValorEntero(25)),
            exigirOk(Evaluador.evaluarLinea("x", pila, entorno))
        )
        exigirOk(Evaluador.evaluarLinea("end", pila, entorno))
        assertIgual(
            SalidaEval.ResultadoValor(ValorEntero(10)),
            exigirOk(Evaluador.evaluarLinea("x", pila, entorno))
        )
    }

    registro.probar("Auditoria 56: asignaciones mal formadas son atomicas y NO mutan el entorno") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertVerdadero(exigirErr(Evaluador.evaluarLinea("x 10 = 99", pila, entorno)) is ErrorEval.ExpresionMalFormada, "x 10 = 99")
        assertIgual(null, entorno.buscar("x"), "x no debe existir tras 'x 10 = 99'")

        assertVerdadero(exigirErr(Evaluador.evaluarLinea("99 x 10 =", pila, entorno)) is ErrorEval.ExpresionMalFormada, "99 x 10 =")
        assertIgual(null, entorno.buscar("x"), "x no debe existir tras '99 x 10 ='")

        assertVerdadero(exigirErr(Evaluador.evaluarLinea("x 10 = y 20 =", pila, entorno)) is ErrorEval.ExpresionMalFormada, "x 10 = y 20 =")
        assertIgual(null, entorno.buscar("x"), "x no debe existir tras 'x 10 = y 20 ='")
    }

    registro.probar("Auditoria 57: desbordamiento de Int en suma, resta y multiplicacion") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertVerdadero(exigirErr(Evaluador.evaluarLinea("2147483647 1 +", pila, entorno)) is ErrorEval.DesbordamientoNumerico, "MAX + 1")
        assertVerdadero(exigirErr(Evaluador.evaluarLinea("-2147483648 1 -", pila, entorno)) is ErrorEval.DesbordamientoNumerico, "MIN - 1")
        assertVerdadero(exigirErr(Evaluador.evaluarLinea("100000 100000 *", pila, entorno)) is ErrorEval.DesbordamientoNumerico, "100000 * 100000")
    }

    registro.probar("Auditoria 58: desbordamiento de Int en division Int.MIN_VALUE / -1") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        val err = exigirErr(Evaluador.evaluarLinea("-2147483648 -1 /", pila, entorno))
        assertVerdadero(err is ErrorEval.DesbordamientoNumerico, "MIN_VALUE / -1 debe reportar DesbordamientoNumerico")
    }

    registro.probar("Auditoria 59: desbordamiento de Float a Infinity reporta DesbordamientoNumerico") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        val err = exigirErr(Evaluador.evaluarLinea("1.0e308 10.0 *", pila, entorno))
        assertVerdadero(err is ErrorEval.DesbordamientoNumerico, "1.0e308 * 10.0 debe reportar DesbordamientoNumerico")
    }

    registro.probar("Auditoria 60: simetria lexica en flotantes con notacion cientifica (1.0E7)") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        assertIgual(
            SalidaEval.ResultadoValor(ValorFlotante(2.0e7)),
            exigirOk(Evaluador.evaluarLinea("1.0E7 2.0 *", pila, entorno))
        )
    }

    registro.probar("Auditoria 61: rechazo especifico de multiples operadores '=' en la misma linea") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        val err = exigirErr(Evaluador.evaluarLinea("x = 10 =", pila, entorno))
        assertVerdadero(err is ErrorEval.ExpresionMalFormada, "Se esperaba ExpresionMalFormada")
        assertVerdadero(err.mensaje.contains("solo se permite un operador '='"), "Mensaje debe indicar multiple '='")
    }

    registro.probar("Auditoria 62: normalizacion de -0.0 a 0.0 en literal y en aritmetica") {
        val pila = PilaEvaluacion()
        val entorno = Entorno()

        val resLiteral = exigirOk(Evaluador.evaluarLinea("-0.0", pila, entorno))
        assertIgual(SalidaEval.ResultadoValor(ValorFlotante(0.0)), resLiteral)
        assertIgual("0.0 (Float)", (resLiteral as SalidaEval.ResultadoValor).valor.formatearConTipo())

        val resMult = exigirOk(Evaluador.evaluarLinea("0.0 -1.0 *", pila, entorno))
        assertIgual(SalidaEval.ResultadoValor(ValorFlotante(0.0)), resMult)
        assertIgual("0.0 (Float)", (resMult as SalidaEval.ResultadoValor).valor.formatearConTipo())
    }

    exitProcess(registro.finalizar())
}
