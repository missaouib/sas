package com.js.sas.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SalesPerformanceService {

    @Autowired
    @Qualifier(value = "secodJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    /**
     * 查询业务员业绩列表
     *
     * @param params
     * @return
     */
    public List<Map<String, Object>> getPage(Map<String, String> params) {
        if (params != null) {
            if (params.get("limit") == null || params.get("offset") == null) {
                return null;
            }
            List<Object> list = new ArrayList<>();
            StringBuilder builder = new StringBuilder(" SELECT *, CASE WHEN 下单月份 <= 6 THEN '1%' WHEN 下单月份 <= 12 THEN '0.5%' ELSE '0%' END AS 比例, " +
                    " CASE WHEN 下单月份 <= 6 THEN 0.01*订单总金额 WHEN 下单月份 <= 12 THEN 0.005*订单总金额 ELSE  0 END AS 业绩额 FROM ");
            builder.append(" (SELECT os.waysalesman AS \"业务员\",os.orderno AS \"订单号\",os.createtime AS \"下单时间\",os.membername AS \"买家账号\"," +
                    " bci.companyname AS \"公司名称\",os.totalprice AS \"订单总金额\",months_between ( " +
                    " (SELECT fot.firsttime FROM (SELECT MIN (os.createtime) AS firsttime,os.memberid FROM orders os GROUP BY os.memberid ) fot" +
                    " WHERE fot.memberid = os.memberid ) :: DATE, os.createtime :: DATE ) + 1 AS \"下单月份\" FROM orders os " +
                    " LEFT JOIN buyercompanyinfo bci ON bci.memberid = os.memberid WHERE 1=1 ");
            if (params.containsKey("startDate")) {
                builder.append(" and os.createtime >='" + params.get("startDate") + "'");
            }
            if (params.containsKey("endDate")) {
                builder.append(" and os.createtime <='" + params.get("endDate") + "'");
            }

            if (params.get("waysalesman") != null && StringUtils.isNotBlank(params.get("waysalesman"))) {
                builder.append(" and os.waysalesman = ?");
                list.add(params.get("waysalesman").replace(" ", ""));
            }
            if (params.get("membername") != null && StringUtils.isNotBlank(params.get("membername"))) {
                builder.append(" and os.membername = ?");
                list.add(params.get("membername").replace(" ", ""));
            }
            if (params.get("companyname") != null && StringUtils.isNotBlank(params.get("companyname"))) {
                builder.append(" and bci.companyname = ?");
                list.add(params.get("companyname").replace(" ", ""));
            }
            //订单状态0=待付款 1=待发货 3=待收货 4=待验货 5=已完成 7=已关闭 8=备货中 9=备货完成 10=部分发货
            builder.append(" AND os.orderstatus IN (1, 3, 4, 5, 8, 10)");
            builder.append(" ORDER BY os.createtime desc) AS T limit ? OFFSET ?;");
            list.add(Long.parseLong(params.get("limit")));
            list.add(Integer.parseInt(params.get("offset")));
            return jdbcTemplate.queryForList(builder.toString(), list.toArray());

        } else {
            return null;
        }

    }

    /**
     * 查询总数
     *
     * @param params
     * @return
     */
    public Long getCount(Map<String, String> params) {
        if (params != null) {

            List<Object> list = new ArrayList<>();
            StringBuilder builder = new StringBuilder(" SELECT count(1) cut FROM ");
            builder.append(" (SELECT os.waysalesman AS \"业务员\",os.orderno AS \"订单号\",os.createtime AS \"下单时间\",os.membername AS \"买家账号\"," +
                    " bci.companyname AS \"公司名称\",os.totalprice AS \"订单总金额\",months_between ( " +
                    " (SELECT fot.firsttime FROM (SELECT MIN (os.createtime) AS firsttime,os.memberid FROM orders os GROUP BY os.memberid ) fot" +
                    " WHERE fot.memberid = os.memberid ) :: DATE, os.createtime :: DATE ) + 1 AS \"下单月份\" FROM orders os " +
                    " LEFT JOIN buyercompanyinfo bci ON bci.memberid = os.memberid WHERE 1=1 ");

            if (params.get("waysalesman") != null && StringUtils.isNotBlank(params.get("waysalesman"))) {
                builder.append(" and os.waysalesman = ?");
                list.add(params.get("waysalesman").trim());
            }
            if (params.get("membername") != null && StringUtils.isNotBlank(params.get("membername"))) {
                builder.append(" and os.membername = ?");
                list.add(params.get("membername").trim());
            }
            if (params.get("companyname") != null && StringUtils.isNotBlank(params.get("companyname"))) {
                builder.append(" and bci.companyname = ?");
                list.add(params.get("companyname").trim());
            }
            //订单状态0=待付款 1=待发货 3=待收货 4=待验货 5=已完成 7=已关闭 8=备货中 9=备货完成 10=部分发货
            builder.append(" AND os.orderstatus IN (1, 3, 4, 5, 8, 10)");
            builder.append(" ) AS T ;");
            return jdbcTemplate.query(builder.toString(), list.toArray(), new ResultSetExtractor<Long>() {
                @Override
                public Long extractData(ResultSet rs) throws SQLException, DataAccessException {
                    if (rs.next()) {
                        return rs.getLong("cut");
                    }
                    return 0L;
                }
            });

        } else {
            return 0L;
        }
    }

    /**
     * 查询指定业务员业绩
     *
     * @param params
     * @return
     */
    public Double getPerformanceOfSales(Map<String, String> params) {
        if (params != null) {
            List<Object> list = new ArrayList<>();
            StringBuilder builder = new StringBuilder("select sum(Performance) sm from ( SELECT CASE WHEN 下单月份 <= 6 THEN 0.01*totalprice " +
                    " WHEN 下单月份 <= 12 THEN 0.005*totalprice ELSE 0 END AS Performance FROM ");
            builder.append(" (SELECT os.totalprice, months_between ( " +
                    " (SELECT fot.firsttime FROM (SELECT MIN (os.createtime) AS firsttime,os.memberid FROM orders os GROUP BY os.memberid ) fot" +
                    " WHERE fot.memberid = os.memberid ) :: DATE, os.createtime :: DATE ) + 1 AS \"下单月份\" FROM orders os " +
                    " LEFT JOIN buyercompanyinfo bci ON bci.memberid = os.memberid WHERE 1=1 ");
            builder.append(" and os.waysalesman = ?");
            list.add(params.get("waysalesman").replace(" ", ""));
            if (params.containsKey("startDate")) {
                builder.append(" and os.createtime >='" + params.get("startDate") + "'");
            }
            if (params.containsKey("endDate")) {
                builder.append(" and os.createtime <='" + params.get("endDate") + "'");
            }
            if (params.get("membername") != null && StringUtils.isNotBlank(params.get("membername"))) {
                builder.append(" and os.membername = ?");
                list.add(params.get("membername").replace(" ", ""));
            }
            if (params.get("companyname") != null && StringUtils.isNotBlank(params.get("companyname"))) {
                builder.append(" and bci.companyname = ?");
                list.add(params.get("companyname").replace(" ", ""));
            }
            //订单状态0=待付款 1=待发货 3=待收货 4=待验货 5=已完成 7=已关闭 8=备货中 9=备货完成 10=部分发货
            builder.append(" AND os.orderstatus IN (1, 3, 4, 5, 8, 10)");
            builder.append(" ) AS T) tb ;");
            return jdbcTemplate.query(builder.toString(), list.toArray(), new ResultSetExtractor<Double>() {
                @Override
                public Double extractData(ResultSet rs) throws SQLException, DataAccessException {
                    if (rs.next()) {
                        return rs.getDouble("sm");
                    }
                    return 0D;
                }
            });
        } else {
            return 0D;
        }
    }

    /**
     * 业务员业绩汇总
     *
     * @param params
     * @return
     */
    public List<Map<String, Object>> getCollectPage(Map<String, String> params) {
        if (params != null) {
            if (params.get("limit") == null || params.get("offset") == null) {
                return null;
            }
            List<Object> list = new ArrayList<>();
            StringBuilder builder = new StringBuilder(" select ss.业务员,count(1) cut ,sum(订单总金额) 订单总金额,sum(业绩额) 业绩额 from (" +
                    "SELECT *, CASE WHEN 下单月份 <= 6 THEN '1%' WHEN 下单月份 <= 12 THEN '0.5%' ELSE '0%' END AS 比例, " +
                    " CASE WHEN 下单月份 <= 6 THEN 0.01*订单总金额 WHEN 下单月份 <= 12 THEN 0.005*订单总金额 ELSE  0 END AS 业绩额 FROM ");
            builder.append(" (SELECT os.waysalesman AS \"业务员\",os.totalprice AS \"订单总金额\",months_between ( " +
                    " (SELECT fot.firsttime FROM (SELECT MIN (os.createtime) AS firsttime,os.memberid FROM orders os GROUP BY os.memberid ) fot" +
                    " WHERE fot.memberid = os.memberid ) :: DATE, os.createtime :: DATE ) + 1 AS \"下单月份\" FROM orders os " +
                    " LEFT JOIN buyercompanyinfo bci ON bci.memberid = os.memberid WHERE 1=1 ");
            if (params.containsKey("startDate")) {
                builder.append(" and os.createtime >='" + params.get("startDate") + "'");
            }
            if (params.containsKey("endDate")) {
                builder.append(" and os.createtime <='" + params.get("endDate") + "'");
            }
            if (params.get("waysalesman") != null && StringUtils.isNotBlank(params.get("waysalesman"))) {
                builder.append(" and os.waysalesman = ?");
                list.add(params.get("waysalesman").replace(" ", ""));
            }
            //订单状态0=待付款 1=待发货 3=待收货 4=待验货 5=已完成 7=已关闭 8=备货中 9=备货完成 10=部分发货
            builder.append(" AND os.orderstatus IN (1, 3, 4, 5, 8, 10)");
            builder.append(" ORDER BY os.createtime desc) AS T ) ss WHERE ss.业务员 !='' GROUP BY ss.业务员 limit ? OFFSET ?;");
            list.add(Long.parseLong(params.get("limit")));
            list.add(Integer.parseInt(params.get("offset")));
            return jdbcTemplate.queryForList(builder.toString(), list.toArray());
        } else {
            return null;
        }

    }

    /**
     * 业务员汇总count
     *
     * @param params
     * @return
     */
    public Long getCollectCount(Map<String, String> params) {
        if (params != null) {

            List<Object> list = new ArrayList<>();
            StringBuilder builder = new StringBuilder("SELECT count(1) cut FROM ( select waysalesman from orders WHERE waysalesman !=''");
            builder.append(" AND orderstatus IN (1, 3, 4, 5, 8, 10) ");
            if (params.containsKey("startDate")) {
                builder.append(" and createtime >='" + params.get("startDate") + "'");
            }
            if (params.containsKey("endDate")) {
                builder.append(" and createtime <='" + params.get("endDate") + "'");
            }
            if (params.get("waysalesman") != null && StringUtils.isNotBlank(params.get("waysalesman"))) {
                builder.append(" and waysalesman = ?");
                list.add(params.get("waysalesman").trim());
            }
            //订单状态0=待付款 1=待发货 3=待收货 4=待验货 5=已完成 7=已关闭 8=备货中 9=备货完成 10=部分发货
            builder.append(" GROUP BY waysalesman ) t;");
            return jdbcTemplate.query(builder.toString(), list.toArray(), new ResultSetExtractor<Long>() {
                @Override
                public Long extractData(ResultSet rs) throws SQLException, DataAccessException {
                    if (rs.next()) {
                        return rs.getLong("cut");
                    }
                    return 0L;
                }
            });
        } else {
            return 0L;
        }
    }
}
