/*
 * KOTLIN PSI SPEC TEST (POSITIVE)
 *
 * SECTIONS: constant-literals, real-literals
 * PARAGRAPH: 2
 * SENTENCE: [1] The exponent is an exponent mark (e or E) followed by an optionaly signed decimal integer (a sequence of decimal digits).
 * NUMBER: 4
 * DESCRIPTION: Simple real literals suffixed by f/F (float suffix) with an exponent mark.
 */

val value = 0.0e0f
val value = 0.0e-00F
val value = 0.0E000F
val value = 0.0E+0000f

val value = 00.0e+0f
val value = 000.00e00f
val value = 0000.000E-000F

val value = 1.0E+1F
val value = 22.00e22F
val value = 333.000e-333f
val value = 4444.0000e4444F
val value = 55555.0E-55555f
val value = 666666.00e666666f
val value = 7777777.000E7777777F
val value = 88888888.0000e-88888888f
val value = 999999999.0E+999999999F

val value = 0000000000.1234567890E999999999f
val value = 123456789.23456789E+123456789f
val value = 2345678.345678e00000000001f
val value = 34567.4567E-100000000000F
val value = 456.56e-0F
val value = 5.65e000000000000F
val value = 654.7654E+010f
val value = 76543.876543E1F
val value = 8765432.98765432e-2f
val value = 987654321.0987654321E-3F

val value = 0.1111e4F
val value = 1.22222E-5F
val value = 9.33333e+6f
val value = 9.444444E7F
val value = 8.5555555e8f
val value = 2.66666666e308f
val value = 3.777777777E-308f
val value = 7.8888888888e+309F
val value = 6.99999999999e-309f

val value = 7.888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888888e+111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111f
val value = 0.000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000F
val value = 0.000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000e-000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000f
val value = 0.000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000e+000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000F
