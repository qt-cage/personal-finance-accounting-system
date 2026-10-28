package com.finance.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.common.exception.BusinessException;
import com.finance.entity.*;
import com.finance.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Service
public class NotificationService {
    private final NotificationMapper notifications;private final BudgetMapper budgets;private final BillMapper bills;private final CategoryMapper categories;
    public NotificationService(NotificationMapper notifications,BudgetMapper budgets,BillMapper bills,CategoryMapper categories){this.notifications=notifications;this.budgets=budgets;this.bills=bills;this.categories=categories;}
    @Transactional
    public void check(Long owner,LocalDate date){
        if(date==null||!notifications.enabled(owner))return;
        String month=YearMonth.from(date).toString(),start=month+"-01",end=YearMonth.from(date).atEndOfMonth().toString();
        for(Budget budget:budgets.activeForUpdate(owner,month)){
            BigDecimal spent=budget.getCategoryId()==null?bills.sumByTypeAndDateRange(owner,1,start,end):bills.sumByCategoryAndDateRange(owner,budget.getCategoryId(),1,start,end);
            if(spent.compareTo(budget.getAmount().multiply(new BigDecimal("0.80")))<0)continue;
            Category category=budget.getCategoryId()==null?null:categories.selectById(budget.getCategoryId());String name=category==null?"总预算":category.getName();
            Notification item=new Notification();item.setUserId(owner);item.setBudgetId(budget.getId());item.setMonth(month);
            item.setStage(80);item.setContent(month+" "+name+"已使用80%以上，支出 ¥"+spent.toPlainString()+" / 预算 ¥"+budget.getAmount().toPlainString());notifications.addOnce(item);
            if(spent.compareTo(budget.getAmount())>=0){item.setStage(100);item.setContent(month+" "+name+(spent.compareTo(budget.getAmount())>0?"已超支":"已用尽")+"，支出 ¥"+spent.toPlainString()+" / 预算 ¥"+budget.getAmount().toPlainString());notifications.addOnce(item);}
        }
    }
    public Map<String,Object> list(Long owner){Map<String,Object> result=new LinkedHashMap<>();result.put("enabled",notifications.enabled(owner));result.put("unread",notifications.selectCount(new LambdaQueryWrapper<Notification>().eq(Notification::getUserId,owner).eq(Notification::getReadFlag,false)));result.put("items",notifications.selectList(new LambdaQueryWrapper<Notification>().eq(Notification::getUserId,owner).orderByDesc(Notification::getId).last("LIMIT 100")));return result;}
    public void preference(Long owner,Boolean enabled){if(enabled==null)throw new BusinessException(400,"请选择提醒设置");notifications.preference(owner,enabled);}
    public void readAll(Long owner){notifications.readAll(owner);}
}
