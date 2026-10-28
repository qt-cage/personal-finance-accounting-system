package com.finance.controller;
import com.finance.common.result.Result;
import com.finance.entity.Category;
import com.finance.entity.dto.*;
import com.finance.service.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.*;
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;private final CategoryService categories;private final DictionaryService dictionaries;private final SystemSettingsService settings;
    public AdminController(AdminService admin,CategoryService categories,DictionaryService dictionaries,SystemSettingsService settings){this.admin=admin;this.categories=categories;this.dictionaries=dictionaries;this.settings=settings;}
    @GetMapping("/users") public Result<Map<String,Object>> users(@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(admin.users(keyword,page,size));}
    @PutMapping("/users/{id}") public Result<Void> user(HttpServletRequest r,@PathVariable Long id,@Valid @RequestBody AdminUserDTO dto){admin.updateUser((Long)r.getAttribute("userId"),id,dto.getRole(),dto.getStatus());return Result.success("success");}
    @GetMapping("/logs") public Result<Map<String,Object>> logs(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(admin.logs(page,size));}
    @GetMapping("/default-categories") public Result<List<Category>> categories(){return Result.success(categories.listByUserId(0L,null));}
    @PostMapping("/default-categories") public Result<Void> category(@Valid @RequestBody CategoryCreateDTO dto){categories.addCategory(0L,toCategory(dto));return Result.success("success");}
    @PutMapping("/default-categories") public Result<Void> category(@Valid @RequestBody CategoryUpdateDTO dto){Category c=new Category();c.setId(dto.getId());c.setName(dto.getName());c.setType(dto.getType());c.setIcon(dto.getIcon());c.setSortOrder(dto.getSortOrder());c.setParentId(dto.getParentId());categories.updateCategory(0L,c,dto.isParentSpecified());return Result.success("success");}
    @DeleteMapping("/default-categories/{id}") public Result<Void> deleteCategory(@PathVariable Long id){categories.deleteCategory(id,0L);return Result.success("success");}
    @GetMapping("/dictionaries") public Result<List<Map<String,Object>>> dictionaries(){return Result.success(dictionaries.list());}
    @PutMapping("/dictionaries/{id}") public Result<Void> dictionary(@PathVariable Long id,@Valid @RequestBody DictionaryDTO dto){dictionaries.update(id,dto.getName(),dto.getEnabled());return Result.success("success");}
    @GetMapping("/settings") public Result<Map<String,Object>> settings(){return Result.success(settings.read());}
    @PutMapping("/settings") public Result<Void> settings(@Valid @RequestBody SystemSettingDTO dto){settings.save(dto.getSiteName(),dto.getRegistrationEnabled());return Result.success("success");}
    private Category toCategory(CategoryCreateDTO dto){Category c=new Category();c.setName(dto.getName());c.setType(dto.getType());c.setIcon(dto.getIcon());c.setSortOrder(dto.getSortOrder());c.setParentId(dto.getParentId());return c;}
}
