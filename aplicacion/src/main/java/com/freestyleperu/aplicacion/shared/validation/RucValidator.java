package com.freestyleperu.aplicacion.shared.validation;

/** Validación sintáctica y del dígito verificador del RUC peruano. */
public final class RucValidator {

    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private RucValidator() {
    }

    public static boolean isValid(String value) {
        if (value == null || !value.matches("\\d{11}")) {
            return false;
        }
        int sum = 0;
        for (int index = 0; index < WEIGHTS.length; index++) {
            sum += Character.digit(value.charAt(index), 10) * WEIGHTS[index];
        }
        int expected = (11 - (sum % 11)) % 10;
        return expected == Character.digit(value.charAt(10), 10);
    }
}
