package com.js.sas.service;

import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.metadata.Sheet;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.fastjson.JSONArray;
import com.js.sas.dto.OverdueDTO;
import com.js.sas.repository.PartnerRepository;
import com.js.sas.utils.CommonUtils;
import com.js.sas.utils.DateTimeUtils;
import com.js.sas.utils.excel.StyleExcelHandler;
import com.js.sas.utils.constant.ExcelPropertyEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.StoredProcedureQuery;
import javax.persistence.criteria.Predicate;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.text.ParseException;
import java.util.Date;

/**
 * @ClassName FinanceService
 * @Description 财务Service
 * @Author zc
 * @Date 2019/6/19 18:58
 **/
@Service
@Slf4j
public class FinanceService {

    @Value("${yongyou.url}")
    private String url;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    @Qualifier(value = "sqlServerJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private final DataSource dataSource;

    private final PartnerRepository partnerRepository;

    public FinanceService(DataSource dataSource, PartnerRepository partnerRepository) {
        this.dataSource = dataSource;
        this.partnerRepository = partnerRepository;
    }

    /**
     * 结算客户对账单（线上、线下）
     *
     * @param name      结算客户名称
     * @param channel   来源（线上、线下）
     * @param startDate 开始时间
     * @param endDate   结束时间
     * @param offset    偏移量
     * @param limit     数量
     * @param sort      排序字段
     * @param sortOrder 排序规则
     * @return Map<String, Object>
     */
    public Map<String, Object> getSettlementSummary(String name, String channel, String startDate, String endDate, int offset, int limit, String sort, String sortOrder) {
        HashMap<String, Object> result = new HashMap<>();

        StoredProcedureQuery store = this.entityManager.createNamedStoredProcedureQuery("getSettlementSummary");

        store.setParameter("settlementName", name);
        store.setParameter("channel", channel);
        store.setParameter("startDate", startDate);
        store.setParameter("endDate", endDate);
        store.setParameter("offsetNum", offset);
        store.setParameter("limitNum", limit);
        store.setParameter("sort", sort);
        store.setParameter("sortOrder", sortOrder);

        List settlementSummaryList = store.getResultList();

        result.put("rows", settlementSummaryList);
        result.put("total", store.getOutputParameterValue("totalNum"));

        return result;
    }

    /**
     * 逾期客户
     *
     * @param partner 逾期客户
     * @return 逾期客户列表
     */
    public Page findOverdue(OverdueDTO partner) {
        // 排序规则
        Sort.Direction sortDirection;
        if (partner.getSortOrder() == null || partner.getSortOrder().equals("desc")) {
            sortDirection = Sort.Direction.DESC;
        } else {
            sortDirection = Sort.Direction.ASC;
        }
        // 判断排序字段
        if (!StringUtils.isNotBlank(partner.getSort())) {
            partner.setSort("name");
        }

        if (partner.getLimit() <= 0) {
            partner.setLimit(1);
        }

        Sort sort = new Sort(sortDirection, partner.getSort());
        Pageable pageable = PageRequest.of(partner.getOffset() / partner.getLimit(), partner.getLimit(), sort);

        Specification<OverdueDTO> specification = (Specification<OverdueDTO>) (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.isNotBlank(partner.getCode())) {
                predicates.add(criteriaBuilder.equal(root.<String>get("code"), partner.getCode()));
            }
            if (StringUtils.isNotBlank(partner.getName())) {
                predicates.add(criteriaBuilder.equal(root.<String>get("name"), partner.getName()));
            }
            if (StringUtils.isNotBlank(partner.getOnlyOverdue()) && "true".equals(partner.getOnlyOverdue())) {
                predicates.add(criteriaBuilder.greaterThan(root.<BigDecimal>get("receivablesBeforeToday"), new BigDecimal(0)));
            }
            predicates.add(criteriaBuilder.equal(root.<String>get("status"), '0'));
            predicates.add(criteriaBuilder.equal(root.<String>get("settlementType"), 1));
            predicates.add(criteriaBuilder.equal(root.<String>get("parentCode"), "0"));
            return criteriaBuilder.and(predicates.toArray(new Predicate[predicates.size()]));
        };

        return partnerRepository.findAll(specification, pageable);
    }

