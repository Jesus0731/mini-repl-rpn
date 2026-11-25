package rpn.model

/**
 * Enumeración cerrada de los operadores aritméticos binarios soportados por el Mini-REPL.
 * @property simbolo Símbolo léxico exacto tal como lo escribe el usuario en RPN.
 */
enum class OperadorAritmetico(val simbolo: String) {
    SUMA("+"),
    RESTA("-"),
    MULTIPLICACION("*"),
    DIVISION("/");

    companion object {
        /**
         * Busca el operador aritmético correspondiente a un lexema, o retorna 'null' si no coincide.
         * Demuestra el uso del sistema *null-safe* de Kotlin ('OperadorAritmetico?')
         */
        fun desdeSimbolo(texto: String): OperadorAritmetico? = entries.find { it.simbolo == texto }
    }
}

/**
 * Enumeración cerrada de los operadores de comparación lógica binarios del Mini-REPL.
 *
 * @property simbolo Símbolo léxico exacto del operador relacional o de igualdad.
 */
enum class OperadorComparacion(val simbolo: String) {
    IGUAL("=="),
    DISTINTO("!="),
    MENOR("<"),
    MAYOR(">");

    companion object {
        // Resuelve un lexema a su [OperadorComparacion] correspondiente, o retorna 'null' si no aplica
        fun desdeSimbolo(texto: String): OperadorComparacion? = entries.find { it.simbolo == texto }
    }
}

/**
 * Comandos especiales de control del entorno y sesión REPL
 *
 * @property palabraClave Palabra reservada principal reconocida por el tokenizador
 */
enum class TipoComando(val palabraClave: String) {
    // Muestra las variables activas en el entorno actual y los niveles ocultos por shadowing
    VARS("vars"),

    // Abre un nuevo entorno local temporal (bloque de alcance)
    BEGIN("begin"),

    // Cierra el entorno local actual descartando sus variables locales
    END("end"),

    // Muestra la ayuda rápida de operadores y comandos en consola
    HELP("help"),

    // Finaliza limpiamente la sesión interactiva del Mini-REPL
    EXIT("exit");

    companion object {
        /**
         * Resuelve una palabra clave a su comando. Incluye el alias '"quit"' para [EXIT]
         * manteniendo intacta la tabla canónica 'entries'
         */
        fun desdePalabra(texto: String): TipoComando? =
            if (texto == "quit") EXIT else entries.find { it.palabraClave == texto }
    }
}

/**
 * Tipo algebraico suma que representa cualquier unidad léxica válida producida por el tokenizador
 */
sealed interface Token {
    // Literal numérico o booleano ya convertido a [ValorRpn]
    data class Literal(val valor: ValorRpn) : Token

    // Identificador válido de variable (ej. 'x', 'contador_1')
    data class Identificador(val nombre: String) : Token

    // Token de operación aritmética ('+', '-', '*', '/')
    data class Aritmetico(val operador: OperadorAritmetico) : Token

    // Token de comparación lógica ('==', '!=', '<', '>')
    data class Comparacion(val operador: OperadorComparacion) : Token

    // Operador de asignación postfija ('='), modelado como singleton sin estado ('data object')
    data object Asignacion : Token

    // Comando de control de sesión o alcance ('vars', 'begin', 'end', 'help', 'exit')
    data class Comando(val tipo: TipoComando) : Token
}
