package rpn.model

/* Jerarquía sellada que representa los tipos de valores manejados en la pila de
 * evaluación y en la tabla de símbolos del Mini-REPL en Notación Polaca Inversa (RPN)
 */
sealed interface ValorRpn {
    // Nombre legible del tipo dentro del lenguaje del evaluador RPN ('"Int"', '"Float"' o '"Bool"')
    val nombreTipo: String

    // Devuelve la representación textual del valor sin incluir su etiqueta de tipo
    fun formatear(): String

    /**
     * Método con implementación por defecto en interfaz: combina el valor formateado
     * junto con su tipo explícito para mostrar respuestas claras en el Mini-REPL.
     */
    fun formatearConTipo(): String = "${formatear()} ($nombreTipo)"
}

/* Valor numérico entero de 32 bits con signo 
 *
 * Al envolver el 'Int' en una 'data class' que implementa [ValorRpn], en la JVM el campo
 * interno se almacena como primitivo 'int', aunque el objeto 'ValorEntero' vive en el Heap
 * cuando se referencia de forma polimórfica a través de [ValorRpn]
 */
data class ValorEntero(val valor: Int) : ValorRpn {
    override val nombreTipo: String
        get() = "Int"

    override fun formatear(): String = valor.toString()
}

// Valor numérico de punto flotante de doble precisión 
data class ValorFlotante(val valor: Double) : ValorRpn {
    override val nombreTipo: String
        get() = "Float"

    override fun formatear(): String = valor.toString()
}

/* Valor lógico booleano
 *
 * Permite representar el resultado de comparaciones lógicas ('==', '!=', '<', '>')
 * y detectar errores de tipos controlados si se intenta mezclar en operaciones aritméticas
 */
data class ValorBooleano(val valor: Boolean) : ValorRpn {
    override val nombreTipo: String
        get() = "Bool"

    override fun formatear(): String = valor.toString()
}
