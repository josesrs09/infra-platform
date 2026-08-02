package com.daertech.platform.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    record ApiError(OffsetDateTime timestamp,int status,String error,String message,String path,Map<String,String> fields){}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex,HttpServletRequest request){
        Map<String,String> fields=new LinkedHashMap<>(); ex.getBindingResult().getFieldErrors().forEach(e->fields.putIfAbsent(e.getField(),e.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST,"Datos inválidos",request,fields);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex,HttpServletRequest request){return response(HttpStatus.CONFLICT,"El registro viola una restricción o ya existe",request,Map.of());}
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException ex,HttpServletRequest request){return response(HttpStatus.valueOf(ex.getStatusCode().value()),ex.getReason(),request,Map.of());}
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> generic(Exception ex,HttpServletRequest request){
        log.error("Unhandled API exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR,"Error interno no controlado",request,Map.of());
    }
    private ResponseEntity<ApiError> response(HttpStatus status,String message,HttpServletRequest request,Map<String,String> fields){return ResponseEntity.status(status).body(new ApiError(OffsetDateTime.now(),status.value(),status.getReasonPhrase(),message,request.getRequestURI(),fields));}
}
