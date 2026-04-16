package com.wind.integration.funds.account;

import com.wind.transaction.core.Money;
import com.wind.transaction.core.enums.CurrencyIsoCode;
import org.jspecify.annotations.NonNull;

/**
 * 资金账户余额视图（Balance Projection View）
 *
 * <p>用于描述某个资金账户在某一时刻的余额状态，是 Ledger 账本聚合后的结果视图。</p>
 *
 * <h3>核心职责</h3>
 * <ul>
 *     <li>仅表达账户“当前资金状态”，不负责计算逻辑</li>
 *     <li>所有金额均来源于 LedgerEntry 的聚合结果（Projection）</li>
 *     <li>不参与资金流转决策（如冻结、退款、提现逻辑）</li>
 *     <li>支持多币种账户的余额表达（按 currency 维度隔离）</li>
 * </ul>
 *
 * <h3>资金模型说明</h3>
 * <ul>
 *     <li>Available：可用于消费或支出的资金</li>
 *     <li>Frozen：已占用但暂不可用的资金（如授权、风控冻结、提现冻结）</li>
 *     <li>Pending：尚未最终确认归属账户的在途资金（清算中/处理中）</li>
 * </ul>
 *
 * <h3>重要原则</h3>
 * <ul>
 *     <li>余额 = Ledger 投影结果，而非业务计算结果</li>
 *     <li>该对象必须为只读模型（不可变）</li>
 *     <li>不得在 View 层实现资金规则逻辑</li>
 * </ul>
 *
 * @author wuxp
 * @date 2026-04-15 16:58
 **/
public interface FundsAccountBalanceView {

    /**
     * 账户标识
     *
     * @return 账户标识
     */
    @NonNull
    FundsAccountId getAccountId();

    /**
     * 账户金额的币种
     *
     * @return 币种
     */
    CurrencyIsoCode getCurrency();

    /**
     * 可用余额（Available Balance）
     *
     * <p>
     * 表示账户当前可直接用于消费、支付或转出的资金。
     * </p>
     *
     * <h4>计算来源（概念层）</h4>
     * <pre>
     * Available = 已入账资金 - 冻结资金 - 在途占用资金
     * </pre>
     *
     * <h4>业务语义</h4>
     * <ul>
     *     <li>可用于支付（消费）</li>
     *     <li>可用于提现申请</li>
     *     <li>参与风控校验</li>
     * </ul>
     */
    Money getAvailableBalance();

    /**
     * 冻结余额（Frozen Balance）
     *
     * <p>
     * 表示已属于账户，但当前被系统限制不可使用的资金。
     * </p>
     *
     * <h4>典型来源</h4>
     * <ul>
     *     <li>支付授权冻结（VCC authorization）</li>
     *     <li>提现冻结（withdraw hold）</li>
     *     <li>风控冻结（risk control freeze）</li>
     * </ul>
     *
     * <h4>业务语义</h4>
     * <ul>
     *     <li>仍属于用户资产</li>
     *     <li>不可用于消费或提现</li>
     *     <li>通常可解冻或转移状态</li>
     * </ul>
     */
    @NonNull
    Money getFrozenBalance();

    /**
     * 待结算余额（Pending Balance）
     *
     * <p>
     * 表示尚未完成最终结算或入账的资金，占用或待确认状态。
     * </p>
     *
     * <h4>典型来源</h4>
     * <ul>
     *     <li>支付清算中（card settlement pending）</li>
     *     <li>退款处理中（refund processing）</li>
     *     <li>跨行转账在途（bank transfer in-flight）</li>
     * </ul>
     *
     * <h4>业务语义</h4>
     * <ul>
     *     <li>不属于可用余额</li>
     *     <li>不属于最终冻结余额</li>
     *     <li>通常会在结算完成后转入 Available 或销账</li>
     * </ul>
     */
    @NonNull
    Money getPendingBalance();

    /**
     * 账户总余额（Total Balance）
     *
     * <p>
     * 表示账户整体资产规模，为可用余额 + 冻结余额 + 在途余额的汇总。
     * </p>
     *
     * <h4>业务语义</h4>
     * <ul>
     *     <li>反映账户资产总量</li>
     *     <li>用于对账与审计</li>
     *     <li>不代表可直接使用资金</li>
     * </ul>
     *
     * <h4>注意</h4>
     * <ul>
     *     <li>该值为派生值，不应作为 Ledger 存储字段</li>
     * </ul>
     */
    @NonNull
    default Money getTotalBalance() {
        return getAvailableBalance().add(getFrozenBalance()).add(getPendingBalance());
    }

    /**
     * 数据版本
     *
     * @return 账户数据版本
     */
    @NonNull
    Integer getVersion();
}
