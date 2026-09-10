package com.hh135.icc;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicate(DuplicateKeyException exception) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Contract number already exists or contract has already been sent"); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalid(Exception exception) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request: check required fields, values and formats"); }
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException exception) { return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason()); }
}
