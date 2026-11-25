package rpn.core

import rpn.model.ErrorEval
import rpn.model.ResultadoEval
import rpn.model.ValorRpn

/* Representa cada elemento almacenado en la pila de evaluacion postfija.
 *
 * Distingue dos conceptos fundamentales de lenguajes de programación:
 * - **R-value ([EntradaValor]):** un valor ya calculado o literal (`10`, `3.5`, `true`).
 * - **L-value / referencia simbólica ([RefVariable]):** el nombre de una variable (`x`),
 *   que puede actuar como destino de escritura en una asignación (`x 10 =`, `x x 1 + =`)
 *   o desreferenciarse para obtener su valor en una operación aritmética o lógica (`x 5 +`).
 */
sealed interface EntradaPila {
    // Valor concreto evaluado en la pila.
    data class EntradaValor(val valor: ValorRpn) : EntradaPila

    // Referencia por nombre a una variable en el entorno. 
    data class RefVariable(val nombre: String) : EntradaPila
}

/* Pila de evaluación LIFO respaldada por `ArrayDeque`.
 *
 * Se pasa como argumento a las subrutinas del evaluador (Requisito 3 del
 * enunciado) para ilustrar el paso por valor de referencias (*call-by-sharing*) en Kotlin
 * y la asignación de objetos mutables en el Heap.
 */
class PilaEvaluacion {
    // Contenedor encapsulado (privado) que impide alteraciones directas fuera de esta clase
    private val elementos: ArrayDeque<EntradaPila> = ArrayDeque()

    // Cantidad actual de elementos almacenados en la pila
    val tamano: Int
        get() = elementos.size

    //Indica si la pila no contiene ningún elemento
    val estaVacia: Boolean
        get() = elementos.isEmpty()

    // Apila una [EntradaPila] en el tope
    fun push(entrada: EntradaPila) {
        elementos.addLast(entrada)
    }

    //Atajo idiomático para apilar directamente un [ValorRpn] como R-value
    fun pushValor(valor: ValorRpn) {
        push(EntradaPila.EntradaValor(valor))
    }

    // Atajo idiomático para apilar el identificador de una variable como L-value potencial
    fun pushVariable(nombre: String) {
        push(EntradaPila.RefVariable(nombre))
    }

    // Extrae y retorna el tope de la pila, o `null` si está vacia
    fun popOrNull(): EntradaPila? = elementos.removeLastOrNull()

    /* Extrae un par de operandos `(izquierdo, derecho)` para un operador binario
     *
     * Si hay menos de 2 elementos en la pila, no extrae ninguno y retorna inmediatamente
     * [ErrorEval.PilaInsuficiente], eliminando comprobaciones redundantes en el evaluador
     */
    fun extraerPar(operacion: String): ResultadoEval<Pair<EntradaPila, EntradaPila>> {
        if (elementos.size < 2) {
            return ResultadoEval.Err(
                ErrorEval.PilaInsuficiente(
                    operacion = operacion,
                    requeridos = 2,
                    disponibles = elementos.size
                )
            )
        }
        // En RPN ('a b op'), 'b' fue el ultimo en entrar (tope = derecho) y 'a' está debajo (izquierdo)
        val derecho = elementos.removeLast()
        val izquierdo = elementos.removeLast()
        return ResultadoEval.Ok(izquierdo to derecho)
    }

    // Vacía completamente la pila al iniciar la evaluación de una nueva expresion
    fun limpiar() {
        elementos.clear()
    }
}
