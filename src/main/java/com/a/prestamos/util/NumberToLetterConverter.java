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

        return texto + "CON " + (parteDecimal < 10 ? "0" : "") + parteDecimal + "/100 SOLES";
    }

    private static String convertirNumero(long numero) {
        if (numero == 0) return "CERO ";

        if (numero >= 1000000) {
            if (numero >= 2000000) {
                return convertirNumero(numero / 1000000) + "MILLONES " + convertirNumero(numero % 1000000);
            }
            return "UN MILLON " + convertirNumero(numero % 1000000);
        }

        if (numero >= 1000) {
            if (numero >= 2000) {
                return convertirNumero(numero / 1000) + "MIL " + convertirNumero(numero % 1000);
            }
            return "MIL " + convertirNumero(numero % 1000);
        }

        if (numero >= 100) {
            if (numero == 100) return "CIEN ";
            return CENTENAS[(int) (numero / 100)] + convertirNumero(numero % 100);
        }

        if (numero >= 30) {
            String union = (numero % 10 != 0) ? "Y " : "";
            return DECENAS[(int) (numero / 10)] + union + UNIDADES[(int) (numero % 10)];
        }

        if (numero >= 20) {
            if (numero == 20) return "VEINTE ";
            return "VEINTI" + UNIDADES[(int) (numero % 10)].trim() + " ";
        }

        if (numero >= 10) {
            return ESPECIALES[(int) (numero - 10)];
        }

        return UNIDADES[(int) numero];
    }
}