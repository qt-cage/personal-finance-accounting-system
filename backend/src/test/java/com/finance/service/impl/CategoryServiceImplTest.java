package com.finance.service.impl;
import com.finance.service.DataRevisionService;

import com.finance.common.exception.BusinessException;
import com.finance.common.i18n.I18nUtil;
import com.finance.entity.Category;
import com.finance.mapper.BillMapper;
import com.finance.mapper.BudgetMapper;
import com.finance.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private BillMapper billMapper;

    @Mock
    private I18nUtil i18nUtil;

    @Mock
    private BudgetMapper budgetMapper;

    @Mock private DataRevisionService revision;
    @InjectMocks
    private CategoryServiceImpl service;

    @Test
    void updateRejectsSystemDefaultCategory() {
        when(categoryMapper.selectActiveForUpdate(3L)).thenReturn(category(3L, 0L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateCategory(1L, category(3L, null)));

        assertEquals(403, ex.getCode());
        verify(categoryMapper, never()).updateById(any(Category.class));
    }

    @Test
    void deleteRejectsCategoryOwnedByAnotherUser() {
        when(categoryMapper.selectActiveForUpdate(3L)).thenReturn(category(3L, 2L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteCategory(3L, 1L));

        assertEquals(403, ex.getCode());
        verify(categoryMapper, never()).deleteById(3L);
    }

    @Test
    void deleteRejectsCategoryReferencedByBill() {
        when(categoryMapper.selectActiveForUpdate(3L)).thenReturn(category(3L, 1L));
        when(billMapper.selectCount(any())).thenReturn(1L);
        when(i18nUtil.getMessage("category.delete.in.use"))
                .thenReturn("该分类已被账单使用，无法删除");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteCategory(3L, 1L));

        assertEquals(409, ex.getCode());
        assertEquals("该分类已被账单使用，无法删除", ex.getMessage());
        verify(categoryMapper, never()).deleteById(3L);
    }

    @Test
    void deleteAllowsUnusedOwnedCategory() {
        when(categoryMapper.selectActiveForUpdate(3L)).thenReturn(category(3L, 1L));
        when(billMapper.selectCount(any())).thenReturn(0L);

        service.deleteCategory(3L, 1L);

        verify(categoryMapper).deleteById(3L);
    }

    @Test
    void updatePreservesOwnerAndUpdatesAllowedFields() {
        Category existing = category(3L, 1L);
        existing.setName("旧名称");
        when(categoryMapper.selectActiveForUpdate(3L)).thenReturn(existing);
        Category input = category(3L, 99L);
        input.setName("新名称");
        input.setType(1);
        input.setIcon("food");
        input.setSortOrder(6);

        service.updateCategory(1L, input);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals("新名称", captor.getValue().getName());
    }

    private static Category category(Long id, Long userId) {
        Category category = new Category();
        category.setId(id);
        category.setUserId(userId);
        category.setType(1);
        return category;
    }

    @Test
    void deleteRejectsCategoryWithBudgetButNoBills() {
        when(categoryMapper.selectActiveForUpdate(3L)).thenReturn(category(3L, 1L));
        when(billMapper.selectCount(any())).thenReturn(0L);
        when(budgetMapper.selectCount(any())).thenReturn(1L);
        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteCategory(3L, 1L));
        assertEquals(409, error.getCode());
        verify(categoryMapper, never()).deleteById(3L);
    }

    @Test
    void updateRejectsChangingTypeThroughApi() {
        when(categoryMapper.selectActiveForUpdate(3L)).thenReturn(category(3L, 1L));
        Category update = category(3L, 1L);
        update.setType(2);
        BusinessException error = assertThrows(BusinessException.class, () -> service.updateCategory(1L, update));
        assertEquals(400, error.getCode());
        verify(categoryMapper, never()).updateById(any(Category.class));
    }
}
