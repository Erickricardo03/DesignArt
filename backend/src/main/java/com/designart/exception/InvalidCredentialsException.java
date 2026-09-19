package com.designart.exception;

/**
 * Lançada quando o login falha — usuário inexistente OU senha incorreta.
 * Propositalmente não distingue os dois casos: a mensagem exposta ao cliente
 * é sempre genérica, para não revelar se um nome de usuário existe.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
