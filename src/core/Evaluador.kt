package rpn.core

import rpn.model.ErrorEval
import rpn.model.OperadorAritmetico
import rpn.model.OperadorComparacion
import rpn.model.ResultadoEval
import rpn.model.TipoComando
import rpn.model.Token
import rpn.model.ValorBooleano
import rpn.model.ValorEntero
import rpn.model.ValorFlotante
import rpn.model.ValorRpn
import rpn.model.flatMap
import rpn.model.map

// Representa las posibles salidas exitosas de evaluar una línea en el Mini-REPL RPN
sealed interface SalidaEval {
    // La expresión dejó un valor resultante único en la pila (ej. 'x 5 + 2 *' -> '30')
    data class ResultadoValor(val valor: ValorRpn) : SalidaEval

    // La línea ejecutó una asignación postfija válida (ej. 'x 10 ='). 
    data class ResultadoAsignacion(val nombre: String, val valor: ValorRpn) : SalidaEval

    // Un comando informativo o de bloque produjo un mensaje de estado ('vars', 'begin', 'end', 'help')
    data class Mensaje(val texto: String) : SalidaEval

    // El usuario solicitó cerrar la sesión ('exit' o 'quit')
    data object SenalSalida : SalidaEval

    // La línea estaba vacía o contenía solo espacios; no requiere acción ni mensaje de error
    data object SinOperacion : SalidaEval
}

// Motor de evaluación en Notación Polaca Inversa (RPN) del Mini-REPL
object Evaluador {

    /* Punto de entrada principal para procesar una línea de texto crudo:
     * tokeniza por espacios y encadena con [evaluarTokens] mediante 'flatMap'
     */
    fun evaluarLinea(
        linea: String,
        pila: PilaEvaluacion,
        entorno: Entorno
    ): ResultadoEval<SalidaEval> =
        Tokenizador.tokenizar(linea).flatMap { tokens ->
            evaluarTokens(tokens, pila, entorno)
        }

    /* Evalúa una lista de [Token] ya validados sobre la [pila] y el [entorno] recibidos
     * explícitamente por parámetro
     */
    fun evaluarTokens(
        tokens: List<Token>,
        pila: PilaEvaluacion,
        entorno: Entorno
    ): ResultadoEval<SalidaEval> {
        if (tokens.isEmpty()) {
            return ResultadoEval.Ok(SalidaEval.SinOperacion)
        }

        // Cada línea evalúa una expresión RPN independiente evitando contaminación tras errores
        pila.limpiar()

        // Validación estructural previa 1: los comandos deben ingresarse solos en su propia línea
        val primerComando = tokens.firstOrNull { it is Token.Comando } as? Token.Comando
        if (primerComando != null) {
            if (tokens.size > 1) {
                return ResultadoEval.Err(
                    ErrorEval.ExpresionMalFormada(
                        "el comando '${primerComando.tipo.palabraClave}' debe ingresarse solo en la linea"
                    )
                )
            }
            return aplicarComando(primerComando.tipo, entorno)
        }

        // Validación estructural previa 2:
        // En una línea puede haber a lo sumo un operador '=' y, si existe, debe ubicarse al final.
        // Esto impide asignaciones múltiples ('x 10 = y 20 =', 'x = 10 =') y mutaciones parciales ('x 10 = 99')
        val totalAsignaciones = tokens.count { it is Token.Asignacion }
        if (totalAsignaciones > 1) {
            return ResultadoEval.Err(
                ErrorEval.ExpresionMalFormada(
                    "solo se permite un operador '=' por linea de asignacion"
                )
            )
        }
        if (totalAsignaciones == 1 && tokens.last() !is Token.Asignacion) {
            return ResultadoEval.Err(
                ErrorEval.ExpresionMalFormada(
                    "el operador '=' solo puede ubicarse al final de la expresion de asignacion"
                )
            )
        }

        for (token in tokens) {
            when (token) {
                is Token.Literal -> pila.pushValor(token.valor)
                is Token.Identificador -> pila.pushVariable(token.nombre)
                is Token.Aritmetico -> {
                    when (val res = aplicarAritmetica(token.operador, pila, entorno)) {
                        is ResultadoEval.Ok -> Unit
                        is ResultadoEval.Err -> return res
                    }
                }
                is Token.Comparacion -> {
                    when (val res = aplicarComparacion(token.operador, pila, entorno)) {
                        is ResultadoEval.Ok -> Unit
                        is ResultadoEval.Err -> return res
                    }
                }
                is Token.Asignacion -> {
                    // Al ser único y estar al final de la línea, retorna su resultado de forma atómica
                    return aplicarAsignacion(pila, entorno).map { (nombre, valor) ->
                        SalidaEval.ResultadoAsignacion(nombre = nombre, valor = valor)
                    }
                }
                is Token.Comando -> Unit // Caso ya despachado en la validación estructural previa 1
            }
        }

        // En expresiones de cálculo o consulta debe quedar exactamente 1 resultado en el tope
        val entradaUnica = if (pila.tamano == 1) pila.popOrNull() else null
        if (entradaUnica == null) {
            return ResultadoEval.Err(
                ErrorEval.ExpresionMalFormada(
                    "la expresion termino con ${pila.tamano} elementos en la pila (faltan operadores)"
                )
            )
        }

        return resolverValor(entradaUnica, entorno).map { valorFinal ->
            SalidaEval.ResultadoValor(valorFinal)
        }
    }

