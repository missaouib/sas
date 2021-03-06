package com.js.sas.repository;

import com.js.sas.entity.dto.SaleAmountDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * @ClassName SaleAmountRepository
 * @Description 销货数据
 * @Author zc
 * @Date 2019/6/21 12:48
 **/
public interface SaleAmountRepository extends JpaRepository<SaleAmountDTO, Integer> {
    /**
     * 日销售额列表
     *
     * @param limit 天数
     * @return 日销售列表
     */
    @Query(nativeQuery = true, value = "SELECT @rownum \\:= @rownum +1 AS 'id', 0 AS 'months', 0 AS 'years'," +
            " DATE_FORMAT( sasd.create_time, '%Y/%m/%d' ) AS 'days'," +
            " COUNT( sasd.id ) AS 'counts'," +
            " SUM( sasd.amount ) AS 'amount'" +
            " FROM" +
            " (SELECT @rownum \\:= 0) r," +
            " YY_SA_SaleDelivery sasd" +
            " WHERE" +
            " sasd.create_time > DATE_ADD(NOW(),INTERVAL -30 DAY)" +
            " GROUP BY" +
            " days" +
            " ORDER BY" +
            " days DESC" +
            " LIMIT :limit")
    List<SaleAmountDTO> getSaleAmountByDay(@Param(value = "limit") int limit);

    /**
     * 月销售额列表
     *
     * @param limit 月数
     * @return 月销售列表
     */
    @Query(nativeQuery = true, value = "SELECT @rownum \\:= @rownum +1 AS 'id', 0 AS 'days', 0 AS 'years'," +
            " concat(cast((case when ((dayofmonth(`yss`.`create_time`) > 27) and (month(`yss`.`create_time`) = 12)) then (year(`yss`.`create_time`) + 1) else year(`yss`.`create_time`) end) as char charset utf8mb4),cast((case when ((dayofmonth(`yss`.`create_time`) > 27) and (month(`yss`.`create_time`) < 12)) then (month(`yss`.`create_time`) + 1) when ((dayofmonth(`yss`.`create_time`) > 27) and (month(`yss`.`create_time`) = 12)) then 1 else month(`yss`.`create_time`) end) as char charset utf8mb4)) AS `months`," +
            " COUNT( yss.id ) AS 'counts'," +
            " SUM( yss.amount ) AS 'amount'" +
            " FROM" +
            " (SELECT @rownum \\:= 0) r," +
            " YY_SA_SaleDelivery yss" +
            " GROUP BY" +
            " months" +
            " ORDER BY" +
            " months DESC" +
            " LIMIT :limit")
    List<SaleAmountDTO> getSaleAmountByMonth(@Param(value = "limit") int limit);

    /**
     * 年销售额列表
     *
     * @param limit 年数
     * @return 年销售列表
     */
    @Query(nativeQuery = true, value = "SELECT @rownum \\:= @rownum +1 AS 'id', 0 AS 'days', 0 AS 'months'," +
            " DATE_FORMAT( sasd.create_time, '%Y' ) AS 'years'," +
            " COUNT( sasd.id ) AS 'counts'," +
            " SUM( sasd.amount ) AS 'amount'" +
            " FROM" +
            " (SELECT @rownum \\:= 0) r," +
            " YY_SA_SaleDelivery sasd" +
            " GROUP BY" +
            " years" +
            " ORDER BY" +
            " years DESC" +
            " LIMIT :limit")
    List<SaleAmountDTO> getSaleAmountByYear(@Param(value = "limit") int limit);

}
