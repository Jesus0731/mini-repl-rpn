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

/* Analizador léxico (tokenizador) para expresiones en Notación Polaca Inversa (RPN)
 * separadas estrictamente por espacios en blanco
 */
object Tokenizador {
    // Uno o más caracteres de espacio en blanco (espacios, tabuladores) usados como delimitadores
    private val REGEX_ESPACIOS = Regex("\\s+")

    // Literal entero decimal de 32 bits con signo opcional (ej. '0', '42', '-15', '+7')
    private val REGEX_ENTERO = Regex("^[+-]?\\d+$")

    
     /* Literal real decimal con parte entera y fraccionaria obligatorias y exponente científico
     * opcional (ej. '3.14', '-0.5', '1.0E7', '-2.5e-4'), manteniendo simetría con 'Double.toString()'
     */
    private val REGEX_FLOTANTE = Regex("^[+-]?\\d+\\.\\d+([eE][+-]?\\d+)?$")

    // Identificador de variable válido: letra o guion bajo inicial seguido de alfanuméricos o '_'
    private val REGEX_IDENTIFICADOR = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

    /* Convierte una línea de texto en una lista ordenada de [Token] válidos o devuelve
     * el primer [ErrorEval.TokenInvalido] encontrado
     *
     * @param linea Entrada cruda escrita por el usuario en el Mini-REPL.
     * @return [ResultadoEval.Ok] con la lista de tokens (vacía si la línea está en blanco)
     *         o [ResultadoEval.Err] ante cualquier lexema inválido.
     */
    fun tokenizar(linea: String): ResultadoEval<List<Token>> {
        val entradaLimpia = linea.trim()
        if (entradaLimpia.isEmpty()) {
            return ResultadoEval.Ok(emptyList())
        }

        val lexemas = entradaLimpia.split(REGEX_ESPACIOS)
        val tokens = ArrayList<Token>(lexemas.size)

        for (lexema in lexemas) {
            when (val resultado = clasificarLexema(lexema)) {
                is ResultadoEval.Ok -> tokens.add(resultado.valor)
                is ResultadoEval.Err -> return resultado
            }
        }

        return ResultadoEval.Ok(tokens)
    }

    /* Clasifica un lexema individual siguiendo una prioridad estricta para evitar ambigüedades
     * (por ejemplo, distinguir el operador binario '"-"' del literal negativo '"-5"', y los
     * comandos 'vars'/'begin'/'end' o literales 'true'/'false' de identificadores comunes)
     */
    private fun clasificarLexema(lexema: String): ResultadoEval<Token> {
        // 1. Comandos reservados del entorno y del REPL
        TipoComando.desdePalabra(lexema)?.let { comando ->
            return ResultadoEval.Ok(Token.Comando(comando))
        }

        // 2. Literales booleanos (reservados antes de evaluar identificadores)
        if (lexema == "true") {
            return ResultadoEval.Ok(Token.Literal(ValorBooleano(true)))
        }
        if (lexema == "false") {
            return ResultadoEval.Ok(Token.Literal(ValorBooleano(false)))
        }

        // 3. Operador de asignación postfija
        if (lexema == "=") {
            return ResultadoEval.Ok(Token.Asignacion)
        }

        // 4. Operadores aritméticos (deben evaluarse antes que números con signo)
        OperadorAritmetico.desdeSimbolo(lexema)?.let { opAritmetico ->
            return ResultadoEval.Ok(Token.Aritmetico(opAritmetico))
        }

        // 5. Operadores de comparación lógica
        OperadorComparacion.desdeSimbolo(lexema)?.let { opComparacion ->
            return ResultadoEval.Ok(Token.Comparacion(opComparacion))
        }

        // 6. Literales enteros (detecta desbordamiento de Int de 32 bits sin lanzar excepción)
        if (REGEX_ENTERO.matches(lexema)) {
            val numeroEntero = lexema.toIntOrNull()
                ?: return ResultadoEval.Err(ErrorEval.TokenInvalido(lexema))
            return ResultadoEval.Ok(Token.Literal(ValorEntero(numeroEntero)))
        }

        // 7. Literales flotantes (Double IEEE-754; rechaza valores infinitos y normaliza -0.0 a 0.0)
        if (REGEX_FLOTANTE.matches(lexema)) {
            val numeroCrudo = lexema.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: return ResultadoEval.Err(ErrorEval.TokenInvalido(lexema))
            val numeroFlotante = if (numeroCrudo == 0.0) 0.0 else numeroCrudo
            return ResultadoEval.Ok(Token.Literal(ValorFlotante(numeroFlotante)))
        }

        // 8. Identificadores de variables válidos
        if (REGEX_IDENTIFICADOR.matches(lexema)) {
            return ResultadoEval.Ok(Token.Identificador(lexema))
        }

        // 9. Cualquier otro símbolo o secuencia mal formada se reporta de forma controlada
        return ResultadoEval.Err(ErrorEval.TokenInvalido(lexema))
    }
}