    /* Convierte una [EntradaPila] en un [ValorRpn] concreto:
     * - Si es [EntradaPila.EntradaValor] (R-value), lo devuelve directamente.
     * - Si es [EntradaPila.RefVariable] (L-value simbólico), consulta su valor vigente en [VistaAlcance]
     */
    private fun resolverValor(
        entrada: EntradaPila,
        entorno: VistaAlcance
    ): ResultadoEval<ValorRpn> = when (entrada) {
        is EntradaPila.EntradaValor -> ResultadoEval.Ok(entrada.valor)
        is EntradaPila.RefVariable -> {
            val valorEncontrado = entorno.buscar(entrada.nombre)
            if (valorEncontrado != null) {
                ResultadoEval.Ok(valorEncontrado)
            } else {
                ResultadoEval.Err(ErrorEval.VariableNoDefinida(entrada.nombre))
            }
        }
    }

    /* Helper compartido que extrae atómicamente dos operandos de la [pila] mediante
     * [PilaEvaluacion.extraerPar] y resuelve posibles variables en [entorno]
     * Elimina la duplicación de código entre [aplicarAritmetica] y [aplicarComparacion]
     */
    private fun extraerOperandosResueltos(
        simboloOperador: String,
        pila: PilaEvaluacion,
        entorno: VistaAlcance
    ): ResultadoEval<Pair<ValorRpn, ValorRpn>> {
        val (entradaIzq, entradaDer) = when (val par = pila.extraerPar(simboloOperador)) {
            is ResultadoEval.Ok -> par.valor
            is ResultadoEval.Err -> return par
        }
        val izq = when (val res = resolverValor(entradaIzq, entorno)) {
            is ResultadoEval.Ok -> res.valor
            is ResultadoEval.Err -> return res
        }
        val der = when (val res = resolverValor(entradaDer, entorno)) {
            is ResultadoEval.Ok -> res.valor
            is ResultadoEval.Err -> return res
        }
        return ResultadoEval.Ok(izq to der)
    }

    /* Ejecuta una operación aritmética binaria ('+', '-', '*', '/') sobre la [pila].
     *
     * Reglas de tipos y seguridad numérica:
     * - Ambos operandos deben ser numéricos ([ValorEntero] o [ValorFlotante]); si aparece
     *   [ValorBooleano] (ej. '5 true +'), se reporta [ErrorEval.ErrorDeTipos].
     * - '(Int, Int)' usa 'Math.addExact', 'Math.subtractExact', 'Math.multiplyExact' y valida
     *   'Int.MIN_VALUE / -1' para reportar [ErrorEval.DesbordamientoNumerico] en vez de *wrap-around*.
     * - Operaciones con [ValorFlotante] verifican '.isFinite()' para impedir 'Infinity' o 'NaN'
     *   y normalizan '-0.0' a '0.0'.
     */
    private fun aplicarAritmetica(
        operador: OperadorAritmetico,
        pila: PilaEvaluacion,
        entorno: VistaAlcance
    ): ResultadoEval<ValorRpn> {
        val (izq, der) = when (val ops = extraerOperandosResueltos(operador.simbolo, pila, entorno)) {
            is ResultadoEval.Ok -> ops.valor
            is ResultadoEval.Err -> return ops
        }

        if (izq is ValorBooleano || der is ValorBooleano) {
            return ResultadoEval.Err(
                ErrorEval.ErrorDeTipos(
                    operacion = operador.simbolo,
                    esperado = "dos numeros (Int o Float)",
                    recibido = "${izq.nombreTipo} y ${der.nombreTipo}"
                )
            )
        }

        val resultado: ValorRpn = if (izq is ValorEntero && der is ValorEntero) {
            try {
                when (operador) {
                    OperadorAritmetico.SUMA -> ValorEntero(Math.addExact(izq.valor, der.valor))
                    OperadorAritmetico.RESTA -> ValorEntero(Math.subtractExact(izq.valor, der.valor))
                    OperadorAritmetico.MULTIPLICACION -> ValorEntero(Math.multiplyExact(izq.valor, der.valor))
                    OperadorAritmetico.DIVISION -> {
                        if (der.valor == 0) {
                            return ResultadoEval.Err(ErrorEval.DivisionPorCero(izq))
                        }
                        if (izq.valor == Int.MIN_VALUE && der.valor == -1) {
                            return ResultadoEval.Err(
                                ErrorEval.DesbordamientoNumerico(
                                    operacion = operador.simbolo,
                                    detalle = "Int.MIN_VALUE / -1 excede el rango Int de 32 bits"
                                )
                            )
                        }
                        ValorEntero(izq.valor / der.valor)
                    }
                }
            } catch (_: ArithmeticException) {
                return ResultadoEval.Err(
                    ErrorEval.DesbordamientoNumerico(
                        operacion = operador.simbolo,
                        detalle = "rango Int de 32 bits excedido"
                    )
                )
            }
        } else {
            val numIzq = aDouble(izq)
            val numDer = aDouble(der)
            val resultadoCrudo = when (operador) {
                OperadorAritmetico.SUMA -> numIzq + numDer
                OperadorAritmetico.RESTA -> numIzq - numDer
                OperadorAritmetico.MULTIPLICACION -> numIzq * numDer
                OperadorAritmetico.DIVISION -> {
                    if (numDer == 0.0) {
                        return ResultadoEval.Err(ErrorEval.DivisionPorCero(izq))
                    }
                    numIzq / numDer
                }
            }
            if (!resultadoCrudo.isFinite()) {
                return ResultadoEval.Err(
                    ErrorEval.DesbordamientoNumerico(
                        operacion = operador.simbolo,
                        detalle = "el resultado flotante excede el rango finito IEEE-754"
                    )
                )
            }
            val resultadoNormalizado = if (resultadoCrudo == 0.0) 0.0 else resultadoCrudo
            ValorFlotante(resultadoNormalizado)
        }

        pila.pushValor(resultado)
        return ResultadoEval.Ok(resultado)
    }

