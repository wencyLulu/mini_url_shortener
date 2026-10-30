package com.example.urlshortener.service;

/** Strategy for producing new short codes. */
public interface CodeGenerator {

    String generate();
}
