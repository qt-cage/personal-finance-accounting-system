package com.finance.service.impl;
import com.finance.service.DataRevisionService;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.finance.common.exception.BusinessException;
import com.finance.entity.Bill;
import com.finance.entity.Category;
import com.finance.mapper.BillMapper;
import com.finance.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import com.finance.service.NotificationService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillServiceImplTest {

    @Mock
    private BillMapper billMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private NotificationService notificationService;

    @Mock private DataRevisionService revision;
    @InjectMocks
    private BillServiceImpl service;

    @Test
    void updateBillRejectsCrossUserRecord() {
        Bill existing = bill(9L, 2L, 10L, 1);
        when(billMapper.selectById(9L)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateBill(1L, bill(9L, null, 10L, 1)));

        assertEquals(403, ex.getCode());
        verify(billMapper, never()).updateById(any(Bill.class));
    }

    @Test
    void updateBillPreservesOwnerAndCopiesOnlyBusinessFields() {
        Bill existing = bill(9L, 1L, 10L, 1);
        Category category = category(10L, 0L, 1);
        when(billMapper.selectById(9L)).thenReturn(existing);
        when(categoryMapper.selectActiveForUpdate(10L)).thenReturn(category);

        Bill input = bill(9L, 99L, 10L, 1);
        input.setAmount(new BigDecimal("88.50"));
        input.setRemark("晚餐");
        input.setBillDate(LocalDate.of(2026, 8, 19));
        service.updateBill(1L, input);

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals(new BigDecimal("88.50"), captor.getValue().getAmount());
        assertEquals("晚餐", captor.getValue().getRemark());
    }

    @Test
    void addBillRejectsCategoryOwnedByAnotherUser() {
        when(categoryMapper.selectActiveForUpdate(10L)).thenReturn(category(10L, 2L, 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.addBill(1L, bill(null, null, 10L, 1)));

        assertEquals(403, ex.getCode());
        verify(billMapper, never()).insert(any(Bill.class));
    }

    @Test
    void addBillRejectsMismatchedCategoryType() {
        when(categoryMapper.selectActiveForUpdate(10L)).thenReturn(category(10L, 1L, 2));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.addBill(1L, bill(null, null, 10L, 1)));

        assertEquals(400, ex.getCode());
    }

    @Test
    void batchDeleteRejectsAnyUnownedId() {
        when(billMapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.singletonList(bill(1L, 1L, 10L, 1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteBatch(1L, Arrays.asList(1L, 2L)));

        assertEquals(403, ex.getCode());
        verify(billMapper, never()).delete(any(Wrapper.class));
    }

    private static Bill bill(Long id, Long userId, Long categoryId, int type) {
        Bill bill = new Bill();
        bill.setId(id);
        bill.setUserId(userId);
        bill.setCategoryId(categoryId);
        bill.setType(type);
        bill.setAmount(BigDecimal.ONE);
        bill.setBillDate(LocalDate.of(2026, 8, 19));
        return bill;
    }

    private static Category category(Long id, Long userId, int type) {
        Category category = new Category();
        category.setId(id);
        category.setUserId(userId);
        category.setType(type);
        return category;
    }
}
