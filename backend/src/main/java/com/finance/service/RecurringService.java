package com.finance.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.util.RecurrenceDates;
import com.finance.entity.*;
import com.finance.entity.dto.RecurringDTO;
import com.finance.mapper.CategoryMapper;
import com.finance.mapper.RecurringMapper;
import com.finance.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.finance.service.DataRevisionService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class RecurringService {
    @org.springframework.beans.factory.annotation.Autowired private DataRevisionService revision;

    private static final Logger LOG = LoggerFactory.getLogger(RecurringService.class);
    private final RecurringMapper mapper;
    private final CategoryMapper categories;
    private final AccountService accounts;
    private final BillService bills;
    private final TransactionTemplate transactions;
    private final UserMapper users;
    @Value("${app.recurring.enabled:false}") private boolean automaticEnabled;

    public RecurringService(RecurringMapper mapper, CategoryMapper categories, AccountService accounts,
                            BillService bills, PlatformTransactionManager manager, UserMapper users) {
        this.mapper = mapper; this.categories = categories; this.accounts = accounts; this.bills = bills;
        this.transactions = new TransactionTemplate(manager);
        this.users = users;
    }

    public Map<String, Object> list(Long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("items", mapper.listForUser(userId));
        result.put("automaticEnabled", automaticEnabled);
        return result;
    }

    public static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }

    @Transactional
    public void create(Long userId, RecurringDTO dto) {
        revision.bump(userId);
        if (dto.getStartDate().isBefore(today())) throw new BusinessException(400, "开始日期不能早于今天");
        RecurringBill plan = new RecurringBill();
        apply(plan, dto);
        plan.setUserId(userId); plan.setNextDate(dto.getStartDate()); plan.setEnabled(true);
        validate(plan);
        mapper.insert(plan);
    }

    @Transactional
    public void update(Long userId, Long id, RecurringDTO dto) {
        revision.bump(userId);
        RecurringBill plan = owned(userId, id);
        boolean generated = mapper.runCount(id) > 0;
        if (generated && (!plan.getFrequency().equals(dto.getFrequency()) || !plan.getStartDate().equals(dto.getStartDate()))) {
            throw new BusinessException(409, "计划已生成账单，开始日期和频率不能修改");
        }
        boolean scheduleChanged = !plan.getFrequency().equals(dto.getFrequency()) || !plan.getStartDate().equals(dto.getStartDate());
        apply(plan, dto);
        if (scheduleChanged) plan.setNextDate(RecurrenceDates.onOrAfter(plan.getStartDate(), today(), plan.getFrequency()));
        validate(plan);
        mapper.updateById(plan);
    }

    @Transactional
    public void setEnabled(Long userId, Long id, boolean enabled) {
        revision.bump(userId);
        RecurringBill plan = owned(userId, id);
        if (enabled && !Boolean.TRUE.equals(plan.getEnabled())) {
            // 用户主动暂停与服务中断不同：恢复时跳过暂停期间，避免突然补记支出。
            plan.setNextDate(RecurrenceDates.onOrAfter(plan.getStartDate(), today(), plan.getFrequency()));
            validate(plan);
        }
        plan.setEnabled(enabled); plan.setLastError(null);
        mapper.updateById(plan);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        revision.bump(userId);
        owned(userId, id);
        mapper.deleteById(id);
    }

    public Map<String, Integer> runDue(Long userId, LocalDate date) {
        int generated = 0, failed = 0;
        for (Long id : mapper.dueIds(userId, date)) {
            try {
                // 每个计划独立提交；某计划账户失效不会拖回其他已成功计划。
                Integer count = transactions.execute(status -> process(id, userId, date));
                generated += count == null ? 0 : count;
            } catch (RuntimeException error) {
                failed++;
                mapper.recordError(id, error instanceof BusinessException ? error.getMessage() : "暂未生成，请检查账户与分类后重试");
                LOG.warn("Recurring plan {} failed with {}", id, error.getClass().getSimpleName());
            }
        }
        Map<String, Integer> result = new HashMap<>();
        result.put("generated", generated); result.put("failed", failed);
        return result;
    }

    private int process(Long id, Long userId, LocalDate until) {
        RecurringBill candidate = mapper.selectById(id);
        if(candidate==null || (userId!=null && !userId.equals(candidate.getUserId())))return 0;
        revision.bump(candidate.getUserId());
        RecurringBill plan = mapper.lockActive(id);
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled()) || (userId != null && !userId.equals(plan.getUserId()))) return 0;
        if (users.lockActiveId(plan.getUserId()) == null) return 0;
        int generated = 0, checked = 0;
        LocalDate due = plan.getNextDate();
        while (!due.isAfter(until) && (plan.getEndDate() == null || !due.isAfter(plan.getEndDate())) && checked++ < 31) {
            if (mapper.occurrenceExists(id, due) == 0) {
                Bill bill = new Bill();
                bill.setType(plan.getType()); bill.setCategoryId(plan.getCategoryId()); bill.setAccountId(plan.getAccountId());
                bill.setAmount(plan.getAmount()); bill.setBillDate(due); bill.setRemark(plan.getRemark());
                bills.addBill(plan.getUserId(), bill);
                mapper.recordRun(id, due, bill.getId());
                generated++;
            }
            due = RecurrenceDates.next(plan.getStartDate(), due, plan.getFrequency());
        }
        plan.setNextDate(due); plan.setLastError(null);
        mapper.updateById(plan);
        return generated;
    }

    private RecurringBill owned(Long userId, Long id) {
        RecurringBill plan = mapper.lockActive(id);
        if (plan == null) throw new BusinessException(404, "周期计划不存在");
        if (!userId.equals(plan.getUserId())) throw new BusinessException(403, "无权操作该周期计划");
        return plan;
    }

    private void validate(RecurringBill plan) {
        if (plan.getEndDate() != null && plan.getEndDate().isBefore(plan.getStartDate())) throw new BusinessException(400, "结束日期不能早于开始日期");
        Category category = categories.selectActiveForUpdate(plan.getCategoryId());
        if (category == null) throw new BusinessException(404, "分类不存在");
        if (!plan.getUserId().equals(category.getUserId()) && !Long.valueOf(0).equals(category.getUserId())) throw new BusinessException(403, "无权使用该分类");
        if (!plan.getType().equals(category.getType())) throw new BusinessException(400, "收支类型与分类不一致");
        accounts.lockAccessible(plan.getUserId(), plan.getAccountId(), plan.getNextDate());
    }

    private void apply(RecurringBill plan, RecurringDTO dto) {
        plan.setName(dto.getName().trim()); plan.setType(dto.getType()); plan.setCategoryId(dto.getCategoryId());
        plan.setAccountId(dto.getAccountId()); plan.setAmount(dto.getAmount()); plan.setRemark(dto.getRemark());
        plan.setFrequency(dto.getFrequency()); plan.setStartDate(dto.getStartDate()); plan.setEndDate(dto.getEndDate());
        plan.setLastError(null);
    }
}
