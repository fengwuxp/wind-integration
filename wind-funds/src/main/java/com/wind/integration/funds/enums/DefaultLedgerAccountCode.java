package com.wind.integration.funds.enums;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账本会计科目编码枚举
 *
 * @author wuxp
 * @date 2026-04-13 17:54
 **/
@Getter
@AllArgsConstructor
public enum DefaultLedgerAccountCode implements DescriptiveEnum {

    // =========================
    // 资产类 Asset
    // =========================

    CASH("现金/银行存款", LedgerType.ASSET),

    AVAILABLE("可用余额（钱包可用资金）", LedgerType.ASSET),

    AUTHORIZATION("授权冻结金额（预授权）", LedgerType.ASSET),

    RECEIVABLE("应收款", LedgerType.ASSET),

    CLEARING_RECEIVABLE("清算中应收（T+n在途资金）", LedgerType.ASSET),

    SETTLEMENT_PENDING("待结算资金", LedgerType.ASSET),

    REFUND_RECEIVABLE("退款应收", LedgerType.ASSET),

    // =========================
    // 负债类 Liability
    // =========================

    PAYABLE_USER("应付用户（用户资金负债）", LedgerType.LIABILITY),

    PAYABLE_MERCHANT("应付商户", LedgerType.LIABILITY),

    CUSTOMER_BALANCE("客户资金总账负债", LedgerType.LIABILITY),

    MERCHANT_BALANCE("商户资金总账负债", LedgerType.LIABILITY),

    SETTLEMENT_PAYABLE("清算应付（待打款）", LedgerType.LIABILITY),

    HELD_FUNDS("冻结资金负债（平台代管）", LedgerType.LIABILITY),

    SECURITY_DEPOSIT("保证金/押金", LedgerType.LIABILITY),

    // =========================
    // 收入类 Revenue
    // =========================

    FEE_INCOME("手续费收入", LedgerType.REVENUE),

    FX_GAIN("汇兑收益", LedgerType.REVENUE),

    SERVICE_FEE("服务费收入", LedgerType.REVENUE),

    SURCHARGE_FEE("附加费", LedgerType.REVENUE),

    PENALTY_INCOME("违约/罚金收入", LedgerType.REVENUE),

    // =========================
    // 成本类 Expense
    // =========================

    PAYMENT_FEE("支付通道成本", LedgerType.EXPENSE),

    FX_LOSS("汇兑损失", LedgerType.EXPENSE),

    REFUND_COST("退款成本", LedgerType.EXPENSE),

    SUBSIDY("补贴成本", LedgerType.EXPENSE),

    OPERATION_COST("运营成本", LedgerType.EXPENSE),

    // =========================
    // 清算/过渡类 Clearing
    // =========================

    CLEARING("清算过渡账户", LedgerType.CLEARING),

    INTERIM_SETTLEMENT("临时结算账户", LedgerType.CLEARING),

    IN_TRANSIT("在途资金", LedgerType.CLEARING),

    NETTING("净额清算账户", LedgerType.CLEARING),

    SUSPENSE("悬账/待分配账户", LedgerType.CLEARING),

    // =========================
    // 风险/冻结类 Risk（仍归入 Liability）
    // =========================

    AUTH_HOLD("风控冻结", LedgerType.LIABILITY),

    RISK_RESERVE("风险准备金", LedgerType.LIABILITY),

    CHARGEBACK_RESERVE("拒付准备金", LedgerType.LIABILITY),

    FRAUD_HOLD("反欺诈冻结", LedgerType.LIABILITY);

    private final String desc;

    private final LedgerType ledgerType;
}