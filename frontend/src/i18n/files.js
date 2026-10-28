export const fileLocales = {
  zh: {
    export: '导出 Excel', exported: 'Excel 已生成', download: '下载 Excel', expires: '链接在10分钟后失效，仅本人登录后可下载',
    import: '导入账单', template: '下载 CSV 模板', choose: '选择 CSV 文件', hint: '使用 UTF-8 通用模板。支出填1、收入填2；每次最多500笔。请填写下方对应的分类与账户编号。',
    accounts: '账户编号', categories: '分类编号', preview: '校验结果', row: '行号', error: '校验问题', valid: '通过', importSummary: '共 {count} 笔，{errors} 笔需修正', confirm: '确认导入 {count} 笔', imported: '已导入 {count} 笔账单',
    receipts: '小票', receiptHint: '每笔最多5张，支持2MB以内的 JPG / PNG 图片', upload: '上传小票', remove: '删除小票', removeConfirm: '确认删除这张小票？', noReceipts: '尚未上传小票',
    messages: '消息提醒', messageHint: '预算达到80%或用尽时，在这里查看提醒', unread: '{count} 条未读', readAll: '全部已读', noMessages: '暂时没有提醒', enabled: '预算站内提醒', preferenceHint: '开启后接收预算接近上限与超支提醒', saved: '提醒设置已保存', exportAll: '导出全部账单', previewFailed: '小票加载失败，请重试'
  },
  en: {
    export: 'Export Excel', exported: 'Excel ready', download: 'Download Excel', expires: 'Expires in 10 minutes; requires your signed-in account',
    import: 'Import bills', template: 'CSV template', choose: 'Choose CSV', hint: 'Use the UTF-8 template. Expense: 1; income: 2. Up to 500 rows. Use the category and account numbers below.',
    accounts: 'Account numbers', categories: 'Category numbers', preview: 'Validation', row: 'Row', error: 'Issue', valid: 'Valid', importSummary: '{count} bills; {errors} need correction', confirm: 'Import {count} bills', imported: '{count} bills imported',
    receipts: 'Receipts', receiptHint: 'Up to 5 JPG / PNG images per bill, 2MB each', upload: 'Upload receipt', remove: 'Delete receipt', removeConfirm: 'Delete this receipt?', noReceipts: 'No receipts yet',
    messages: 'Notifications', messageHint: 'View budget alerts at 80% and when your budget is used up', unread: '{count} unread', readAll: 'Mark all read', noMessages: 'No notifications yet', enabled: 'Budget notifications', preferenceHint: 'Receive alerts when budgets approach their limit or are exceeded', saved: 'Notification preference saved', exportAll: 'Export all bills', previewFailed: 'Could not load receipts. Retry.'
  },
  ja: {
    export: 'Excel出力', exported: 'Excelを作成しました', download: 'Excelをダウンロード', expires: '10分間有効です。本人のログインが必要です',
    import: '明細取込', template: 'CSVテンプレート', choose: 'CSVを選択', hint: 'UTF-8テンプレートを使用してください。支出は1、収入は2、最大500件です。下記の分類番号と口座番号を指定してください。',
    accounts: '口座番号', categories: '分類番号', preview: '検証結果', row: '行', error: '問題', valid: '有効', importSummary: '{count}件中、{errors}件の修正が必要です', confirm: '{count}件を取り込む', imported: '{count}件を取り込みました',
    receipts: 'レシート', receiptHint: '明細ごとにJPG / PNGを5枚まで、各2MB以内', upload: 'レシートを追加', remove: 'レシートを削除', removeConfirm: 'このレシートを削除しますか？', noReceipts: 'レシートはありません',
    messages: 'お知らせ', messageHint: '予算の80%到達と使い切り時の通知を確認できます', unread: '未読{count}件', readAll: 'すべて既読', noMessages: '通知はありません', enabled: '予算通知', preferenceHint: '予算の上限接近と超過時に通知します', saved: '通知設定を保存しました', exportAll: 'すべての明細を出力', previewFailed: 'レシートを読み込めませんでした。再試行してください。'
  }
}