    /* Ejecuta una comparación lógica binaria ('==', '!=', '<', '>') dejando un [ValorBooleano]
     * en la pila
     *
     * Si ambos operandos son [ValorEntero], compara directamente en 'Int' sin convertir a 'Double'
     */
    private fun aplicarComparacion(
        operador: OperadorComparacion,
        pila: PilaEvaluacion,
        entorno: VistaAlcance
    ): ResultadoEval<ValorRpn> {
        val (izq, der) = when (val ops = extraerOperandosResueltos(operador.simbolo, pila, entorno)) {
            is ResultadoEval.Ok -> ops.valor
            is ResultadoEval.Err -> return ops
        }

        val resultadoBool: Boolean = when {
            izq is ValorBooleano && der is ValorBooleano -> when (operador) {
                OperadorComparacion.IGUAL -> izq.valor == der.valor
                OperadorComparacion.DISTINTO -> izq.valor != der.valor
                OperadorComparacion.MENOR, OperadorComparacion.MAYOR -> {
                    return ResultadoEval.Err(
                        ErrorEval.ErrorDeTipos(
                            operacion = operador.simbolo,
                            esperado = "dos numeros (Int o Float)",
                            recibido = "${izq.nombreTipo} y ${der.nombreTipo}"
                        )
                    )
                }
            }

            izq is ValorEntero && der is ValorEntero -> when (operador) {
                OperadorComparacion.IGUAL -> izq.valor == der.valor
                OperadorComparacion.DISTINTO -> izq.valor != der.valor
                OperadorComparacion.MENOR -> izq.valor < der.valor
                OperadorComparacion.MAYOR -> izq.valor > der.valor
            }

            izq !is ValorBooleano && der !is ValorBooleano -> {
                val numIzq = aDouble(izq)
                val numDer = aDouble(der)
                when (operador) {
                    OperadorComparacion.IGUAL -> numIzq == numDer
                    OperadorComparacion.DISTINTO -> numIzq != numDer
                    OperadorComparacion.MENOR -> numIzq < numDer
                    OperadorComparacion.MAYOR -> numIzq > numDer
                }
            }

            else -> {
                val esperado = if (operador == OperadorComparacion.IGUAL ||
                    operador == OperadorComparacion.DISTINTO
                ) {
                    "dos numeros o dos booleanos"
                } else {
                    "dos numeros (Int o Float)"
                }
                return ResultadoEval.Err(
                    ErrorEval.ErrorDeTipos(
                        operacion = operador.simbolo,
                        esperado = esperado,
                        recibido = "${izq.nombreTipo} y ${der.nombreTipo}"
                    )
                )
            }
        }

        val valorFinal = ValorBooleano(resultadoBool)
        pila.pushValor(valorFinal)
        return ResultadoEval.Ok(valorFinal)
    }

