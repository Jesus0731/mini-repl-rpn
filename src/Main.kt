package rpn

import rpn.core.Entorno
import rpn.core.PilaEvaluacion
import rpn.repl.ConsolaRepl

/**
 * Punto de entrada interactivo definitivo del Mini-REPL RPN (Bloque 5).
 *
 * Crea las instancias principales de estado ([PilaEvaluacion] y [Entorno]) y las
 * inyecta mediante argumentos nombrados en [ConsolaRepl], que se encarga del ciclo
 * interactivo de consola pasando ambos objetos explícitamente a cada evaluación.
 */
fun main() {
    val pila = PilaEvaluacion()
    val entorno = Entorno()
    val consola = ConsolaRepl(pila = pila, entorno = entorno)
    consola.iniciar()
}
