package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.FinanceAccount;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface FinanceAccountMapper extends BaseMapper<FinanceAccount> {
    @Select("SELECT * FROM finance_account WHERE id=#{id} AND deleted=0 FOR UPDATE")
    FinanceAccount selectActiveForUpdate(@Param("id") Long id);

    // Derive both transfer sides from one row so balances cannot drift independently.
    @Select("SELECT a.*, a.opening_balance + IFNULL((SELECT SUM(CASE " +
            "WHEN b.type=2 AND b.account_id=a.id THEN b.amount " +
            "WHEN b.type=1 AND b.account_id=a.id THEN -b.amount " +
            "WHEN b.type=3 AND b.account_id=a.id THEN -b.amount " +
            "WHEN b.type=3 AND b.transfer_account_id=a.id THEN b.amount ELSE 0 END) " +
            "FROM bill b WHERE b.user_id=a.user_id AND b.deleted=0 " +
            "AND (b.account_id=a.id OR b.transfer_account_id=a.id) " +
            "AND b.bill_date BETWEEN a.opening_date AND #{asOf}),0) AS balance, " +
            "EXISTS(SELECT 1 FROM bill h WHERE h.user_id=a.user_id " +
            "AND (h.account_id=a.id OR h.transfer_account_id=a.id)) AS has_transactions, " +
            "EXISTS(SELECT 1 FROM recurring_bill r WHERE r.account_id=a.id AND r.user_id=a.user_id AND r.deleted=0) AS has_plans " +
            "FROM finance_account a WHERE a.user_id=#{userId} AND a.deleted=0 ORDER BY a.id")
    List<FinanceAccount> listWithBalances(@Param("userId") Long userId, @Param("asOf") LocalDate asOf);

    @Select("SELECT COUNT(*) FROM bill WHERE user_id=#{userId} " +
            "AND (account_id=#{accountId} OR transfer_account_id=#{accountId})")
    long countReferences(@Param("userId") Long userId, @Param("accountId") Long accountId);

    @Select("SELECT COUNT(*) FROM bill WHERE user_id=#{userId} AND deleted=0 " +
            "AND (account_id=#{accountId} OR transfer_account_id=#{accountId})")
    long countActiveReferences(@Param("userId") Long userId, @Param("accountId") Long accountId);

    @Select("SELECT COUNT(*) FROM recurring_bill WHERE user_id=#{userId} AND account_id=#{accountId} AND deleted=0")
    long countPlanReferences(@Param("userId") Long userId, @Param("accountId") Long accountId);
}
