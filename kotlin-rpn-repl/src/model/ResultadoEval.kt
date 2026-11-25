package rpn.model

/* Jerarquía sellada para representar de manera explícita todos los errores controlados
 * que pueden ocurrir durante la tokenización, verificación de tipos o evaluación en pila
 */
sealed interface ErrorEval {
    // Categoría breve del error (ej. '"Error de tipos"', '"Error de ejecucion"')
    val categoria: String

    // Descripción legible para el usuario explicando el motivo del fallo
    val mensaje: String

    // Formato estándar de salida en consola
    fun formatear(): String = "$categoria: $mensaje"

    // El texto ingresado contiene un símbolo o literal que no cumple las reglas léxicas
    data class TokenInvalido(val texto: String) : ErrorEval {
        override val categoria: String get() = "Error lexico"
        override val mensaje: String get() = "token invalido '$texto'"
    }

    // Se intentó aplicar un operador sin la cantidad mínima de operandos en la pila
    data class PilaInsuficiente(
        val operacion: String,
        val requeridos: Int,
        val disponibles: Int
    ) : ErrorEval {
        override val categoria: String get() = "Error de ejecucion"
        override val mensaje: String
            get() = "pila insuficiente para '$operacion' (requiere $requeridos, disponibles $disponibles)"
    }

    // Intento de división entre cero con divisor entero ('0') o flotante ('0.0')
    data class DivisionPorCero(val dividendo: ValorRpn) : ErrorEval {
        override val categoria: String get() = "Error de ejecucion"
        override val mensaje: String
            get() = "division por cero al dividir ${dividendo.formatearConTipo()}"
    }

    /**
     * El resultado de una operación aritmética supera el rango representable de
     * 'Int' (32 bits con signo) o de 'Float' ('Double' IEEE-754 finito)
     */
    data class DesbordamientoNumerico(
        val operacion: String,
        val detalle: String
    ) : ErrorEval {
        override val categoria: String get() = "Error de ejecucion"
        override val mensaje: String
            get() = "desbordamiento numerico en '$operacion' ($detalle)"
    }

    // Los tipos presentes en la pila son incompatibles con el operador solicitado (ej. '5 true +')
    data class ErrorDeTipos(
        val operacion: String,
        val esperado: String,
        val recibido: String
    ) : ErrorEval {
        override val categoria: String get() = "Error de tipos"
        override val mensaje: String
            get() = "no se puede aplicar '$operacion': se esperaba $esperado, se recibio $recibido"
    }

    // Se consultó o intentó operar sobre una variable no declarada en el alcance visible
    data class VariableNoDefinida(val nombre: String) : ErrorEval {
        override val categoria: String get() = "Error de alcance"
        override val mensaje: String get() = "la variable '$nombre' no esta declarada"
    }

    // La asignación postfija '=' no tiene un identificador destino válido (ej. '5 10 =')
    data class AsignacionInvalida(val motivo: String) : ErrorEval {
        override val categoria: String get() = "Error de asignacion"
        override val mensaje: String get() = motivo
    }

    // Operación de bloque inválida, como ejecutar 'end' sin un 'begin' abierto
    data class OperacionDeAlcanceInvalida(val motivo: String) : ErrorEval {
        override val categoria: String get() = "Error de alcance"
        override val mensaje: String get() = motivo
    }

    // La expresión finalizó con múltiples operandos sin consumir o mezcla inválida con comandos
    data class ExpresionMalFormada(val motivo: String) : ErrorEval {
        override val categoria: String get() = "Error de expresion"
        override val mensaje: String get() = motivo
    }
}

/* Tipo resultado genérico y covariante para propagar éxitos ([Ok]) o errores ([Err])
 * sin usar excepciones como control de flujo normal
 */
sealed interface ResultadoEval<out T> {
    // Resultado exitoso que envuelve el valor producido de tipo [T]
    data class Ok<out T>(val valor: T) : ResultadoEval<T>

    // Resultado fallido que transporta un [ErrorEval] concreto; compatible con cualquier 'T'
    data class Err(val error: ErrorEval) : ResultadoEval<Nothing>
}

/* Transforma el valor contenido si el resultado es [ResultadoEval.Ok]; si es [ResultadoEval.Err],
 * propaga el mismo error sin ejecutar la lambda.
 *
 * Marcada como 'inline' (Punto 3): el compilador sustituye el cuerpo de la función y la
 * lambda directamente en el sitio de llamada, evitando asignar un objeto función en el Heap
 */
inline fun <T, R> ResultadoEval<T>.map(transformar: (T) -> R): ResultadoEval<R> = when (this) {
    is ResultadoEval.Ok -> ResultadoEval.Ok(transformar(valor))
    is ResultadoEval.Err -> this
}

/* Encadena una operación que también devuelve [ResultadoEval], evitando anidamientos
 * 'ResultadoEval<ResultadoEval<R>>' (composición monádica idiomática con 'inline')
 */
inline fun <T, R> ResultadoEval<T>.flatMap(transformar: (T) -> ResultadoEval<R>): ResultadoEval<R> = when (this) {
    is ResultadoEval.Ok -> transformar(valor)
    is ResultadoEval.Err -> this
}
