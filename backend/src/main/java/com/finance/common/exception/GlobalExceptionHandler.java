package com.finance.common.exception;

import com.finance.common.i18n.I18nUtil;
import com.finance.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired
    private I18nUtil i18nUtil;

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse(i18nUtil.getMessage("validation.failed"));
        return Result.error(400, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, DateTimeParseException.class})
    public Result<Void> handleMalformedRequest(Exception error) {
        return Result.error(400, i18nUtil.getMessage("validation.failed"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeException(MaxUploadSizeExceededException e) {
        return Result.error(400, i18nUtil.getMessage("user.avatar.size"));
    }

    /**
     * MyBatis 与 Spring 会把底层 JDBC 异常包装为运行时异常。
     * 在进入通用 RuntimeException 处理器前拦截，避免把 SQL、账号或堆栈返回给前端。
     */
    @ExceptionHandler({PersistenceException.class, DataAccessException.class})
    public Result<Void> handlePersistenceException(RuntimeException e) {
        logDatabaseFailure(e);
        return Result.error(i18nUtil.getMessage("system.query.error"));
    }

    /**
     * 捕获SQL日期/语法异常，统一返回友好提示，不暴露原始SQL和堆栈
     */
    @ExceptionHandler({SQLException.class, SQLSyntaxErrorException.class,
            BadSqlGrammarException.class, DataIntegrityViolationException.class})
    public Result<Void> handleSqlException(Exception e) {
        logDatabaseFailure(e);
        String msg = e.getMessage();
        // 日期相关异常统一提示
        if (msg != null && (msg.contains("date") || msg.contains("Date") || msg.contains("time") || msg.contains("Time"))) {
            return Result.error(i18nUtil.getMessage("system.date.error"));
        }
        return Result.error(i18nUtil.getMessage("system.query.error"));
    }

    private void logDatabaseFailure(Exception error) {
        String reference = UUID.randomUUID().toString();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null && attributes.getResponse() != null) {
            attributes.getResponse().setHeader("X-Error-Id", reference);
        }
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 20; depth++, cause = cause.getCause()) {
            if (cause instanceof SQLException) {
                SQLException sql = (SQLException) cause;
                // 不记录SQL参数、凭据或驱动消息；标识可与响应头对应。
                log.error("Database failure reference={} type={} sqlState={} vendorCode={}",
                        reference, error.getClass().getSimpleName(), sql.getSQLState(), sql.getErrorCode());
                return;
            }
        }
        log.error("Database failure reference={} type={}", reference, error.getClass().getSimpleName());
    }
}
