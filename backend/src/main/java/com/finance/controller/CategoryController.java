package com.finance.controller;

import com.finance.common.i18n.I18nUtil;
import com.finance.common.result.Result;
import com.finance.entity.Category;
import com.finance.entity.dto.CategoryCreateDTO;
import com.finance.entity.dto.CategoryUpdateDTO;
import com.finance.entity.dto.CategoryReorderDTO;
import com.finance.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private I18nUtil i18nUtil;

    @GetMapping("/list")
    public Result<List<Category>> list(HttpServletRequest request,
                                       @RequestParam(required = false) Integer type) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(categoryService.listByUserId(userId, type));
    }

    @PostMapping
    public Result<Void> add(HttpServletRequest request, @Validated @RequestBody CategoryCreateDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        Category category = new Category();
        category.setName(dto.getName());
        category.setType(dto.getType());
        category.setIcon(dto.getIcon());
        category.setSortOrder(dto.getSortOrder());
        category.setParentId(dto.getParentId());
        categoryService.addCategory(userId, category);
        return Result.success(i18nUtil.getMessage("category.add.success"));
    }

    @PutMapping
    public Result<Void> update(HttpServletRequest request, @Validated @RequestBody CategoryUpdateDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        Category category = new Category();
        category.setId(dto.getId());
        category.setName(dto.getName());
        category.setType(dto.getType());
        category.setIcon(dto.getIcon());
        category.setSortOrder(dto.getSortOrder());
        category.setParentId(dto.getParentId());
        categoryService.updateCategory(userId, category, dto.isParentSpecified());
        return Result.success(i18nUtil.getMessage("category.update.success"));
    }

    @PostMapping("/reorder")
    public Result<Void> reorder(HttpServletRequest request, @Validated @RequestBody CategoryReorderDTO dto) {
        categoryService.reorder((Long) request.getAttribute("userId"), dto.getId(), dto.getTargetId());
        return Result.success(i18nUtil.getMessage("category.update.success"));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        categoryService.deleteCategory(id, userId);
        return Result.success(i18nUtil.getMessage("category.delete.success"));
    }
}