    /**
     * 逾期统计列名
     *
     * @return Map<String, Object>
     */
    public Map<String, List<String>> findOverdueAllColumns() {
        List<String> columnList = new ArrayList<>();

        columnList.add("部门");
        columnList.add("业务员");
        columnList.add("往来单位名称");
        columnList.add("账期月");
        columnList.add("账期日");
        columnList.add("订货客户");
        columnList.add("应收总计");
        columnList.add("逾期款");
        columnList.add("期初应收");

        // 当前时间
        Calendar now = Calendar.getInstance();
        // 初始时间
        Calendar origin = Calendar.getInstance();
        // 2019-01-01 00:00:00
        origin.set(2019, 0, 1, 0, 0, 0);

        while (origin.before(now)) {
            columnList.add(origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月");
            // 加1个月
            origin.add(Calendar.MONTH, 1);
        }
        // 如果日期大于27日，则需要多计算下一个月
        if (now.get(Calendar.DATE) > 27) {
            columnList.add(origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月");
        }

        HashMap<String, List<String>> result = new HashMap<>();
        result.put("columns", columnList);
        return result;
    }

    /**
     * 20191226：财务需求：不是仓库特殊用户，且应收总计等于0的不显示。
     *
     * @param partner
     * @return
     */
    public List<Object[]> findOverdueAll(OverdueDTO partner) {

        StringBuilder sqlStringBuilder = new StringBuilder("SELECT yap.warehouse_sign, yap.parent_code, yap.amount_delivery, yap.amount_collected, IFNULL(ds.department,'') AS 部门, " +
                "yap.customer_service_staff AS 业务员, yap.code AS 用友往来单位编码, yap.name AS 往来单位名称, yap.payment_month AS 账期月, " +
                "IF(yap.settlement_type = '1' AND yap.parent_code = '0', '现款', yap.payment_date) AS 账期日, IF(yap.parent_name IS NULL OR yap.parent_name = '' OR (yap.settlement_type = '1' AND yap.parent_code = '0'), yap.NAME, yap.parent_name) AS 订货客户, yap.receivables AS 应收总计, " +
                "yap.amount_delivery + yap.opening_balance - yap.amount_collected + yap.amount_refund AS 逾期款, yap.opening_balance AS 期初应收 ");
        // 当前时间
        Calendar now = Calendar.getInstance();
        // 初始时间
        Calendar origin = Calendar.getInstance();
        // 2019-01-01 00:00:00
        origin.set(2019, 0, 1, 0, 0, 0);

        while (origin.before(now)) {
            sqlStringBuilder.append(", SUM(CASE months WHEN '" + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月销售' THEN vssm.amount ELSE 0 END) AS " + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月 ");
            sqlStringBuilder.append(", SUM(CASE months WHEN '" + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月退货' THEN vssm.amount ELSE 0 END) AS " + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月退货 ");
            // 加1个月
            origin.add(Calendar.MONTH, 1);
        }

        // 如果日期大于27日，则需要多计算下一个月
        if (now.get(Calendar.DATE) > 27) {
            sqlStringBuilder.append(", SUM(CASE months WHEN '" + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月销售' THEN vssm.amount ELSE 0 END) AS " + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月 ");
            sqlStringBuilder.append(", SUM(CASE months WHEN '" + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月退货' THEN vssm.amount ELSE 0 END) AS " + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月退货 ");
        }

        sqlStringBuilder.append(" FROM YY_AA_Partner yap ");
        sqlStringBuilder.append(" LEFT JOIN v_settlement_sales_months vssm ON yap.id = vssm.settlementId ");
        sqlStringBuilder.append(" LEFT JOIN dept_staff ds ON yap.customer_service_staff = ds.name ");
        sqlStringBuilder.append(" WHERE yap.status = 0 ");
        if (partner != null && StringUtils.isNotBlank(partner.getCode())) {
            sqlStringBuilder.append(" AND yap.code = '" + partner.getCode() + "' ");
        }
        if (partner != null && StringUtils.isNotBlank(partner.getName())) {
            sqlStringBuilder.append(" AND yap.name = '" + partner.getName() + "' ");
        }
        if (partner != null && StringUtils.isNotBlank(partner.getOnlyOverdue()) && partner.getOnlyOverdue().equals("true")) {
            sqlStringBuilder.append(" AND yap.receivables > 0 ");
        }
        sqlStringBuilder.append(" GROUP BY ");
        sqlStringBuilder.append(" yap.id, ds.department, yap.code, yap.parent_code, yap.payment_month, yap.payment_date, yap.name, yap.amount_delivery, yap.amount_collected, yap.opening_balance ");

        if (partner != null) {
            sqlStringBuilder.append(" ORDER BY yap.parent_code DESC, yap.name ASC LIMIT " + partner.getOffset() + ", " + partner.getLimit());
        } else {
            sqlStringBuilder.append(" ORDER BY yap.parent_code DESC, yap.name ASC ");
        }

        Query query = entityManager.createNativeQuery(sqlStringBuilder.toString());

        //query.unwrap(NativeQueryImpl.class).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);

        return query.getResultList();
    }

    public BigInteger findOverdueAllCount(OverdueDTO partner) {
        StringBuilder sqlCountStringBuilder = new StringBuilder("SELECT COUNT(1) FROM ( SELECT 1 ");
        sqlCountStringBuilder.append(" FROM YY_AA_Partner yap ");
        sqlCountStringBuilder.append(" LEFT JOIN v_settlement_sales_months vssm ON yap.id = vssm.settlementId ");
        sqlCountStringBuilder.append(" LEFT JOIN dept_staff ds ON yap.customer_service_staff = ds.name ");
        sqlCountStringBuilder.append(" WHERE yap.status = 0 ");
        if (partner != null && StringUtils.isNotBlank(partner.getCode())) {
            sqlCountStringBuilder.append(" AND yap.code = '" + partner.getCode() + "' ");
        }
        if (partner != null && StringUtils.isNotBlank(partner.getName())) {
            sqlCountStringBuilder.append(" AND yap.name = '" + partner.getName() + "' ");
        }
        if (partner != null && StringUtils.isNotBlank(partner.getOnlyOverdue()) && partner.getOnlyOverdue().equals("true")) {
            sqlCountStringBuilder.append(" AND yap.receivables > 0 ");
        }
        sqlCountStringBuilder.append(" GROUP BY ");
        sqlCountStringBuilder.append(" yap.id, ds.department, yap.code, yap.parent_code, yap.payment_month, yap.payment_date, yap.name, yap.amount_delivery, yap.amount_collected, yap.opening_balance ");
        sqlCountStringBuilder.append(" ) t ");
        return (BigInteger) entityManager.createNativeQuery(sqlCountStringBuilder.toString()).getSingleResult();
    }

    /**
     * 逾期统计（销售版）列名
     *
     * @return Map<String, Object>
     */
    public Map<String, List<String>> findOverdueSalesColumns() {
        List<String> columnList = new ArrayList<>();

        columnList.add("部门");
        columnList.add("业务员");
        columnList.add("往来单位名称");
        columnList.add("账期月");
        columnList.add("账期日");
        columnList.add("订货客户");
        columnList.add("应收总计");
        columnList.add("逾期款");
        columnList.add("期初应收");

        // 当前时间
        Calendar now = Calendar.getInstance();
        // 加1个月
        now.add(Calendar.MONTH, 1);
        // 减3个月
        now.add(Calendar.MONTH, -3);
        columnList.add(now.get(Calendar.YEAR) + "年" + (now.get(Calendar.MONTH) + 1) + "月");
        // 加1个月
        now.add(Calendar.MONTH, 1);
        columnList.add(now.get(Calendar.YEAR) + "年" + (now.get(Calendar.MONTH) + 1) + "月");
        // 加1个月
        now.add(Calendar.MONTH, 1);
        columnList.add(now.get(Calendar.YEAR) + "年" + (now.get(Calendar.MONTH) + 1) + "月");
        // 加1个月
        now.add(Calendar.MONTH, 1);
        columnList.add(now.get(Calendar.YEAR) + "年" + (now.get(Calendar.MONTH) + 1) + "月");

        HashMap<String, List<String>> result = new HashMap<>();
        result.put("columns", columnList);
        return result;
    }

    /**
     * 逾期统计（销售版）
     * <p>
     * 这里的第一个字段，yap.warehouse_sign，已经废弃
     *
     * @param partner
     * @return
     */
    public List<Object[]> findOverdueSales(OverdueDTO partner) {

        StringBuilder sqlStringBuilder = new StringBuilder("SELECT yap.parent_code, yap.amount_delivery, yap.amount_collected, IFNULL(ds.department,'-') AS 部门, " +
                "IFNULL( yap.customer_service_staff, '-' ) AS 业务员, yap.name AS 往来单位名称, yap.payment_month AS 账期月, " +
                "IF(yap.parent_name IS NULL OR (yap.parent_name = '' AND yap.settlement_type != '2') OR (yap.settlement_type = '1' AND yap.parent_code = '0'), '现款', yap.payment_date) AS 账期日, IF(yap.parent_name IS NULL OR yap.parent_name = '' OR (yap.settlement_type = '1' AND yap.parent_code = '0'), yap.NAME, yap.parent_name) AS 订货客户, yap.receivables AS 应收总计, " +
                "yap.amount_delivery + yap.opening_balance - yap.amount_collected + yap.amount_refund AS 逾期款, yap.opening_balance AS 期初应收 ");
        // 当前时间
        Calendar now = Calendar.getInstance();
        /**
         * 初始时间
         * 功能只需要显示3个月的，但是涉及账期问题，如果账期一个月，需要多计算1个月，也就是4个月。目前按多算1个月，再加后1个月，也就是6个月的数据。
         */
        Calendar origin = Calendar.getInstance();
        origin.add(Calendar.MONTH, -5);
        origin.set(origin.get(Calendar.YEAR), origin.get(Calendar.MONTH), 1, 0, 0, 0);

        while (origin.before(now)) {
            sqlStringBuilder.append(", MAX(CASE months WHEN '" + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月销售' THEN vssm.amount ELSE 0 END) AS " + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月 ");
            sqlStringBuilder.append(", MIN(CASE months WHEN '" + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月退货' THEN vssm.amount ELSE 0 END) AS " + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月退货 ");
            sqlStringBuilder.append(", MIN( CASE vsr.months_received WHEN '" + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月收款' THEN vsr.amount_received ELSE 0 END ) AS " + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月收款 ");
            // 加1个月
            origin.add(Calendar.MONTH, 1);
        }
        // 多统计一个月
        sqlStringBuilder.append(", MAX(CASE months WHEN '" + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月销售' THEN vssm.amount ELSE 0 END) AS " + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月 ");
        sqlStringBuilder.append(", MIN(CASE months WHEN '" + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月退货' THEN vssm.amount ELSE 0 END) AS " + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月退货 ");
        sqlStringBuilder.append(", MIN( CASE vsr.months_received WHEN '" + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月收款' THEN vsr.amount_received ELSE 0 END ) AS " + origin.get(Calendar.YEAR) + "年" + (origin.get(Calendar.MONTH) + 1) + "月收款 ");

        sqlStringBuilder.append(" FROM YY_AA_Partner yap ");
        sqlStringBuilder.append(" LEFT JOIN v_settlement_sales_months_v3 vssm ON yap.id = vssm.settlementId ");
        sqlStringBuilder.append(" LEFT JOIN v_settlement_received vsr ON yap.id = vsr.settlementId ");
        sqlStringBuilder.append(" LEFT JOIN YY_AA_Partner yapp ON yapp.`code` = yap.parent_code ");
        sqlStringBuilder.append(" LEFT JOIN dept_staff ds ON yap.customer_service_staff = ds.NAME ");
        sqlStringBuilder.append(" WHERE yap.status = 0 ");
        if (partner != null && StringUtils.isNotBlank(partner.getCode())) {
            sqlStringBuilder.append(" AND yap.code = '" + partner.getCode() + "' ");
        }
        if (partner != null && StringUtils.isNotBlank(partner.getName())) {
            sqlStringBuilder.append(" AND yap.name = '" + partner.getName() + "' ");
        }
        if (partner != null && StringUtils.isNotBlank(partner.getOnlyOverdue()) && partner.getOnlyOverdue().equals("true")) {
            sqlStringBuilder.append(" AND yap.receivables > 0 ");
        }
        sqlStringBuilder.append(" GROUP BY ");
        sqlStringBuilder.append(" yap.id, ds.department, yapp.customer_service_staff, yap.code, yap.parent_code, yap.payment_month, yap.payment_date, yap.name, yap.amount_delivery, yap.amount_collected, yap.opening_balance ");

        if (partner != null) {
            sqlStringBuilder.append(" ORDER BY yap.parent_code DESC, yap.name ASC LIMIT " + partner.getOffset() + ", " + partner.getLimit());
        } else {
            sqlStringBuilder.append(" ORDER BY yap.parent_code DESC, 账期日 ASC ");
        }

        Query query = entityManager.createNativeQuery(sqlStringBuilder.toString());

        return query.getResultList();
    }

    /**
     * 销售版逾期统计获取数据列表
     *
     * @param partner OverdueDTO
     * @return List<List < Object>>
     */
    public List<List<Object>> getOverdueSalesList(OverdueDTO partner) {
        // 数据List
        List<Object[]> overdueSalesList = findOverdueSales(partner);
        // 数据结果列表
        List<List<Object>> rowsList = new ArrayList<>();
        for (Object[] dataRow : overdueSalesList) {
            // 数据行List
            ArrayList<Object> dataList = new ArrayList<>();
            // 账期月, 目前rs第6列
            int month = Integer.parseInt(dataRow[6].toString());
            // 账期日，目前rs第7列
            int day = 0;
            // 现款客户标记
            boolean cash = false;
            if (StringUtils.isNumeric(dataRow[7].toString())) {
                day = Integer.parseInt(dataRow[7].toString());
            } else {
                cash = true;
            }
            // 应减去的结算周期数
            int overdueMonths = CommonUtils.overdueMonth(month, day);
            // 当前日期
            int nowDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
            // 如果当前日期小于27，账期月份需要+1
            if (nowDay <= 27) {
                overdueMonths++;
            }
            // 仓库标记
            boolean warehouse = false;
            // 关联code不是零是仓库特殊客户
            if (!dataRow[0].toString().equals("0")) {
                warehouse = true;
            }
            // 应收总计
            BigDecimal receivables = new BigDecimal(dataRow[9].toString());
            // 当前逾期金额
            BigDecimal overdue = new BigDecimal(dataRow[10].toString());
            // 设置数据行，移除前3列（关联id列、总发货、总收款）
            for (int i = 3; i < dataRow.length; i++) {
                if (i > 11) {  // 计算每个账期的应收款
                    if (i >= dataRow.length - overdueMonths * 3) { // 未逾期月份
                        /**
                         * 20191226修改：
                         * 1. 预期总额等于各月逾期金额之和。
                         * 2. 仓库特殊用户，退款金额不抵扣之前的欠款，只计算当月。
                         *
                         * 之前的规则：
                         * 有关联的账期客户逾期总金额不计算未到账期的退货金额，无关联关系的账期客户预期总金额计算所有退货金额
                         * 分月统计全部计算所有退货金额
                         *
                         * 20200103：仓库客户，逾期款不需要减每个月收款，只减退货金额，之前的逻辑是错的。
                         */
                        // 减发货金额
                        overdue = overdue.subtract(new BigDecimal(dataRow[i++].toString()));
                        if (warehouse) { // 标记仓库
                            // 逾期款扣除退货金额
                            overdue = overdue.subtract(new BigDecimal(dataRow[i++].toString()));
                            // 当月发货+退货=实际发货金额
                            BigDecimal monthReceivables = new BigDecimal(dataRow[i - 2].toString()).add(new BigDecimal(dataRow[i - 1].toString()));
                            dataList.add(monthReceivables);
                        } else { // 不是仓库客户，逾期款不需要扣除退货金额
                            // 设置当月发货金额
                            dataList.add(new BigDecimal(dataRow[i - 1].toString()));
                            i++;
                        }
                    } else { // 逾期月份
                        // 发货+退货 = 实际发货金额
                        BigDecimal monthReceivables = new BigDecimal(dataRow[i++].toString()).add(new BigDecimal(dataRow[i++].toString()));
                        dataList.add(monthReceivables);
                    }
                } else if (i > 10) {
                    dataList.add(new BigDecimal(dataRow[i].toString()));
                } else {
                    dataList.add(dataRow[i].toString());
                }
            }
            // 应收总计设置为数字格式
            dataList.set(6, receivables);
            // 现款客户，逾期款等于应收总计
            if (cash) {
                overdue = new BigDecimal(dataList.get(7).toString());
                dataList.set(8, dataList.get(7));
            }
            // 导出数据，按规则删除不需要的数据
            if (partner == null) {
                // 去掉应收总计、逾期款都为0的
                if (overdue.compareTo(BigDecimal.ZERO) == 0 && receivables.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                // 不是仓库的，去掉应收总计是0的
                if (!warehouse && receivables.compareTo(BigDecimal.ZERO) < 1) {
                    continue;
                }
            }

            if (cash) { // 现款客户
                /**
                 * 现款计算四个月，dataList从后向前，到第12列为止
                 */
                for (int index = dataList.size() - 1; index > 11; index--) {
                    if (overdue.compareTo(BigDecimal.ZERO) == 0) { // 逾期款等于0，所有账期逾期金额都是0
                        dataList.set(index, 0);
                    } else if (overdue.compareTo(BigDecimal.ZERO) == -1) {  // 逾期金额小于0
                        // dataList.size()-index ：倒数第几个，从倒数第一个开始。
                        int dataIndex = dataList.size() - index;
                        // dataRow从最后一个向前，每3列一个收款金额. receiveIndex表示dataRow的倒数第几个，比如倒数第三个，倒数第六个。
                        int deliveryIndex = ((dataIndex - 1) * 3) + 3;
                        // dataRow从倒数第二个向前，每3列一个退款金额. refundIndex表示dataRow的倒数第几个，比如倒数第二个，倒数第五个。
                        int refundIndex = ((dataIndex - 1) * 3) + 2;
                        // 当月发货金额
                        BigDecimal monthDelivery = new BigDecimal(dataRow[dataRow.length - deliveryIndex].toString());
                        // 当月退货金额
                        BigDecimal monthRefund = new BigDecimal(dataRow[dataRow.length - refundIndex].toString());

                        if (monthDelivery.compareTo(BigDecimal.ZERO) == 0 && monthRefund.compareTo(BigDecimal.ZERO) == 0) { // 如果发货和退货都是0，跳过，本月就是0
                            dataList.set(index, 0);
                        } else if (overdue.compareTo(new BigDecimal(dataList.get(index).toString())) > -1) {
                            // 对比收款，如果逾期金额大于等于当月发货金额+退货金额，那么显示逾期的数量（因为是负数，所以和正数的逻辑是反的）
                            dataList.set(index, overdue);
                            overdue = BigDecimal.ZERO;
                        } else {
                            // 如果逾期金额小于当月收款金额+退款金额，那么显示收款金额+退货金额，相减得到新的逾期金额
                            dataList.set(index, dataList.get(index).toString());
                            overdue = overdue.subtract((new BigDecimal(dataList.get(index).toString())));
                        }
                    } else {  // 逾期金额大于0，从最后一个开始分摊逾期金额
                        // 如果当月总计金额小于等于0，说明当月有预收款，设置当月为0，然后计算下一个月。
                        if (new BigDecimal(dataList.get(index).toString()).compareTo(BigDecimal.ZERO) < 1) {
                            dataList.set(index, BigDecimal.ZERO);
                            continue;
                        }
                        // 大于等于当月发货，设置当月发货金额
                        if (overdue.compareTo(new BigDecimal(dataList.get(index).toString())) > -1) {
                            overdue = overdue.subtract(new BigDecimal(dataList.get(index).toString()));
                            dataList.set(index, new BigDecimal(dataList.get(index).toString()));
                        } else {
                            // 小于当月发货，设置逾期金额，并置零
                            dataList.set(index, overdue);
                            overdue = BigDecimal.ZERO;
                        }
                    }
                }
            } else { // 账期客户
                /**
                 * 20191227:
                 * 未逾期，应显示应收金额，每月应收 + 每月逾期 + 期初应收 = 总应收
                 *
                 * 未逾期的应收款 = 应收金额 - 逾期款
                 */
                BigDecimal tempReceivables = receivables.subtract(overdue);

                for (int index = dataList.size() - 1; index > dataList.size() - 1 - overdueMonths; index--) {
                    if (tempReceivables.compareTo(BigDecimal.ZERO) == 0) { // 等于0
                        dataList.set(index, 0);
                    } else if (tempReceivables.compareTo(BigDecimal.ZERO) > 0) { // 大于0
                        if (tempReceivables.compareTo(new BigDecimal(dataList.get(index).toString())) > -1) {
                            tempReceivables = tempReceivables.subtract(new BigDecimal(dataList.get(index).toString()));
                            dataList.set(index, dataList.get(index));
                        } else {
                            dataList.set(index, tempReceivables);
                            tempReceivables = BigDecimal.ZERO;
                        }
                    } else { // 小于0
                        if (new BigDecimal(dataList.get(index).toString()).compareTo(BigDecimal.ZERO) == 0) { // 发货+退货为0
                            dataList.set(index, 0);
                        } else if (tempReceivables.compareTo(new BigDecimal(dataList.get(index).toString())) > -1) { // 应收大于等于发货+退货
                            dataList.set(index, tempReceivables);
                            tempReceivables = BigDecimal.ZERO;
                        } else { // 应收小于发货
                            dataList.set(index, new BigDecimal(dataList.get(index).toString()));
                            tempReceivables = tempReceivables.subtract(new BigDecimal(dataList.get(index).toString()));
                        }
                    }
                }

                // 设置逾期款
                dataList.set(7, overdue);

                /**
                 * 功能只需要显示3个月的，但是涉及账期问题，如果账期一个月，需要多计算1个月，也就是4个月。目前按多算3个月，也就是6个月的数据。
                 * 因为是6个月的数据，所以逾期的分摊只需要算三个月，从第11列开始。
                 * 需要去掉未到账期的月份
                 *
                 * 20191227：修改为显示4个月
                 *
                 * 20200104：如果逾期金额小于等于0：
                 * 1.逾期月份的金额先设置为0；
                 * 2.讲逾期金额（预付款）放在最后一个账期，！！！注意:如果最后一个账期，所有相关联的用户都没有正数的应收款，则后移，按此规则一直移到统计当月的下个月
                 */
                int overdueIndex = 0;
                if (overdueMonths > 3) {
                    // 这里的10 - (overdueMonths - 3)，因为1个月账期从10以后开始计算，如果是超过1个月，需要根据账期多计算(overdueMonths - 3)个月
                    overdueIndex = overdueMonths - 3;
                }
                for (int index = dataList.size() - 1 - overdueMonths; index > 10 - overdueIndex; index--) {
                    // 逾期款小于等于0，所有账期月、期初应收都是0
                    if (overdue.compareTo(BigDecimal.ZERO) < 1) {

                        /**
                         * 20200103：
                         * 1.逾期款为负数，那么显示在最后一个账期。
                         */
                        if (index == dataList.size() - 1 - overdueMonths) {
                            dataList.set(index, overdue);
                        } else {
                            dataList.set(index, BigDecimal.ZERO);
                        }

                        overdue = BigDecimal.ZERO;
                    } else {  // 逾期金额大于0，从最后一个开始分摊逾期金额
                        // 大于等于当月发货，设置当月发货金额
                        if (overdue.compareTo(new BigDecimal(dataList.get(index).toString())) > -1) {
                            overdue = overdue.subtract(new BigDecimal(dataList.get(index).toString()));
                            // 发货金额大于等于0
                            //if (new BigDecimal(dataList.get(index).toString()).compareTo(BigDecimal.ZERO) > -1) {
                            dataList.set(index, new BigDecimal(dataList.get(index).toString()));
                            //} else { // 发货金额小于0，当月逾期金额设置为0
                            //dataList.set(index, BigDecimal.ZERO);
                            //}
                        } else {
                            // 小于当月发货，设置逾期金额，并置零
                            dataList.set(index, overdue);
                            overdue = BigDecimal.ZERO;
                        }
                    }
                }

            }
            // 设置期初应收
            dataList.set(8, overdue);
            // 账期客户进行账期月设置
            if (!cash) {
                for (int overdueIndex = 0; overdueIndex < month; overdueIndex++) {
                    // 插入0
                    dataList.add(9, 0);
                    // 删除最后一位
                    dataList.remove(dataList.size() - 1);
                }
            }

            rowsList.add(dataList);
        }

        return rowsList;
    }

    /**
     * 查询供应商对账单
     *
     * @param hashMap
     * @param isOnline
     * @return
     */
    public List<Map<String, Object>> getSupplier(Map<String, String> hashMap, String isOnline) {
        long pageSize = 0L;
        long offset = 0L;
        try {
            if (hashMap.containsKey("limit") && StringUtils.isNotBlank(hashMap.get("limit"))) {
                pageSize = Long.parseLong(hashMap.get("limit").trim());
            }
            if (hashMap.containsKey("offset") && StringUtils.isNotBlank(hashMap.get("offset"))) {
                offset = Long.parseLong(hashMap.get("offset").trim());
            }

            List list = new ArrayList<String>();
            StringBuilder builder = new StringBuilder();
            builder.append("select * from ( select Row_Number() over (ORDER BY tb.voucherdate,tb.code)as RowNumbes,aa.name ,tb.* from AA_Partner aa ");
            builder.append(" LEFT JOIN( select code ,voucherdate,'1' plus,idpartner,'进货单' type,totalTaxAmount Amount ");
            builder.append(" ,(case pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline");
            builder.append(" from PU_PurchaseArrival  where voucherState = 189 UNION ");
            builder.append(" select code ,voucherdate,'0' plus,idfirstpartner idpartner,'应付冲应付' type,'0' Amount ,'线上' isOnline");
            builder.append(" from ARAP_StrikeBalance  where idbusitype = 13 UNION");
            builder.append(" select code ,voucherdate,'-2' plus,idpartner,'开票单据' ,sum(OrigTaxAmount) Amount,isOnline from ( ");
            builder.append(" select ppi.code,ppib.OrigTaxAmount,ppi.voucherdate,ppi.idpartner,");
            builder.append(" (case ppa.pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline");
            builder.append(" from PU_PurchaseInvoice ppi ");
            builder.append(" left join PU_PurchaseInvoice_b ppib on ppib.idPurchaseInvoiceDTO = ppi.id");
            builder.append(" left join PU_PurchaseArrival ppa on ppa.code = ppib.SourceVoucherCode where ppi.voucherState = 189 ) tb ");
            builder.append(" GROUP BY isOnline,code ,voucherdate,idpartner ");
            builder.append(" UNION select  code ,voucherdate,'-1' plus,idpartner,'付款单' type,origAmount Amount");
            builder.append(" ,(case pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline");
            builder.append(" from ARAP_ReceivePayment   where idbusitype = 80 ");
            builder.append(" ) tb on aa.id = tb.idpartner where isOnline =?");
            list.add(isOnline);
            if (hashMap.containsKey("name") && StringUtils.isNotBlank(hashMap.get("name"))) {
                builder.append(" and aa.name =? ");
                list.add(hashMap.get("name"));
            }
            if (hashMap.containsKey("startDate") && StringUtils.isNotBlank(hashMap.get("startDate"))) {
                builder.append(" and tb.voucherdate >= ?");
                String ss = hashMap.get("startDate") + " 00:00:00";
                list.add(DateTimeUtils.parseDate(ss, DateTimeUtils.DATE_TIME_FORMAT));
            }
            if (hashMap.containsKey("endDate") && StringUtils.isNotBlank(hashMap.get("endDate"))) {
                builder.append(" and tb.voucherdate <= ?");
                String ss = hashMap.get("endDate") + " 23:59:59";
                list.add(DateTimeUtils.parseDate(ss, DateTimeUtils.DATE_TIME_FORMAT));
            }

            builder.append(" )tba where 1=1 ");

            builder.append(" and RowNumbes BETWEEN " + (offset + 1) + " and " + (offset + pageSize));
            return jdbcTemplate.queryForList(builder.toString(), list.toArray());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 数据条数
     *
     * @param hashMap
     * @return
     */
    public long getSupplierCount(Map<String, String> hashMap, String isOnline) {
        try {
            List list = new ArrayList<String>();
            StringBuilder builder = new StringBuilder();
            builder.append(" select count(*) from AA_Partner aa LEFT JOIN( ");
            builder.append(" select code,idpartner,voucherdate,(case pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline ");
            builder.append(" from PU_PurchaseArrival  where voucherState = 189 ");
            builder.append(" UNION select code ,idfirstpartner idpartner,voucherdate,'线上' isOnline from ARAP_StrikeBalance  where idbusitype = 13  ");
            builder.append(" UNION select code ,idpartner,voucherdate,isOnline from ");
            builder.append(" (select ppi.code,ppi.idpartner,ppi.voucherdate,(case ppa.pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline  ");
            builder.append(" from PU_PurchaseInvoice ppi  left join PU_PurchaseInvoice_b ppib on ppib.idPurchaseInvoiceDTO = ppi.id ");
            builder.append(" left join PU_PurchaseArrival ppa on ppa.code = ppib.SourceVoucherCode where ppi.voucherState = 189 ) tb  ");
            builder.append(" GROUP BY isOnline,code ,voucherdate,idpartner  ");
            builder.append(" UNION select  code ,idpartner,voucherdate,(case pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline ");
            builder.append(" from ARAP_ReceivePayment   where idbusitype = 80  ");
            builder.append(" ) tb on aa.id = tb.idpartner where isOnline =? ");
            list.add(isOnline);
            if (hashMap.containsKey("name") && StringUtils.isNotBlank(hashMap.get("name"))) {
                builder.append(" and aa.name =? ");
                list.add(hashMap.get("name"));
            }
            if (hashMap.containsKey("startDate") && StringUtils.isNotBlank(hashMap.get("startDate"))) {
                builder.append(" and tb.voucherdate >= ?");
                String ss = hashMap.get("startDate") + " 00:00:00";
                list.add(DateTimeUtils.parseDate(ss, DateTimeUtils.DATE_TIME_FORMAT));
            }
            if (hashMap.containsKey("endDate") && StringUtils.isNotBlank(hashMap.get("endDate"))) {
                builder.append(" and tb.voucherdate <= ?");
                String ss = hashMap.get("endDate") + " 23:59:59";
                list.add(DateTimeUtils.parseDate(ss, DateTimeUtils.DATE_TIME_FORMAT));
            }
//            System.out.println("【getSupplierCount】："+builder.toString());
            return jdbcTemplate.queryForObject(builder.toString(), list.toArray(), Long.class);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return 0L;
    }

    /**
     * 获取用户期初数据
     *
     * @param hashMap
     * @return
     */
    public Map<String, Object> getOrigAmount(Map<String, String> hashMap, String isOnline) throws ParseException {
        if (!hashMap.containsKey("name") && StringUtils.isBlank(hashMap.get("name"))) {
            throw new RuntimeException("未获取到用户");
        }
        StringBuilder builder = new StringBuilder();
        List list = new ArrayList<String>();
        builder.append(" select sum(CASE plus WHEN '1' THEN Amount WHEN '-1' THEN -Amount ELSE 0 END) payable,");
        builder.append(" sum(CASE plus WHEN '1' THEN Amount WHEN '-2' THEN -Amount ELSE 0 END) invoice");
        builder.append(" from AA_Partner aa LEFT JOIN(");
        builder.append(" select code,voucherdate,'1' plus,idpartner,totalTaxAmount Amount ,");
        builder.append(" (case pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline");
        builder.append(" from PU_PurchaseArrival  where voucherState = 189 ");
        builder.append(" UNION select code ,voucherdate,'-2' plus,idpartner,sum(OrigTaxAmount) Amount,isOnline ");
        builder.append(" from (select ppi.code,ppib.OrigTaxAmount,ppi.voucherdate,ppi.idpartner, ");
        builder.append(" (case ppa.pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline ");
        builder.append(" from PU_PurchaseInvoice ppi  left join PU_PurchaseInvoice_b ppib on ppib.idPurchaseInvoiceDTO = ppi.id ");
        builder.append(" left join PU_PurchaseArrival ppa on ppa.code = ppib.SourceVoucherCode where ppi.voucherState = 189 ) tb  GROUP BY isOnline,code ,voucherdate,idpartner");
        builder.append(" UNION select code ,voucherdate,'-1' plus,idpartner,origAmount Amount");
        builder.append(" ,(case pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline");
        builder.append(" from ARAP_ReceivePayment   where idbusitype = 80 ");
        builder.append(" UNION select code ,'2018-12-30 01:01:01','1' plus,idpartner,origAmount Amount,'线上'isOnline from ARAP_OriginalAmount_ApDetail ");
        builder.append(" ) tb on aa.id = tb.idpartner where tb.voucherdate >= '2018-12-28 00:00:00' and isonline = ?");
        list.add(isOnline);
        if (hashMap.containsKey("name") && StringUtils.isNotBlank(hashMap.get("name"))) {
            builder.append(" and aa.name =? ");
            list.add(hashMap.get("name"));
        }
        if (hashMap.containsKey("startDate") && StringUtils.isNotBlank(hashMap.get("startDate"))) {
            builder.append(" and tb.voucherdate < ?");
            String ss = hashMap.get("startDate") + " 00:00:00";
            list.add(DateTimeUtils.parseDate(ss, DateTimeUtils.DATE_TIME_FORMAT));
        }
//        System.out.println("【getOrigAmount】:"+builder.toString());
        return jdbcTemplate.queryForMap(builder.toString(), list.toArray());
    }

    /**
     * 获取分页 offsset前 余额
     *
     * @param hashMap
     * @return
     * @throws ParseException
     */
    public List<Map<String, Object>> getTopOrigAmount(Map<String, String> hashMap, String isOnline) throws ParseException {
        Long offset = 0L;
        if (hashMap.containsKey("offset") && StringUtils.isNotBlank(hashMap.get("offset"))) {
            offset = Long.parseLong(hashMap.get("offset").trim());
        }
        if (!hashMap.containsKey("name") && StringUtils.isBlank(hashMap.get("name"))) {
            throw new RuntimeException("未获取到用户");
        }
        StringBuilder builder = new StringBuilder();
        List list = new ArrayList<String>();
        builder.append(" select sum(CASE plus WHEN '1' THEN Amount WHEN '-1' THEN -Amount ELSE 0 END) payable,");
        builder.append(" sum(CASE plus WHEN '1' THEN Amount WHEN '-2' THEN -Amount ELSE 0 END) invoice");
        builder.append(" from ( select top " + offset + " * from (");
        builder.append(" select aa.name ,tb.* from AA_Partner aa LEFT JOIN(");
        builder.append(" select code,voucherdate,'1' plus,idpartner,totalTaxAmount Amount " +
                ",(case pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline from PU_PurchaseArrival where voucherState = 189");
        builder.append(" UNION (select code,voucherdate,'-2' plus,idpartner, sum(OrigTaxAmount) Amount,isOnline from (");
        builder.append(" select ppi.code,ppib.OrigTaxAmount,ppi.voucherdate,ppi.idpartner,");
        builder.append(" (case ppa.pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline");
        builder.append(" from PU_PurchaseInvoice ppi left join PU_PurchaseInvoice_b ppib on ppib.idPurchaseInvoiceDTO = ppi.id");
        builder.append(" left join PU_PurchaseArrival ppa on ppa.code = ppib.SourceVoucherCode where ppi.voucherState = 189 ) tb ");
        builder.append(" GROUP BY isOnline,code ,voucherdate,idpartner)");
        builder.append(" UNION select  code ,voucherdate,'-1' plus,idpartner,origAmount Amount" +
                ",(case pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline from ARAP_ReceivePayment   where idbusitype = 80");
        builder.append(" ) tb on aa.id = tb.idpartner where isOnline =? ");
        list.add(isOnline);
        if (hashMap.containsKey("name") && StringUtils.isNotBlank(hashMap.get("name"))) {
            builder.append(" and aa.name =? ");
            list.add(hashMap.get("name"));
        }
        if (hashMap.containsKey("startDate") && StringUtils.isNotBlank(hashMap.get("startDate"))) {
            builder.append(" and tb.voucherdate >= ?");
            String ss = hashMap.get("startDate") + " 00:00:00";
            list.add(DateTimeUtils.parseDate(ss, DateTimeUtils.DATE_TIME_FORMAT));
        }
        if (hashMap.containsKey("endDate") && StringUtils.isNotBlank(hashMap.get("endDate"))) {
            builder.append(" and tb.voucherdate <= ?");
            String ss = hashMap.get("endDate") + " 23:59:59";
            list.add(DateTimeUtils.parseDate(ss, DateTimeUtils.DATE_TIME_FORMAT));
        }
        builder.append(" )ss ORDER BY voucherdate,code )bb GROUP BY bb.idpartner;");
//        System.out.println("【getTopOrigAmount】："+ builder.toString());
        return jdbcTemplate.queryForList(builder.toString(), list.toArray());
    }

    /**
     * 结算
     *
     * @param hashMap
     * @return
     * @throws ParseException
     */
    public List<Map<String, Object>> getSupplierCount(Map<String, String> hashMap) throws ParseException {
        if (!hashMap.containsKey("name") && StringUtils.isBlank(hashMap.get("name"))) {
            throw new RuntimeException("未获取到用户");
        }
        StringBuilder builder = new StringBuilder();
        List list = new ArrayList<String>();
        builder.append(" select ");
        builder.append(" sum(CASE plus WHEN '1' THEN Amount ELSE 0 END) receivingAmount,");
        builder.append(" sum(CASE plus WHEN '-1' THEN Amount ELSE 0 END) paymentAmount,");
        builder.append(" sum(CASE plus WHEN '-2' THEN Amount ELSE 0 END) invoiceAmount,isOnline type ");
        builder.append(" from AA_Partner aa LEFT JOIN( ");
        builder.append(" select code,voucherdate,'1' plus,idpartner,totalTaxAmount Amount ,");
        builder.append(" (case pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline ");
        builder.append(" from PU_PurchaseArrival  where voucherState = 189 ");
        builder.append(" UNION  select code ,voucherdate,'0' plus,idfirstpartner idpartner,'0' Amount , ");
        builder.append(" '线上' isOnline from ARAP_StrikeBalance  where idbusitype = 13 ");
        builder.append(" UNION select code ,voucherdate,'-2' plus,idpartner,sum(OrigTaxAmount) Amount,isOnline");
        builder.append(" from (  select ppi.code,ppib.OrigTaxAmount,ppi.voucherdate,ppi.idpartner,");
        builder.append(" (case ppa.pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline");
        builder.append(" from PU_PurchaseInvoice ppi  left join PU_PurchaseInvoice_b ppib on ppib.idPurchaseInvoiceDTO = ppi.id");
        builder.append(" left join PU_PurchaseArrival ppa on ppa.code = ppib.SourceVoucherCode");
        builder.append(" where ppi.voucherState = 189 ) tb  GROUP BY isOnline,code ,voucherdate,idpartner");
        builder.append(" UNION select  code ,voucherdate,'-1' plus,idpartner,origAmount Amount ,");
        builder.append(" (case pubuserdefnvc1 when '限时购' then '线上' when '线上' then '线上' else '线下' END) isOnline");
        builder.append(" from ARAP_ReceivePayment where idbusitype = 80");
        builder.append(" ) tb on aa.id = tb.idpartner where 1=1");
        if (hashMap.containsKey("name") && StringUtils.isNotBlank(hashMap.get("name"))) {
            builder.append(" and aa.name =? ");
            list.add(hashMap.get("name"));
        }
        if (hashMap.containsKey("startDate") && StringUtils.isNotBlank(hashMap.get("startDate"))) {
            builder.append(" and tb.voucherdate >= ?");
            String ss = hashMap.get("startDate") + " 00:00:00";
            list.add(DateTimeUtils.parseDate(ss, DateTimeUtils.DATE_TIME_FORMAT));
        }
        if (hashMap.containsKey("endDate") && StringUtils.isNotBlank(hashMap.get("endDate"))) {
            builder.append(" and tb.voucherdate <= ?");
            String ss = hashMap.get("endDate") + " 23:59:59";
            list.add(DateTimeUtils.parseDate(ss, DateTimeUtils.DATE_TIME_FORMAT));
        }
        builder.append(" GROUP BY isonline ;");
//        System.out.println("结算【getSupplierCount】："+ builder.toString());
        List<Map<String, Object>> mapList = jdbcTemplate.queryForList(builder.toString(), list.toArray());
        Date date = DateTimeUtils.parseDate(hashMap.get("endDate"), DateTimeUtils.DATE_FORMAT);
        Timestamp time = DateTimeUtils.addTime(date, 1, DateTimeUtils.DAY);
        hashMap.put("startDate", DateTimeUtils.convert(time, DateTimeUtils.DATE_FORMAT));
        Map<String, Object> online = this.getOrigAmount(hashMap, "线上");
        Map<String, Object> offline = this.getOrigAmount(hashMap, "线下");

        for (Map<String, Object> objectMap : mapList) {
            if ("线上".equals(objectMap.get("type"))) {
                objectMap.put("balancePayableAmount", online.get("payable"));
                objectMap.put("balanceInvoiceAmount", online.get("invoice"));
            } else {
                objectMap.put("balancePayableAmount", offline.get("payable"));
                objectMap.put("balanceInvoiceAmount", offline.get("invoice"));
            }
        }
        return mapList;
    }

    /*public List<Map<String, Object>> getPurchaseArrivalDetail(Map<String, Object> requestMap) {
        StringBuilder builder = new StringBuilder();
        List<Object> list = new ArrayList<>();
        System.out.println(requestMap.toString());

        builder.append("select top 500 PPA.code,PPb.origTaxAmount,ppb.quantity,ppb.origTaxPrice,PPA.voucherdate from PU_PurchaseArrival PPA ");
        builder.append(" left join PU_PurchaseArrival_b PPB on ppa.id = PPB.idPurchaseArrivalDTO");
        builder.append(" left join AA_Partner AA on PPA.IdPartner = AA.ID where AA.name =?");
        list.add(requestMap.get("name"));
        List<Map<String, Object>> maps = jdbcTemplate.queryForList(builder.toString(), list.toArray());
        return maps;
    }*/

    public Object getAccountspayable(Map<String, String> requestMap, String explan) {
        Map<String, Object> result = new HashMap<>();
        result.put("customerName", requestMap.get("customerName"));
        result.put("queryStartDate", requestMap.get("startDate"));
        result.put("queryEndDate", requestMap.get("endDate"));
        result.put("explan", explan);
        //应付列表
        List<Map<String, Object>> mapList = this.getAccountspayableList(requestMap, explan);
        //应付前期结转
        Map<String, Object> accountspayableCount = this.getAccountspayableCount(requestMap, explan);
        BigDecimal initInvoiceBalance = new BigDecimal(accountspayableCount.get("initPaymentBalance") == null ?
                "0" : accountspayableCount.get("initPaymentBalance").toString());//还应付款
        BigDecimal initPaymentBalance = new BigDecimal(accountspayableCount.get("initInvoiceBalance") == null ?
                "0" : accountspayableCount.get("initInvoiceBalance").toString());//还应开票

        result.put("initInvoiceBalance", initInvoiceBalance);
        result.put("initPaymentBalance", initPaymentBalance);
        BigDecimal deliverTotalAmount = new BigDecimal(0);
        BigDecimal invoiceBalanceTotalAmount = new BigDecimal(0);
        BigDecimal collectTotalAmount = new BigDecimal(0);
        BigDecimal receivableTotalAmount = new BigDecimal(0);
        BigDecimal invoiceTotalAmount = new BigDecimal(0);
        if (mapList != null && mapList.size() > 0) {
            for (Map<String, Object> map : mapList) {
                String type = map.get("type").toString();
                BigDecimal amount = new BigDecimal(map.get("Amount").toString());
                //
                map.put("voucherdate", map.get("voucherdate").toString().substring(0, 10));
                switch (type) {
                    case "1":
                        initPaymentBalance = initPaymentBalance.add(amount);
                        initInvoiceBalance = initInvoiceBalance.add(amount);
                        collectTotalAmount = collectTotalAmount.add(amount);
                        break;
                    case "2":
                        initPaymentBalance = initPaymentBalance.subtract(amount);
                        receivableTotalAmount = receivableTotalAmount.add(amount);
                        break;
                    case "3":
                        initInvoiceBalance = initInvoiceBalance.subtract(amount);
                        invoiceTotalAmount = invoiceTotalAmount.add(amount);
                        break;
                    default:
                        break;
                }
                map.put("invoiceBalanceAmount", initInvoiceBalance);
                map.put("receivableBalance", initPaymentBalance);
            }

            deliverTotalAmount = (BigDecimal) mapList.get(mapList.size() - 1).get("receivableBalance");
            invoiceBalanceTotalAmount = (BigDecimal) mapList.get(mapList.size() - 1).get("invoiceBalanceAmount");
        } else {
            deliverTotalAmount = initPaymentBalance;
            invoiceBalanceTotalAmount = initInvoiceBalance;
        }
        result.put("deliverTotalAmount", deliverTotalAmount);
        result.put("invoiceBalanceTotalAmount", invoiceBalanceTotalAmount);
        result.put("collectTotalAmount", collectTotalAmount);
        result.put("receivableTotalAmount", receivableTotalAmount);
        result.put("invoiceTotalAmount", invoiceTotalAmount);
        result.put("arrDetail", mapList);
        return result;
    }

    /*private List<Map<String, Object>> getAccountspayableList(Map<String, String> requestMap, String explan) {
        StringBuilder sb = new StringBuilder();
        ArrayList<Object> list = new ArrayList<>();
        sb.append(" select ss.* from (");
        sb.append(" select code,voucherdate,OrigTotalTaxAmount Amount,'1' type,IdPartner,");
        sb.append(" (case pubuserdefnvc1 when '平台直发' then '线上' when '线上' then '线上' else '线下' END) pubuserdefnvc1 from PU_PurchaseArrival where voucherState =189 and voucherdate >='2018/12/28 00:00:00'");
        sb.append(" union all select ppi.purchaseInvoiceNo code,ppi.voucherdate,ppi.totalTaxAmount Amount,'2' type,ppi.IdPartner,");
        sb.append(" (case ppa.pubuserdefnvc1 when '平台直发' then '线上' when '线上' then '线上' else '线下' END) pubuserdefnvc1 from pu_PurchaseInvoice ppi");
        sb.append(" left join PU_PurchaseArrival ppa on ppi.sourceVoucherCode = ppa.code where ppi.voucherState =189");
        sb.append(" union all select code,voucherdate,Amount,'3' type,IdPartner,'线下' pubuserdefnvc1 from ARAP_ReceivePayment where idbusitype =80");
        sb.append(" ) ss left join AA_Partner AA on ss.IdPartner = AA.ID where 1=1 and AA.name =? and pubuserdefnvc1 = ?");
        list.add(requestMap.get("customerName"));
        list.add(explan);
        sb.append(" and voucherdate >='"+requestMap.get("startDate")+"'");
        sb.append(" and voucherdate <='"+requestMap.get("endDate")+"'");
        sb.append( " order by voucherdate");
        return  jdbcTemplate.queryForList(sb.toString(), list.toArray());
    }

    private Map<String, Object> getAccountspayableCount(Map<String, String> requestMap, String explan) {
        StringBuilder sb = new StringBuilder();
        ArrayList<Object> list = new ArrayList<>();
        sb.append(" select sum(case type when '0' then Amount when '1' then Amount when '3' then -Amount end) initPaymentBalance,");
        sb.append(" sum(case type when '0' then Amount when '1' then Amount when '2' then -Amount end) initInvoiceBalance from (");
        sb.append(" select code,origDate voucherdate,Amount,'0' type,IdPartner,'线下' pubuserdefnvc1 from ARAP_OriginalAmount_ApDetail UNION ALL");
        sb.append(" select code,voucherdate,OrigTotalTaxAmount Amount,'1' type,IdPartner,");
        sb.append(" (case pubuserdefnvc1 when '平台直发' then '线上' when '线上' then '线上' else '线下' END) pubuserdefnvc1 from PU_PurchaseArrival where voucherState =189 and voucherdate >='2018/12/28 00:00:00'");
        sb.append(" union all select ppi.purchaseInvoiceNo code,ppi.voucherdate,ppi.totalTaxAmount Amount,'2' type,ppi.IdPartner,");
        sb.append(" (case ppa.pubuserdefnvc1 when '平台直发' then '线上' when '线上' then '线上' else '线下' END) pubuserdefnvc1 from pu_PurchaseInvoice ppi");
        sb.append(" left join PU_PurchaseArrival ppa on ppi.sourceVoucherCode = ppa.code where ppi.voucherState =189");
        sb.append(" union all select code,voucherdate,Amount,'3' type,IdPartner,'线下' pubuserdefnvc1 from ARAP_ReceivePayment where idbusitype =80");
        sb.append(" ) ss left join AA_Partner AA on ss.IdPartner = AA.ID where 1=1 and AA.name =? and pubuserdefnvc1 = ?");
        list.add(requestMap.get("customerName"));
        list.add(explan);
        sb.append(" and voucherdate <'"+requestMap.get("startDate")+"'");
        System.out.println(sb.toString());
        return  jdbcTemplate.queryForMap(sb.toString(), list.toArray());
    }*/
    private List<Map<String, Object>> getAccountspayableList(Map<String, String> requestMap, String explan) {
        StringBuilder sb = new StringBuilder();
        ArrayList<Object> list = new ArrayList<>();
        sb.append(" select ss.* from (");
        sb.append(" select code,voucherdate,OrigTotalTaxAmount Amount,'1' type,IdPartner,");
        sb.append(" '线上' pubuserdefnvc1 from PU_PurchaseArrival where voucherState =189 and voucherdate >='2018/12/28 00:00:00'");
        sb.append(" union all select ppi.purchaseInvoiceNo code,ppi.voucherdate,ppi.totalTaxAmount Amount,'2' type,ppi.IdPartner,");
        sb.append(" '线上' pubuserdefnvc1 from pu_PurchaseInvoice ppi");
        sb.append(" left join PU_PurchaseArrival ppa on ppi.sourceVoucherCode = ppa.code where ppi.voucherState =189");
        sb.append(" union all select code,voucherdate,Amount,'3' type,IdPartner,'线上' pubuserdefnvc1 from ARAP_ReceivePayment where idbusitype =80");
        sb.append(" ) ss left join AA_Partner AA on ss.IdPartner = AA.ID where 1=1 and AA.name =? and pubuserdefnvc1 = ?");
        list.add(requestMap.get("customerName"));
        list.add(explan);
        sb.append(" and voucherdate >='" + requestMap.get("startDate") + "'");
        sb.append(" and voucherdate <='" + requestMap.get("endDate") + "'");
        sb.append(" order by voucherdate");
        return jdbcTemplate.queryForList(sb.toString(), list.toArray());
    }

    private Map<String, Object> getAccountspayableCount(Map<String, String> requestMap, String explan) {
        StringBuilder sb = new StringBuilder();
        ArrayList<Object> list = new ArrayList<>();
        sb.append(" select sum(case type when '0' then Amount when '1' then Amount when '3' then -Amount end) initPaymentBalance,");
        sb.append(" sum(case type when '0' then Amount when '1' then Amount when '2' then -Amount end) initInvoiceBalance from (");
        sb.append(" select code,origDate voucherdate,Amount,'0' type,IdPartner,'线上' pubuserdefnvc1 from ARAP_OriginalAmount_ApDetail UNION ALL");
        sb.append(" select code,voucherdate,OrigTotalTaxAmount Amount,'1' type,IdPartner,");
        sb.append(" '线上' pubuserdefnvc1 from PU_PurchaseArrival where voucherState =189 and voucherdate >='2018/12/28 00:00:00'");
        sb.append(" union all select ppi.purchaseInvoiceNo code,ppi.voucherdate,ppi.totalTaxAmount Amount,'2' type,ppi.IdPartner,");
        sb.append(" '线上' pubuserdefnvc1 from pu_PurchaseInvoice ppi");
        sb.append(" left join PU_PurchaseArrival ppa on ppi.sourceVoucherCode = ppa.code where ppi.voucherState =189");
        sb.append(" union all select code,voucherdate,Amount,'3' type,IdPartner,'线上' pubuserdefnvc1 from ARAP_ReceivePayment where idbusitype =80");
        sb.append(" ) ss left join AA_Partner AA on ss.IdPartner = AA.ID where 1=1 and AA.name =? and pubuserdefnvc1 = ?");
        list.add(requestMap.get("customerName"));
        list.add(explan);
        sb.append(" and voucherdate <'" + requestMap.get("startDate") + "'");
        return jdbcTemplate.queryForMap(sb.toString(), list.toArray());
    }

    public void downloadOverdueCredit(HttpServletResponse httpServletResponse) {
        Connection con = null;
        ResultSet rs = null;
        try {
            con = dataSource.getConnection();
            CallableStatement c = con.prepareCall("{call PROC_settlement_sales_months}");
            rs = c.executeQuery();

            ResultSetMetaData rsmd = rs.getMetaData();
            // 数据列数
            int count = rsmd.getColumnCount();

            // 列名数据
            ArrayList<String> columnsList = new ArrayList<>();
            // 移除前3列（关联id列、总发货、总收款）
            for (int i = 4; i < count; i++) {
                columnsList.add(rsmd.getColumnLabel(i));
                if (i > 11) {
                    i++;
                }
            }

            // 数据
            ArrayList<List<Object>> rowsList = new ArrayList<>();
            // 需要合并计算的用户序号List，根据code编码判断
            List<Integer> totalIndexList = new ArrayList<>();
            // 需要合并的序号List集合
            List<List<Integer>> totalList = new ArrayList<>();
            // 关联code
            String parentCode = "";
            // 有关联账号标记
            boolean hasParentCode;
            // 关联账户总计应收
            BigDecimal totalReceivables = BigDecimal.ZERO;

            while (rs.next()) {
                ArrayList<Object> dataList = new ArrayList<>();
                // 账期月, 目前rs第7列
                int month = rs.getInt(7);
                // 账期日，目前rs第8列
                int day = rs.getInt(8);
                // 应减去的结算周期数
                int overdueMonths = CommonUtils.overdueMonth(month, day);
                // 当前逾期金额
                BigDecimal overdue = rs.getBigDecimal(10);

                // 设置数据行，移除前3列（关联id列、总发货、总收款）
                for (int i = 4; i <= count; i++) {
                    if (i > 11) {  // 计算每个周期的发货和应收
                        if (i > count - overdueMonths * 2) {
                            // 有关联的账期客户逾期总金额不计算未到账期的退货金额，无关联关系的账期客户预期总金额计算所有退货金额
                            // 分月统计全部计算所有退货金额
                            // 发货金额，未到账期均不计算
                            overdue = overdue.subtract(rs.getBigDecimal(i));
                            // 只计算逾期账期数据，如果是未逾期账期数据，需要将逾期款减去相应的发货金额
                            BigDecimal tempOverdue = BigDecimal.ZERO;

                            if ("0".equals(rs.getString("parent_code"))) {
                                tempOverdue = new BigDecimal(dataList.get(6).toString()).subtract(rs.getBigDecimal(i++));
                                dataList.set(6, tempOverdue);
                            } else {
                                // 有关联账户不计算未到期的退货
                                tempOverdue = new BigDecimal(dataList.get(6).toString()).subtract(rs.getBigDecimal(i++));
                                tempOverdue = tempOverdue.subtract(rs.getBigDecimal(i));
                                dataList.set(6, tempOverdue);
                            }
                            dataList.add(0);
                        } else {
                            dataList.add(rs.getBigDecimal(i++));
                        }
                    } else if (i == 11) {
                        dataList.add(rs.getBigDecimal(i));
                    } else {
                        dataList.add(rs.getString(i));
                    }
                }

                // 根据逾期款，设置excel数据。从后向前，到期初为止。
                for (int index = dataList.size() - 1; index > 6; index--) {
                    if (overdue.compareTo(BigDecimal.ZERO) < 1) {  // 逾期金额小于等于0，所有账期逾期金额都是0
                        dataList.set(index, 0);
                    } else {  // 逾期金额大于0，从最后一个开始分摊逾期金额
                        if (overdue.compareTo(new BigDecimal(dataList.get(index).toString())) > -1) {
                            overdue = overdue.subtract(new BigDecimal(dataList.get(index).toString()));
                            dataList.set(index, dataList.get(index));
                        } else {
                            dataList.set(index, overdue);
                            overdue = BigDecimal.ZERO;
                        }
                    }
                }

                // 补零数量
                // int overdueZero = CommonUtils.overdueZero(month, day);
                // 导出的Excel显示逾期金额，不是发货金额。需要按照账期周期，向后推迟逾期金额，在期初之后补0实现。
                for (int overdueIndex = 0; overdueIndex < month; overdueIndex++) {
                    // 插入0
                    dataList.add(8, 0);
                    // 删除最后一位
                    dataList.remove(dataList.size() - 1);
                }

                // 设置数据列
                rowsList.add(dataList);

                // 设置逾期金额总计
                // 如果存在关联code
                if (!"0".equals(rs.getString("parent_code"))) {
                    hasParentCode = true;
                    // 如果两个不相等，说明是新的关联code，重新赋值
                    if (!parentCode.equals(rs.getString("parent_code"))) {
                        parentCode = rs.getString("parent_code");
                        // 此处修改的目的是防止单元格合并之后，数值求和计算错误。
                        if (!totalIndexList.isEmpty()) {
                            rowsList.get(totalIndexList.get(0) - 1).set(5, totalReceivables);
                        }
                        // 置零
                        totalReceivables = BigDecimal.ZERO;
                        // 添加至集合
                        if (!totalIndexList.isEmpty()) {
                            totalList.add(totalIndexList);
                        }
                        // 序列list置空
                        totalIndexList = new ArrayList<>();
                    }
                } else {
                    // 最后一个totalIndexList
                    if (!totalIndexList.isEmpty()) {
                        // 计算之前应收之和
                        for (int index : totalIndexList) {
                            rowsList.get(index - 1).set(5, totalReceivables);
                        }
                        // 添加至集合
                        totalList.add(totalIndexList);
                        totalIndexList = new ArrayList<>();
                    } else {
                        // 如果没有关联客户，将逾期金额赋值到总逾期金额
                        rowsList.get(rs.getRow() - 1).set(5, rowsList.get(rs.getRow() - 1).get(6));
                    }
                    // 置零
                    hasParentCode = false;
                    parentCode = "";
                    totalReceivables = BigDecimal.ZERO;
                }

                // 设置应收金额合计
                if (hasParentCode) {
                    totalIndexList.add(rs.getRow());
                    totalReceivables = totalReceivables.add(new BigDecimal(dataList.get(6).toString()));
                }

            }

            // 导出excel
            exportOverdue(httpServletResponse, columnsList, rowsList, "账期客户逾期统计", totalList);

        } catch (SQLException | IOException e) {
            log.error("下载账期客户逾期统计异常：{}", e);
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 查询全部逾期数据
     *
     * @param partner
     * @return
     */
    public Map<String, Object> findOverdueAllData(OverdueDTO partner) {
        // 列名
        List<String> columnsList = findOverdueAllColumns().get("columns");
        // 数据List
        List<Object[]> resultDataList = findOverdueAll(partner);
        // 数据
        List<Map<String, Object>> rowsMapList = new ArrayList<>();
        // 数据
        ArrayList<List<Object>> rowsList = new ArrayList<>();
        // 为了通用导出和页面格式，回传两个数据格式，后续可以优化
        Map<String, Object> resultMap = new HashMap<>();

        for (Object[] dataRow : resultDataList) {
            Map<String, Object> dataMap = new HashMap<>();
            ArrayList<Object> dataList = new ArrayList<>();
            // 账期月, 目前rs第8列
            int month = Integer.parseInt(dataRow[8].toString());
            // 账期日，目前rs第9列
            int day = 0;
            if (StringUtils.isNumeric(dataRow[9].toString())) {
                day = Integer.parseInt(dataRow[9].toString());
            }
            // 应减去的结算周期数
            int overdueMonths = CommonUtils.overdueMonth(month, day);
            // 当前逾期金额
            BigDecimal overdue = new BigDecimal(dataRow[12].toString());
            // 设置数据行，移除前4列（仓库用户标记、关联id列、总发货、总收款）
            for (int i = 4; i < dataRow.length; i++) {
                if (i > 13) {  // 计算每个周期的发货和应收
                    if (i >= dataRow.length - overdueMonths * 2) {
                        /**
                         * 20191206修改：
                         * 1. 预期总额等于各月逾期金额之和。
                         * 2. 仓库特殊用户，退款金额不抵扣之前的欠款，只计算当月。
                         *
                         * 之前的规则：
                         * 有关联的账期客户逾期总金额不计算未到账期的退货金额，无关联关系的账期客户预期总金额计算所有退货金额
                         * 分月统计全部计算所有退货金额
                         */
                        // 发货金额，未到账期均不计算
                        overdue = overdue.subtract(new BigDecimal(dataRow[i].toString()));
                        // 只计算逾期账期数据，如果是未逾期账期数据，需要将逾期款减去相应的发货金额
                        BigDecimal tempOverdue;
                        // 1-仓库特殊用户，退货金额只计算在当月，不计算未到期的退货金额
                        if ("1".equals(dataRow[0])) {
                            tempOverdue = new BigDecimal(dataList.get(8).toString()).subtract(new BigDecimal(dataRow[i++].toString()));
                            dataList.set(8, tempOverdue);
                        } else {
                            // 有关联账户不计算未到期的退货
                            tempOverdue = new BigDecimal(dataList.get(8).toString()).subtract(new BigDecimal(dataRow[i++].toString()));
                            tempOverdue = tempOverdue.subtract(new BigDecimal(dataRow[i].toString()));
                            dataList.set(8, tempOverdue);
                        }
                        dataList.add(0);
                    } else {
                        dataList.add(new BigDecimal(dataRow[i++].toString()));
                    }
                } else if (i > 10) {
                    dataList.add(new BigDecimal(dataRow[i].toString()));
                } else {
                    dataList.add(dataRow[i].toString());
                }
            }

            overdue = new BigDecimal(dataList.get(8).toString());

            // 根据逾期款，设置excel数据。从后向前，到期初为止。
            for (int index = dataList.size() - 1; index > 8; index--) {
                if (overdue.compareTo(BigDecimal.ZERO) < 1) {  // 逾期金额小于等于0，最后一个开始分摊
                    // dataList.set(index, 0);

                    if (overdue.compareTo(new BigDecimal(dataList.get(index).toString())) > -1) {
                        overdue = overdue.subtract(new BigDecimal(dataList.get(index).toString()));
                        dataList.set(index, dataList.get(index));
                    } else {
                        dataList.set(index, overdue);
                        overdue = BigDecimal.ZERO;
                    }


                } else {  // 逾期金额大于0，从最后一个开始分摊逾期金额
                    if (overdue.compareTo(new BigDecimal(dataList.get(index).toString())) > -1) {
                        overdue = overdue.subtract(new BigDecimal(dataList.get(index).toString()));
                        dataList.set(index, dataList.get(index));
                    } else {
                        dataList.set(index, overdue);
                        overdue = BigDecimal.ZERO;
                    }
                }
            }

            // 导出的Excel显示逾期金额，不是发货金额。需要按照账期周期，向后推迟逾期金额，在期初之后补0实现。
            for (int overdueIndex = 0; overdueIndex < month; overdueIndex++) {
                // 插入0
                dataList.add(9, 0);
                // 删除最后一位
                dataList.remove(dataList.size() - 1);
            }

            // 设置数据列
            for (int index = 0; index < columnsList.size(); index++) {
                dataMap.put(columnsList.get(index), dataList.get(index));
            }

            rowsMapList.add(dataMap);

            if (Double.parseDouble(dataList.get(7).toString()) == 0.0 && Double.parseDouble(dataList.get(8).toString()) == 0.0) {
                continue;
            }

            rowsList.add(dataList);
        }

        resultMap.put("map", rowsMapList);
        resultMap.put("list", rowsList);

        return resultMap;
    }

    /**
     * 导出账期逾期客户Excel
     * 牵涉到单元格合并和特殊的表结构，单独一个方法实现。
     *
     * @param response       HttpServletResponse
     * @param columnNameList 导出列名List
     * @param dataList       导出数据List
     * @param fileName       导出文件名，目前sheet页是相同名称
     * @param totalList      需要合并的数据序号List
     * @throws IOException @Description
     */
    private void exportOverdue(HttpServletResponse response, List<String> columnNameList, List<List<Object>> dataList, String fileName, List<List<Integer>> totalList) throws IOException {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");

        Sheet sheet1 = new Sheet(1, 0);
        sheet1.setSheetName(fileName);
        sheet1.setAutoWidth(Boolean.TRUE);

        fileName = fileName + df.format(new Date());
        ServletOutputStream out = response.getOutputStream();
        response.setContentType("multipart/form-data");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename*= UTF-8''" + URLEncoder.encode(fileName, "UTF-8") + ".xlsx");
        // 设置列名
        if (columnNameList != null) {
            List<List<String>> list = new ArrayList<>();
            columnNameList.forEach(c -> list.add(Collections.singletonList(c)));
            sheet1.setHead(list);
        }
        // 写入数据
        ExcelWriter writer = new ExcelWriter(out, ExcelTypeEnum.XLSX, true);
        writer.write1(dataList, sheet1);
        // 合并单元格
        for (List<Integer> totalIndexList : totalList) {
            if (!totalIndexList.isEmpty()) {
                if (!totalIndexList.get(0).equals(totalIndexList.get(totalIndexList.size() - 1))) {
                    writer.merge(totalIndexList.get(0), totalIndexList.get(totalIndexList.size() - 1), 5, 5);
                }
            }
        }

        writer.finish();
        out.flush();
        out.close();

    }

    /**
     * 导出用友对账单Excel公用方法
     *
     * @param period 账期，格式：yyyy-MM
     * @param name   对账单位名称
     * @return 对账单信息EnumMap
     */
    public EnumMap<ExcelPropertyEnum, Object> getYonyouStatementExcel(String period, String name) {
        // 判断参数
        if (StringUtils.isBlank(period) || StringUtils.isBlank(name)) {
            return null;
        }
        // 开始时间
        String startDate;
        // 结束时间
        String endDate;
        // 分割时间
        String[] dateArray = period.split("-");
        // 拼接时间，上个月的28日，到这个月的27日
        if (dateArray.length == 2) {
            if (CommonUtils.isNumber(dateArray[0]) && CommonUtils.isNumber(dateArray[1])) {
                if (Integer.parseInt(dateArray[1]) > 1 && Integer.parseInt(dateArray[1]) <= 12) {
                    startDate = dateArray[0] + "-" + (Integer.parseInt(dateArray[1]) - 1) + "-28";
                    endDate = period + "-27";
                } else if (Integer.parseInt(dateArray[1]) == 1) {
                    startDate = (Integer.parseInt(dateArray[0]) - 1) + "-12-28";
                    endDate = period + "-27";
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }

        // 比较结束日期，如果大于今天，显示今天。
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date date = null;
        try {
            date = sdf.parse(endDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        if (date.after(new Date())) {
            endDate = sdf.format(new Date());
        }

        // 调用接口获取对账单数据
        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>();
        multiValueMap.add("startDate", startDate);
        multiValueMap.add("endDate", endDate);
        multiValueMap.add("settleCustomer", name);
        ResponseEntity responseEntity = CommonUtils.sendPostRequest(url, multiValueMap);
        if (responseEntity.getBody() == null) {
            return null;
        }
        // 格式化JSONArray
        JSONArray dataJSONArray = JSONArray.parseArray("[" + responseEntity.getBody() + "]");
        // 每行数据List
        List<Object> dataList = new ArrayList<>();
        // 总行数据List
        List<List<Object>> rowList = new ArrayList<>();
        // 对账单明细行数据List
        List<List<Object>> totalRowList = new ArrayList<>();
        // 需要加粗显示行号List
        List<Integer> boldList = new ArrayList<>();
        // 需要加边框行号List
        List<Integer> borderList = new ArrayList<>();
        // 背景色行
        List<Integer> backgroundColorList = new ArrayList<>();
        // 居中行
        List<Integer> centerList = new ArrayList<>();
        // 明细居中行
        List<Integer> centerDetailList = new ArrayList<>();
        // 需要合并的行
        List<Integer> mergeRowNumList = new ArrayList<>();
        // 处理数据
        if (dataJSONArray.size() > 0) {
            BigDecimal deliverTotal = BigDecimal.ZERO;
            BigDecimal collectTotal = BigDecimal.ZERO;
            BigDecimal receivableTotal = BigDecimal.ZERO;
            BigDecimal invoiceTotal = BigDecimal.ZERO;
            BigDecimal invoiceBalanceTotal = BigDecimal.ZERO;
            // 第一行，结算客户信息
            dataList.add(dataJSONArray.getJSONObject(0).getString("settleCustomer"));
            dataList.add("");
            dataList.add("");
            dataList.add(dataJSONArray.getJSONObject(0).getString("settleCustomerTel"));
            dataList.add("");
            dataList.add(dataJSONArray.getJSONObject(0).getString("settleCustomerFax"));
            dataList.add("");
            rowList.add(dataList);
            boldList.add(rowList.size());
            // 第二行，公司信息
            dataList = new ArrayList<>();
            dataList.add(dataJSONArray.getJSONObject(0).getString("company"));
            dataList.add("");
            dataList.add("");
            dataList.add(dataJSONArray.getJSONObject(0).getString("companyTel"));
            dataList.add("");
            dataList.add(dataJSONArray.getJSONObject(0).getString("companyFax"));
            dataList.add("");
            rowList.add(dataList);
            boldList.add(rowList.size());
            // 第三行，空白
            dataList = new ArrayList<>();
            rowList.add(dataList);
            // 线上、线下
            for (int index = 0; index < dataJSONArray.getJSONObject(0).getJSONArray("reportContent").size(); index++) {
                // 线上、线下标题
                dataList = new ArrayList<>();
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getString("settleCustomer") + " - " + dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getString("explan"));
                rowList.add(dataList);
                boldList.add(rowList.size());
                centerList.add(rowList.size());
                // 时间行
                dataList = new ArrayList<>();
                dataList.add("日期：" + dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getString("queryStartDate") + " _ " + dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getString("queryEndDate"));
                rowList.add(dataList);
                boldList.add(rowList.size());
                // 明细标题行
                dataList = new ArrayList<>();
                dataList.add("日期");
                dataList.add("合同编号");
                dataList.add("类别");
                dataList.add("发货金额");
                dataList.add("收款金额");
                dataList.add("应收款");
                dataList.add("开票金额");
                dataList.add("发票结余");
                rowList.add(dataList);
                boldList.add(rowList.size());
                borderList.add(rowList.size());
                backgroundColorList.add(rowList.size());
                centerList.add(rowList.size());
                // 期初数据行
                dataList = new ArrayList<>();
                dataList.add("线上期初数据");
                dataList.add("上期结转：");
                dataList.add("");
                dataList.add("");
                dataList.add("");
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("initReceivableBanlance"));
                dataList.add("");
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("initInvoiceBanlance"));
                rowList.add(dataList);
                borderList.add(rowList.size());
                centerDetailList.add(rowList.size());
                // 明细
                for (int innerIndex = 0; innerIndex < dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").size(); innerIndex++) {
                    dataList = new ArrayList<>();
                    dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getString("bookedDate"));
                    dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getString("summary"));
                    dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getString("category"));
                    dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getBigDecimal("deliverAmount").compareTo(BigDecimal.ZERO) == 0 ? "" : dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getBigDecimal("deliverAmount"));
                    dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getBigDecimal("collectAmount").compareTo(BigDecimal.ZERO) == 0 ? "" : dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getBigDecimal("collectAmount"));
                    dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getBigDecimal("receivableAmount").compareTo(BigDecimal.ZERO) == 0 ? "" : dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getBigDecimal("receivableAmount"));
                    dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getBigDecimal("invoiceAmount").compareTo(BigDecimal.ZERO) == 0 ? "" : dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getBigDecimal("invoiceAmount"));
                    dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getJSONArray("arrDetail").getJSONObject(innerIndex).getBigDecimal("invoiceBalanceAmount"));
                    rowList.add(dataList);
                    borderList.add(rowList.size());
                    centerDetailList.add(rowList.size());
                }
                // 汇总信息
                dataList = new ArrayList<>();
                dataList.add("本月" + dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getString("explan") + "结算");
                dataList.add("");
                dataList.add("");
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("deliverTotalAmount"));
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("collectTotalAmount"));
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("receivableTotalAmount"));
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("invoiceTotalAmount"));
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("invoiceBalanceTotalAmount"));
                rowList.add(dataList);
                borderList.add(rowList.size());
                boldList.add(rowList.size());
                mergeRowNumList.add(rowList.size());
                centerList.add(rowList.size());
                backgroundColorList.add(rowList.size());
                // 备注行
                dataList = new ArrayList<>();
                dataList.add("备注：本月" + dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getString("explan") + "销售、收款、开票如上表所示");
                rowList.add(dataList);
                boldList.add(rowList.size());
                // 空白行
                dataList = new ArrayList<>();
                rowList.add(dataList);
                // 本月信息行
                dataList = new ArrayList<>();
                dataList.add("本月小计 - " + dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getString("explan") + "：");
                dataList.add("");
                dataList.add("");
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("deliverTotalAmount"));
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("collectTotalAmount"));
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("receivableTotalAmount"));
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("invoiceTotalAmount"));
                dataList.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("invoiceBalanceTotalAmount"));
                totalRowList.add(dataList);

                deliverTotal = deliverTotal.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("deliverTotalAmount"));
                collectTotal = collectTotal.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("collectTotalAmount"));
                receivableTotal = receivableTotal.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("receivableTotalAmount"));
                invoiceTotal = invoiceTotal.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("invoiceTotalAmount"));
                invoiceBalanceTotal = invoiceBalanceTotal.add(dataJSONArray.getJSONObject(0).getJSONArray("reportContent").getJSONObject(index).getBigDecimal("invoiceBalanceTotalAmount"));
            }
            // 月汇总
            dataList = new ArrayList<>();
            dataList.add("综上所述，本月汇总如下");
            rowList.add(dataList);
            boldList.add(rowList.size());
            centerList.add(rowList.size());
            // 月汇总标题行
            dataList = new ArrayList<>();
            dataList.add("合计");
            dataList.add("");
            dataList.add("");
            dataList.add("发货金额");
            dataList.add("收款金额");
            dataList.add("应收款");
            dataList.add("开票金额");
            dataList.add("发票结余");
            rowList.add(dataList);
            boldList.add(rowList.size());
            borderList.add(rowList.size());
            centerList.add(rowList.size());
            backgroundColorList.add(rowList.size());
            // 线上、线下汇总
            rowList.addAll(totalRowList);
            boldList.add(rowList.size());
            borderList.add(rowList.size());
            boldList.add(rowList.size() - 1);
            borderList.add(rowList.size() - 1);
            centerList.add(rowList.size());
            centerList.add(rowList.size() - 1);
            // 月累计行
            dataList = new ArrayList<>();
            dataList.add("本月累计：");
            dataList.add("");
            dataList.add("");
            dataList.add(deliverTotal);
            dataList.add(collectTotal);
            dataList.add(receivableTotal);
            dataList.add(invoiceTotal);
            dataList.add(invoiceBalanceTotal);
            rowList.add(dataList);
            boldList.add(rowList.size());
            borderList.add(rowList.size());
            centerList.add(rowList.size());
            backgroundColorList.add(rowList.size());
            // 其他信息行
            dataList = new ArrayList<>();
            dataList.add("1、此对账单的截止日期为上述发出日期。");
            rowList.add(dataList);
            boldList.add(rowList.size());

            dataList = new ArrayList<>();
            dataList.add("2、如有错漏，请于发出此对账单后七日內提出，否则视为默认！");
            rowList.add(dataList);
            boldList.add(rowList.size());

            dataList = new ArrayList<>();
            dataList.add("制单：");
            dataList.add("");
            dataList.add("业务员确认：");
            dataList.add("");
            dataList.add("");
            dataList.add("发出日期：");
            dataList.add("");
            dataList.add("");
            rowList.add(dataList);
            boldList.add(rowList.size());

            dataList = new ArrayList<>();
            rowList.add(dataList);

            dataList = new ArrayList<>();
            dataList.add("客户签字：");
            rowList.add(dataList);
            boldList.add(rowList.size());

            dataList = new ArrayList<>();
            dataList.add("客户盖章：");
            rowList.add(dataList);
            boldList.add(rowList.size());
        } else {
            return null;
        }
        // 名称
        String fileName = name;
        // sheet页
        Sheet sheet1 = new Sheet(1, 0);
        sheet1.setSheetName(fileName);
        sheet1.setAutoWidth(Boolean.TRUE);
        // 样式
        List<Integer> spacialBackgroundColorList = new ArrayList<>();
        if (!backgroundColorList.isEmpty()) {
            spacialBackgroundColorList.add(mergeRowNumList.get(mergeRowNumList.size() - 1) + 5);
            spacialBackgroundColorList.add(mergeRowNumList.get(mergeRowNumList.size() - 1) + 6);
        }
        StyleExcelHandler handler = new StyleExcelHandler(boldList, borderList, backgroundColorList, centerList, spacialBackgroundColorList, centerDetailList);
        // 返回值
        EnumMap<ExcelPropertyEnum, Object> reusltEnumMap = new EnumMap<>(ExcelPropertyEnum.class);
        reusltEnumMap.put(ExcelPropertyEnum.HANDLER, handler);
        reusltEnumMap.put(ExcelPropertyEnum.ROWLIST, rowList);
        reusltEnumMap.put(ExcelPropertyEnum.SHEET, sheet1);
        reusltEnumMap.put(ExcelPropertyEnum.FILENAME, fileName + "(" + startDate + "_" + endDate + ")");
        reusltEnumMap.put(ExcelPropertyEnum.MERGE, mergeRowNumList);

        return reusltEnumMap;
    }

}