    /* Ejecuta de forma atómica la asignación postfija '=' (ej. 'x 10 =', 'x 5 2 + =', 'x x 1 + =')
     *
     * Verifica que no existan operandos sobrantes en la pila **antes** de escribir en [entorno],
     * garantizando que una expresión mal formada como '99 x 10 =' jamás mute la tabla de símbolos
     */
    private fun aplicarAsignacion(
        pila: PilaEvaluacion,
        entorno: Entorno
    ): ResultadoEval<Pair<String, ValorRpn>> {
        if (pila.tamano > 2) {
            return ResultadoEval.Err(
                ErrorEval.ExpresionMalFormada(
                    "la asignacion dejo ${pila.tamano - 2} elemento(s) sin consumir en la pila"
                )
            )
        }

        val (entradaDestino, entradaValor) = when (val par = pila.extraerPar("=")) {
            is ResultadoEval.Ok -> par.valor
            is ResultadoEval.Err -> return par
        }

        if (entradaDestino !is EntradaPila.RefVariable) {
            return ResultadoEval.Err(
                ErrorEval.AsignacionInvalida(
                    "el destino de '=' debe ser un identificador de variable (no un literal)"
                )
            )
        }

        val valorResuelto = when (val res = resolverValor(entradaValor, entorno)) {
            is ResultadoEval.Ok -> res.valor
            is ResultadoEval.Err -> return res
        }

        entorno.asignar(entradaDestino.nombre, valorResuelto)
        return ResultadoEval.Ok(entradaDestino.nombre to valorResuelto)
    }

    // Ejecuta los comandos de control del entorno y sesión ('vars', 'begin', 'end', 'help', 'exit')
    private fun aplicarComando(
        comando: TipoComando,
        entorno: Entorno
    ): ResultadoEval<SalidaEval> = when (comando) {
        TipoComando.VARS -> ResultadoEval.Ok(SalidaEval.Mensaje(formatearVariables(entorno)))
        TipoComando.BEGIN -> {
            val nivel = entorno.abrirAlcance()
            ResultadoEval.Ok(SalidaEval.Mensaje("Bloque abierto (nivel $nivel)"))
        }
        TipoComando.END -> entorno.cerrarAlcance().map { nivel ->
            SalidaEval.Mensaje("Bloque cerrado (nivel $nivel)")
        }
        TipoComando.HELP -> ResultadoEval.Ok(SalidaEval.Mensaje(textoAyuda()))
        TipoComando.EXIT -> ResultadoEval.Ok(SalidaEval.SenalSalida)
    }

    // Formatea la tabla de símbolos para el comando 'vars'
    fun formatearVariables(alcance: VistaAlcance): String {
        val activas = alcance.variablesActivas()
        if (activas.isEmpty()) {
            return "No hay variables definidas (nivel ${alcance.profundidad})."
        }

        val jerarquia = alcance.jerarquiaAlcances()
        val constructor = StringBuilder()
        constructor.append("Variables activas (profundidad actual: ${alcance.profundidad}):")
        val nombresMostrados = mutableSetOf<String>()

        for (nivel in jerarquia.lastIndex downTo 0) {
            val marco = jerarquia[nivel]
            if (marco.isEmpty()) continue

            val etiquetaNivel = if (nivel == 0) "nivel 0 / global" else "nivel $nivel / local"
            constructor.append("\n  [$etiquetaNivel]")

            for ((nombre, valor) in marco) {
                val marcaShadowing = if (!nombresMostrados.add(nombre)) {
                    " [oculta por shadowing]"
                } else {
                    ""
                }
                constructor.append("\n    $nombre = ${valor.formatearConTipo()}$marcaShadowing")
            }
        }

        return constructor.toString()
    }

    // Texto de ayuda breve en consola con la sintaxis RPN, operadores y comandos soportados
    private fun textoAyuda(): String = buildString {
        appendLine("Comandos y operadores del Mini-REPL RPN:")
        appendLine("  Aritmetica    : +  -  *  /          (ej: 3 5 + 2 *)")
        appendLine("  Comparaciones : ==  !=  <  >        (ej: 10 5 >)")
        appendLine("  Variables     : <nombre> <expr> =   (ej: x 10 = , x x 1 + =)")
        appendLine("  Alcances      : begin / end / vars  (bloques locales con shadowing)")
        append("  Sesion        : help / exit / quit")
    }

    // Convierte valores numéricos a 'Double' de manera explícita; rechaza booleanos
    private fun aDouble(valor: ValorRpn): Double = when (valor) {
        is ValorEntero -> valor.valor.toDouble()
        is ValorFlotante -> valor.valor
        is ValorBooleano -> throw IllegalArgumentException("No se puede convertir Bool a Double")
    }
}
