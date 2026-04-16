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

    CASH("现金/银行存款", LedgerAccountType.ASSET),

    AVAILABLE("可用余额（钱包可用资金）", LedgerAccountType.LIABILITY),

    AUTHORIZATION("授权冻结金额（预授权）", LedgerAccountType.LIABILITY),

    RECEIVABLE("应收款", LedgerAccountType.ASSET),

    CLEARING_RECEIVABLE("清算中应收（T+n在途资金）", LedgerAccountType.ASSET),

    SETTLEMENT_PENDING("待结算资金", LedgerAccountType.ASSET),

    REFUND_RECEIVABLE("退款应收", LedgerAccountType.ASSET),

    // =========================
    // 负债类 Liability
    // =========================

    PAYABLE_USER("应付用户（用户资金负债）", LedgerAccountType.LIABILITY),

    PAYABLE_MERCHANT("应付商户", LedgerAccountType.LIABILITY),

    CUSTOMER_BALANCE("客户资金总账负债", LedgerAccountType.LIABILITY),

    MERCHANT_BALANCE("商户资金总账负债", LedgerAccountType.LIABILITY),

    SETTLEMENT_PAYABLE("清算应付（待打款）", LedgerAccountType.LIABILITY),

    HELD_FUNDS("冻结资金负债（平台代管）", LedgerAccountType.LIABILITY),

    SECURITY_DEPOSIT("保证金/押金", LedgerAccountType.LIABILITY),

    // =========================
    // 收入类 Revenue
    // =========================

    FEE_INCOME("手续费收入", LedgerAccountType.REVENUE),

    FX_GAIN("汇兑收益", LedgerAccountType.REVENUE),

    SERVICE_FEE("服务费收入", LedgerAccountType.REVENUE),

    SURCHARGE_FEE("附加费", LedgerAccountType.REVENUE),

    PENALTY_INCOME("违约/罚金收入", LedgerAccountType.REVENUE),

    // =========================
    // 成本类 Expense
    // =========================

    PAYMENT_FEE("支付通道成本", LedgerAccountType.EXPENSE),

    FX_LOSS("汇兑损失", LedgerAccountType.EXPENSE),

    REFUND_COST("退款成本", LedgerAccountType.EXPENSE),

    SUBSIDY("补贴成本", LedgerAccountType.EXPENSE),

    OPERATION_COST("运营成本", LedgerAccountType.EXPENSE),

    // =========================
    // 清算/过渡类 Clearing
    // =========================

    CLEARING("清算过渡账户", LedgerAccountType.CLEARING),

    INTERIM_SETTLEMENT("临时结算账户", LedgerAccountType.CLEARING),

    IN_TRANSIT("在途资金", LedgerAccountType.CLEARING),

    NETTING("净额清算账户", LedgerAccountType.CLEARING),

    SUSPENSE("悬账/待分配账户", LedgerAccountType.CLEARING),

    // =========================
    // 风险/冻结类 Risk（仍归入 Liability）
    // =========================

    AUTH_HOLD("风控冻结", LedgerAccountType.LIABILITY),

    RISK_RESERVE("风险准备金", LedgerAccountType.LIABILITY),

    CHARGEBACK_RESERVE("拒付准备金", LedgerAccountType.LIABILITY),

    FRAUD_HOLD("反欺诈冻结", LedgerAccountType.LIABILITY);

    private final String desc;

    private final LedgerAccountType accountType;
}