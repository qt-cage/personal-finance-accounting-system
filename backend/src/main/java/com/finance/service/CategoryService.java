package com.finance.service;

import com.finance.entity.Category;
import java.util.List;

public interface CategoryService {

    List<Category> listByUserId(Long userId, Integer type);

    void addCategory(Long userId, Category category);

    void updateCategory(Long userId, Category category);

    void updateCategory(Long userId, Category category, boolean parentSpecified);

    void deleteCategory(Long id, Long userId);

    void reorder(Long userId, Long id, Long targetId);
}
