package com.a.prestamos.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class NumberToLetterConverter {

    private static final String[] UNIDADES = {"", "UN ", "DOS ", "TRES ", "CUATRO ", "CINCO ", "SEIS ", "SIETE ", "OCHO ", "NUEVE "};
    private static final String[] ESPECIALES = {"DIEZ ", "ONCE ", "DOCE ", "TRECE ", "CATORCE ", "QUINCE ", "DIECISEIS ", "DIECISIETE ", "DIECIOCHO ", "DIECINUEVE "};
    private static final String[] DECENAS = {"", "", "VEINTE ", "TREINTA ", "CUARENTA ", "CINCUENTA ", "SESENTA ", "SETENTA ", "OCHENTA ", "NOVENTA "};
    private static final String[] CENTENAS = {"", "CIENTO ", "DOSCIENTOS ", "TRESCIENTOS ", "CUATROCIENTOS ", "QUINIENTOS ", "SEISCIENTOS ", "SETECIENTOS ", "OCHOCIENTOS ", "NOVECIENTOS "};

    public static String convert(BigDecimal monto) {
        if (monto == null) return "";

        monto = monto.setScale(2, RoundingMode.HALF_UP);

        long parteEntera = monto.longValue();
        int parteDecimal = monto.remainder(BigDecimal.ONE).multiply(new BigDecimal(100)).intValue();

        String texto = "SON: " + convertirNumero(parteEntera);

        // Corrección de bug 2: Validar singular/plural de la moneda
        String moneda = (parteEntera == 1) ? " SOL" : " SOLES";

        // .trim() ayuda a eliminar el espacio final extra que dejan las unidades
        return texto.trim() + " CON " + (parteDecimal < 10 ? "0" : "") + parteDecimal + "/100" + moneda;
    }

    private static String convertirNumero(long numero) {
        if (numero == 0) return "CERO ";

        // MILLONES
        if (numero >= 1000000) {
            String resto = (numero % 1000000 > 0) ? convertirNumero(numero % 1000000) : "";
            if (numero >= 2000000) {
                return convertirNumero(numero / 1000000).trim() + " MILLONES " + resto;
            }
            return "UN MILLON " + resto;
        }

        // MILES
        if (numero >= 1000) {
            String resto = (numero % 1000 > 0) ? convertirNumero(numero % 1000) : "";
            if (numero >= 2000) {
                return convertirNumero(numero / 1000).trim() + " MIL " + resto;
            }
            return "MIL " + resto;
        }

        // CENTENAS
        if (numero >= 100) {
            if (numero == 100) return "CIEN ";
            String resto = (numero % 100 > 0) ? convertirNumero(numero % 100) : "";
            return CENTENAS[(int) (numero / 100)] + resto;
        }

        // DECENAS
        if (numero >= 30) {
            String union = (numero % 10 != 0) ? "Y " : "";
            String unidad = (numero % 10 != 0) ? UNIDADES[(int) (numero % 10)] : "";
            return DECENAS[(int) (numero / 10)] + union + unidad;
        }

        // VEINTES
        if (numero >= 20) {
            if (numero == 20) return "VEINTE ";
            return "VEINTI" + UNIDADES[(int) (numero % 10)];
        }

        // DIECES (10-19)
        if (numero >= 10) {
            return ESPECIALES[(int) (numero - 10)];
        }

        // UNIDADES (0-9)
        return UNIDADES[(int) numero];
    }
}