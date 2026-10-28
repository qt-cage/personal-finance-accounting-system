package com.finance.service.impl;
import com.finance.service.DataRevisionService;
import com.finance.service.DictionaryService;

import com.finance.common.exception.BusinessException;
import com.finance.entity.FinanceAccount;
import com.finance.entity.dto.AccountDTO;
import com.finance.mapper.FinanceAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {
    @Mock FinanceAccountMapper mapper;
    @Mock private DictionaryService dictionaryService;
    @Mock private DataRevisionService revision;
    @InjectMocks AccountServiceImpl service;

    @Test
    void accessRejectsOtherOwnersAndPreOpeningDates() {
        FinanceAccount account = account();
        when(mapper.selectActiveForUpdate(1L)).thenReturn(account);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.lockAccessible(2L, 1L, null)).getCode());
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.lockAccessible(1L, 1L, LocalDate.of(2019, 12, 31))).getCode());
    }

    @Test
    void openingBalanceCannotBeRewrittenAfterAnyMovement() {
        when(mapper.selectActiveForUpdate(1L)).thenReturn(account());
        when(mapper.countReferences(1L, 1L)).thenReturn(1L);
        AccountDTO dto = dto();
        dto.setOpeningBalance(new BigDecimal("200"));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.update(1L, 1L, dto)).getCode());
        verify(mapper, never()).updateById(any());
    }

    @Test
    void sameNumericOpeningBasisAllowsOnlyNameChange() {
        when(mapper.selectActiveForUpdate(1L)).thenReturn(account());
        AccountDTO dto = dto();
        dto.setName("Renamed");
        dto.setOpeningBalance(new BigDecimal("100.0"));
        service.update(1L, 1L, dto);
        ArgumentCaptor<FinanceAccount> capture = ArgumentCaptor.forClass(FinanceAccount.class);
        verify(mapper).updateById(capture.capture());
        assertEquals("Renamed", capture.getValue().getName());
        assertEquals(1L, capture.getValue().getUserId());
    }

    @Test
    void futureOpeningDateIsRejectedBeforeInsert() {
        AccountDTO dto = dto();
        dto.setOpeningDate(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1));
        assertEquals(400, assertThrows(BusinessException.class, () -> service.create(1L, dto)).getCode());
        verify(mapper, never()).insert(any());
    }

    private FinanceAccount account() {
        FinanceAccount account = new FinanceAccount();
        account.setId(1L); account.setUserId(1L); account.setName("Cash"); account.setAccountType("CASH");
        account.setOpeningBalance(new BigDecimal("100.00")); account.setOpeningDate(LocalDate.of(2020, 1, 1));
        return account;
    }
    private AccountDTO dto() {
        AccountDTO dto = new AccountDTO();
        dto.setName("Cash"); dto.setAccountType("CASH"); dto.setOpeningBalance(new BigDecimal("100.00"));
        dto.setOpeningDate(LocalDate.of(2020, 1, 1));
        return dto;
    }
}
