package com.example.myquotes;

public class QuoteCodecException extends Exception {
    public QuoteCodecException(String message) {
        super(message);
    }

    public QuoteCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
