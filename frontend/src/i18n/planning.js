export const planningLocales = {
  zh: {
    recurringTitle:'周期账单', recurringSubtitle:'按日、周或月自动记录固定收支。', addPlan:'新增计划', editPlan:'编辑计划',
    planName:'计划名称', frequency:'频率', daily:'每日', weekly:'每周', monthly:'每月', startDate:'开始日期', endDate:'结束日期',
    nextDate:'下次记账', status:'状态', running:'正常', paused:'已暂停', finished:'已结束', failed:'执行失败',
    pause:'暂停', resume:'恢复', runDue:'处理到期账单', runResult:'已生成 {generated} 笔，{failed} 个计划暂未成功。',
    automaticOn:'自动记账已开启', automaticOff:'自动记账已暂停，可手动处理到期账单。',
    planHint:'暂停期间不生成；恢复后从下一次日期开始。修改金额只影响后续生成的账单。',
    deleteConfirm:'删除此计划？已经生成的账单会保留。', noPlans:'暂无周期计划', noPlansHint:'添加房租、会员费或工资等固定收支。',
    scheduleLocked:'计划已生成账单，开始日期和频率保持不变。', nameRequired:'请输入计划名称',
    assetsTitle:'资产看板', assetsSubtitle:'汇总账户余额、债务和近期现金流。', totalAssets:'总资产', totalLiabilities:'总负债', netAssets:'净资产',
    cashflow:'近30天净现金流', balanceChart:'账户余额', flowChart:'近30天收支趋势',
    assetHint:'净资产仅统计已配置账户的余额。近30天现金流按收入减支出计算，转账不计入。',
    unassigned:'有 {count} 笔收支尚未归属资金账户，净资产暂不包含这些记录。', dayStats:'按日统计',
    accountReferenced:'账户已被账单或周期计划使用，仅可修改名称。'
  },
  en: {
    recurringTitle:'Recurring bills', recurringSubtitle:'Record fixed income or expenses daily, weekly or monthly.', addPlan:'Add plan', editPlan:'Edit plan',
    planName:'Plan name', frequency:'Frequency', daily:'Daily', weekly:'Weekly', monthly:'Monthly', startDate:'Start date', endDate:'End date',
    nextDate:'Next date', status:'Status', running:'Active', paused:'Paused', finished:'Completed', failed:'Failed',
    pause:'Pause', resume:'Resume', runDue:'Process due bills', runResult:'Created {generated} bills; {failed} plans need attention.',
    automaticOn:'Automatic recording is enabled', automaticOff:'Automatic recording is paused. Due bills can be processed manually.',
    planHint:'Paused dates are skipped on resume. Amount changes affect future generated bills only.',
    deleteConfirm:'Delete this plan? Generated bills will be retained.', noPlans:'No recurring plans', noPlansHint:'Add rent, subscriptions or salary.',
    scheduleLocked:'This plan has generated bills. Its start date and frequency cannot change.', nameRequired:'Enter a plan name',
    assetsTitle:'Assets', assetsSubtitle:'Review account balances, debt and recent cash flow.', totalAssets:'Assets', totalLiabilities:'Liabilities', netAssets:'Net assets',
    cashflow:'30-day net cash flow', balanceChart:'Account balances', flowChart:'Income and expenses over 30 days',
    assetHint:'Net assets cover configured accounts only. Cash flow equals income minus expenses and excludes transfers.',
    unassigned:'{count} income or expense records are unassigned and excluded from net assets.', dayStats:'Daily statistics',
    accountReferenced:'This account is used by bills or recurring plans. Only its name can change.'
  },
  ja: {
    recurringTitle:'定期明細', recurringSubtitle:'固定収支を日・週・月ごとに登録します。', addPlan:'予定を追加', editPlan:'予定を編集',
    planName:'予定名', frequency:'頻度', daily:'毎日', weekly:'毎週', monthly:'毎月', startDate:'開始日', endDate:'終了日',
    nextDate:'次回登録日', status:'状態', running:'有効', paused:'一時停止', finished:'終了', failed:'処理失敗',
    pause:'停止', resume:'再開', runDue:'期日分を処理', runResult:'{generated}件を登録しました。{failed}件の予定を確認してください。',
    automaticOn:'自動登録は有効です', automaticOff:'自動登録は停止中です。期日分は手動で処理できます。',
    planHint:'停止期間は再開時にスキップします。金額変更は以降の登録分に適用されます。',
    deleteConfirm:'予定を削除しますか？登録済み明細は残ります。', noPlans:'定期予定がありません', noPlansHint:'家賃・会費・給与などを設定してください。',
    scheduleLocked:'登録済み明細があるため、開始日と頻度は変更できません。', nameRequired:'予定名を入力してください',
    assetsTitle:'資産一覧', assetsSubtitle:'口座残高、負債と最近の収支を確認します。', totalAssets:'資産', totalLiabilities:'負債', netAssets:'純資産',
    cashflow:'30日間の純収支', balanceChart:'口座残高', flowChart:'30日間の収支推移',
    assetHint:'純資産は設定済み口座のみです。純収支は収入から支出を引き、振替は除外します。',
    unassigned:'未割り当ての収支が{count}件あり、純資産には含まれません。', dayStats:'日別統計',
    accountReferenced:'明細または定期予定に使用中のため、口座名のみ変更できます。'
  }
}
