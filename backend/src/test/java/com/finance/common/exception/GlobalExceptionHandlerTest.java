package com.finance.common.exception;

import com.finance.common.i18n.I18nUtil;
import com.finance.common.result.Result;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private I18nUtil i18nUtil;

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void hidesWrappedDatabaseDetailsFromClient() {
        when(i18nUtil.getMessage("system.query.error")).thenReturn("系统查询异常，请联系管理员");

        Result<Void> result = handler.handlePersistenceException(
                new PersistenceException("Access denied for user root; SELECT * FROM user"));

        assertEquals(500, result.getCode());
        assertEquals("系统查询异常，请联系管理员", result.getMessage());
        assertFalse(result.getMessage().contains("Access denied"));
        assertFalse(result.getMessage().contains("SELECT"));
    }
}
