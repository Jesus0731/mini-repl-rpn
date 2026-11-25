package rpn.core

import rpn.model.ErrorEval
import rpn.model.ResultadoEval
import rpn.model.ValorRpn

// Vista de 'solo lectura' de la tabla de símbolos (entorno de variables)
interface VistaAlcance {
    // Profundidad de anidamiento actual: '0' indica únicamente el ambito global
    val profundidad: Int

    /* Busca el valor vigente de [nombre] recorriendo desde el bloque local más interno
     * hacia el ámbito global (regla de alcance léxico y *shadowing*). Retorna 'null' si no existe
     */
    fun buscar(nombre: String): ValorRpn?

    /* Devuelve un mapa inmutable con todas las variables visibles actualmente y sus valores
     * efectivos (donde las locales ocultan a las externas homónimas)
     */
    fun variablesActivas(): Map<String, ValorRpn>

    /* Devuelve una instantánea de todos los marcos activos desde el nivel '0' (global)
     * hasta el bloque más interno, útil para inspeccionar variables ocultas en 'vars'
     */
    fun jerarquiaAlcances(): List<Map<String, ValorRpn>>
}

/* Tabla de símbolos basada en una pila de marcos ('ArrayDeque<MutableMap<String, ValorRpn>>')
 * que implementa alcance local y 'shadowing' real para los comandos 'begin' y 'end'
 */
class Entorno : VistaAlcance {
    /* Pila privada de tablas de símbolos por nivel. Siempre conserva al menos un marco
     * en el índice '0' correspondiente al entorno global, usando 'LinkedHashMap' para
     * preservar el orden de declaración al listar con 'vars'
     */
    private val marcos: ArrayDeque<MutableMap<String, ValorRpn>> =
        ArrayDeque(listOf(linkedMapOf()))

    override val profundidad: Int
        get() = marcos.size - 1

    /* Abre un nuevo bloque de alcance local ('begin') apilando un mapa vacío en el tope
     *
     * @return La nueva [profundidad] del bloque recién abierto
     */
    fun abrirAlcance(): Int {
        marcos.addLast(linkedMapOf())
        return profundidad
    }

    /* Cierra el bloque local más interno ('end'), eliminando todas las variables creadas o
     * redefinidas dentro de ese bloque
     *
     * @return [ResultadoEval.Ok] con la profundidad resultante, o
     *         [ResultadoEval.Err] con [ErrorEval.OperacionDeAlcanceInvalida] si ya estaba en nivel '0'.
     */
    fun cerrarAlcance(): ResultadoEval<Int> {
        if (marcos.size <= 1) {
            return ResultadoEval.Err(
                ErrorEval.OperacionDeAlcanceInvalida(
                    "no hay ningun bloque local abierto para cerrar con 'end'"
                )
            )
        }
        marcos.removeLast()
        return ResultadoEval.Ok(profundidad)
    }

    
     /* Declara o actualiza [nombre] con [valor] en el marco activo más interno ('marcos.last()').
     * Garantiza que las modificaciones dentro de un bloque 'begin ... end' nunca sobrescriban
     * permanentemente variables homónimas de niveles externos.
     */
    fun asignar(nombre: String, valor: ValorRpn) {
        marcos.last()[nombre] = valor
    }

    override fun buscar(nombre: String): ValorRpn? {
        // Recorrido desde el ámbito más interno hacia el global (índice 0)
        for (indice in marcos.lastIndex downTo 0) {
            val valorEncontrado = marcos[indice][nombre]
            if (valorEncontrado != null) {
                return valorEncontrado
            }
        }
        return null
    }

    override fun variablesActivas(): Map<String, ValorRpn> {
        // Se apilan en orden ascendente (global -> local) para que las locales prevalezcan
        val activas = linkedMapOf<String, ValorRpn>()
        for (marco in marcos) {
            activas.putAll(marco)
        }
        return activas.toMap()
    }

    override fun jerarquiaAlcances(): List<Map<String, ValorRpn>> =
        marcos.map { marco -> marco.toMap() }
}
