package rpn.repl

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import rpn.core.Entorno
import rpn.core.Evaluador
import rpn.core.PilaEvaluacion
import rpn.core.SalidaEval
import rpn.core.VistaAlcance
import rpn.model.ResultadoEval

/* Consola interactiva (REPL: Read-Eval-Print Loop) para el evaluador en Notación Polaca Inversa
 */
class ConsolaRepl(
    private val entrada: BufferedReader = BufferedReader(InputStreamReader(System.`in`)),
    private val salida: PrintWriter = PrintWriter(System.out, true),
    private val pila: PilaEvaluacion = PilaEvaluacion(),
    private val entorno: Entorno = Entorno()
) {
    /* Inicia el bucle interactivo Read-Eval-Print hasta recibir el comando 'exit'/'quit'
     * o alcanzar el fin de archivo (EOF) en el flujo de entrada
     */
    fun iniciar() {
        salida.println("============================================================")
        salida.println("  Comandos: vars | begin | end | help | exit")
        salida.println("============================================================")

        var continuar = true
        while (continuar) {
            salida.print(construirPrompt(entorno))
            salida.flush()

            val linea = entrada.readLine()
            if (linea == null) {
                salida.println("\nFin de entrada (EOF). Saliendo del Mini-REPL.")
                break
            }

            continuar = procesarUnaLinea(linea)
        }
    }

    /* Evalúa una única línea ingresada por el usuario delegando explícitamente en
     * [Evaluador.evaluarLinea] con la [pila] y el [entorno] compartidos.
     *
     * @return 'true' si el REPL debe continuar pidiendo entradas, 'false' si se solicitó salir
     */
    fun procesarUnaLinea(linea: String): Boolean {
        return try {
            when (val resultado = Evaluador.evaluarLinea(linea, pila, entorno)) {
                is ResultadoEval.Ok -> when (val valorSalida = resultado.valor) {
                    is SalidaEval.ResultadoValor -> {
                        salida.println("=> ${valorSalida.valor.formatearConTipo()}")
                        true
                    }
                    is SalidaEval.ResultadoAsignacion -> {
                        salida.println("${valorSalida.nombre} = ${valorSalida.valor.formatearConTipo()}")
                        true
                    }
                    is SalidaEval.Mensaje -> {
                        salida.println(valorSalida.texto)
                        true
                    }
                    is SalidaEval.SinOperacion -> true
                    is SalidaEval.SenalSalida -> {
                        if (entorno.profundidad > 0) {
                            salida.println(
                                "Aviso: cerrando sesion con ${entorno.profundidad} bloque(s) local(es) abierto(s)."
                            )
                        }
                        salida.println("Hasta luego.")
                        false
                    }
                }
                is ResultadoEval.Err -> {
                    salida.println(resultado.error.formatear())
                    true
                }
            }
        } catch (ex: Exception) {
            // Red de seguridad adicional para evitar un cierre abrupto ante fallos imprevistos
            salida.println("Error inesperado controlado: ${ex.message ?: ex::class.simpleName}")
            true
        }
    }

    /* Construye el indicador de consola reflejando el nivel de anidamiento de bloques 'begin'/'end'
     * Recibe [VistaAlcance] (solo lectura) reforzando el principio de mínimo privilegio
     */
    private fun construirPrompt(alcance: VistaAlcance): String =
        if (alcance.profundidad == 0) {
            "rpn> "
        } else {
            "rpn[${alcance.profundidad}]> "
        }
}
